// Frame capture for the Windows backend.
//
// Every capture source runs on its own thread with its own D3D11 device, so
// capturing never stalls Minecraft's render thread. Monitors are captured
// with Desktop Duplication (falling back to Windows.Graphics.Capture when
// duplication is unavailable), single windows with Windows.Graphics.Capture.
// Frames are read back to the CPU as tightly packed BGRA8 and picked up by
// the render thread with `CaptureSource::poll_frame`.

use crate::win_desktop;
use std::ffi::c_void;
use std::panic::{self, AssertUnwindSafe};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, MutexGuard, mpsc};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};
use thiserror::Error;
use windows::Foundation::TypedEventHandler;
use windows::Graphics::Capture::{
    Direct3D11CaptureFramePool, GraphicsCaptureAccess,
    GraphicsCaptureAccessKind, GraphicsCaptureItem, GraphicsCaptureSession,
};
use windows::Graphics::DirectX::Direct3D11::IDirect3DDevice;
use windows::Graphics::DirectX::DirectXPixelFormat;
use windows::Graphics::SizeInt32;
use windows::Win32::Foundation::{HMODULE, HWND};
use windows::Win32::Graphics::Direct3D::{
    D3D_DRIVER_TYPE_HARDWARE, D3D_DRIVER_TYPE_UNKNOWN,
};
use windows::Win32::Graphics::Direct3D11::{
    D3D11_CPU_ACCESS_READ, D3D11_CREATE_DEVICE_BGRA_SUPPORT, D3D11_MAP_READ,
    D3D11_MAPPED_SUBRESOURCE, D3D11_SDK_VERSION, D3D11_TEXTURE2D_DESC,
    D3D11_USAGE_STAGING, D3D11CreateDevice, ID3D11Device, ID3D11DeviceContext,
    ID3D11Texture2D,
};
use windows::Win32::Graphics::Dxgi::Common::{
    DXGI_MODE_ROTATION, DXGI_MODE_ROTATION_ROTATE90,
    DXGI_MODE_ROTATION_ROTATE180, DXGI_MODE_ROTATION_ROTATE270,
    DXGI_SAMPLE_DESC,
};
use windows::Win32::Graphics::Dxgi::{
    CreateDXGIFactory1, DXGI_ERROR_ACCESS_LOST, DXGI_ERROR_WAIT_TIMEOUT,
    DXGI_OUTDUPL_FRAME_INFO, IDXGIAdapter, IDXGIAdapter1, IDXGIDevice,
    IDXGIFactory1, IDXGIOutput1, IDXGIOutputDuplication,
};
use windows::Win32::Graphics::Gdi::HMONITOR;
use windows::Win32::System::WinRT::Direct3D11::{
    CreateDirect3D11DeviceFromDXGIDevice, IDirect3DDxgiInterfaceAccess,
};
use windows::Win32::System::WinRT::Graphics::Capture::IGraphicsCaptureItemInterop;
use windows::Win32::System::WinRT::{RO_INIT_MULTITHREADED, RoInitialize};
use windows::core::{IInspectable, Interface};

#[derive(Debug, Error)]
pub enum CaptureError {
    #[error("{0}")]
    Windows(String),
    #[error("The monitor is no longer connected")]
    MonitorGone,
    #[error("The window was closed")]
    WindowClosed,
    #[error("Screen capture is not supported on this system")]
    Unsupported,
    #[error("Capture thread failed: {0}")]
    Thread(String),
}

impl From<windows::core::Error> for CaptureError {
    fn from(e: windows::core::Error) -> Self {
        CaptureError::Windows(format!("{} ({})", e.message(), e.code()))
    }
}

// Raw handles aren't Send, so targets carry their values instead
#[derive(Clone, Copy)]
pub enum CaptureTarget {
    Monitor(isize),
    Window(isize),
}

impl CaptureTarget {
    pub fn monitor(&self) -> Option<HMONITOR> {
        match self {
            CaptureTarget::Monitor(m) => Some(HMONITOR(*m as *mut c_void)),
            CaptureTarget::Window(_) => None,
        }
    }

    pub fn window(&self) -> Option<HWND> {
        match self {
            CaptureTarget::Monitor(_) => None,
            CaptureTarget::Window(w) => Some(HWND(*w as *mut c_void)),
        }
    }
}

pub struct Frame {
    // Tightly packed RGBA8 rows (alpha always 255), stride is always width * 4
    pub data: Vec<u8>,
    pub width: u32,
    pub height: u32,
    // DPI scale of the monitor the frame came from
    pub scale: f32,
}

#[derive(Default)]
struct Exchange {
    latest: Option<Frame>,
    spare: Vec<Vec<u8>>,
    // Set once the capture thread has stopped for good
    error: Option<String>,
}

struct Shared {
    exchange: Mutex<Exchange>,
    stop: AtomicBool,
    interval_micros: AtomicU64,
}

impl Shared {
    fn lock(&self) -> MutexGuard<'_, Exchange> {
        self.exchange.lock().unwrap_or_else(|e| e.into_inner())
    }

    fn interval(&self) -> Duration {
        Duration::from_micros(self.interval_micros.load(Ordering::Relaxed))
    }

    fn take_buffer(&self, len: usize) -> Vec<u8> {
        let mut buf = self.lock().spare.pop().unwrap_or_default();
        buf.resize(len, 0);
        buf
    }

    fn publish(&self, frame: Frame) {
        let mut exchange = self.lock();
        if let Some(old) = exchange.latest.replace(frame) {
            recycle(&mut exchange, old.data);
        }
    }
}

fn recycle(exchange: &mut Exchange, buf: Vec<u8>) {
    if exchange.spare.len() < 2 {
        exchange.spare.push(buf);
    }
}

fn rate_to_micros(rate: u32) -> u64 {
    1_000_000 / rate.clamp(1, 240) as u64
}

pub struct CaptureSource {
    pub target: CaptureTarget,
    shared: Arc<Shared>,
    thread: Option<JoinHandle<()>>,
    // Frame last handed out by poll_frame. Its buffer must stay untouched
    // until the next poll, since Java uploads straight from the pointer.
    held: Option<Frame>,
}

impl CaptureSource {
    // Start capturing on a new thread. Returns once the capture is set up
    // so initialization errors can be reported right away.
    pub fn start(
        target: CaptureTarget,
        rate: u32,
    ) -> Result<Self, CaptureError> {
        let shared = Arc::new(Shared {
            exchange: Mutex::new(Exchange::default()),
            stop: AtomicBool::new(false),
            interval_micros: AtomicU64::new(rate_to_micros(rate)),
        });

        let (tx, rx) = mpsc::sync_channel::<Result<(), CaptureError>>(1);
        let thread_shared = shared.clone();

        let thread = thread::Builder::new()
            .name("screens-capture".into())
            .spawn(move || {
                let capturer = match target {
                    CaptureTarget::Monitor(_) => {
                        let monitor = target.monitor().unwrap();
                        DuplicationCapture::new(monitor)
                            .map(Capturer::Duplication)
                            .or_else(|e| {
                                // Duplication fails with multiple GPUs or
                                // when too many apps already duplicate this
                                // output. WGC handles both.
                                GraphicsCapture::for_monitor(monitor)
                                    .map(Capturer::Graphics)
                                    .map_err(|_| e)
                            })
                    }
                    CaptureTarget::Window(_) => {
                        GraphicsCapture::for_window(target.window().unwrap())
                            .map(Capturer::Graphics)
                    }
                };

                match capturer {
                    Ok(capturer) => {
                        let _ = tx.send(Ok(()));
                        let result =
                            panic::catch_unwind(AssertUnwindSafe(|| {
                                run(capturer, &thread_shared)
                            }));
                        if result.is_err() {
                            thread_shared.lock().error =
                                Some("The capture thread crashed".into());
                        }
                    }
                    Err(e) => {
                        let _ = tx.send(Err(e));
                    }
                }
            })
            .map_err(|e| CaptureError::Thread(e.to_string()))?;

        let result = rx
            .recv()
            .unwrap_or_else(|e| Err(CaptureError::Thread(e.to_string())));

        match result {
            Ok(()) => Ok(CaptureSource {
                target,
                shared,
                thread: Some(thread),
                held: None,
            }),
            Err(e) => {
                let _ = thread.join();
                Err(e)
            }
        }
    }

    pub fn set_rate(&self, rate: u32) {
        self.shared
            .interval_micros
            .store(rate_to_micros(rate), Ordering::Relaxed);
    }

    // Take the newest frame, if there is one that hasn't been polled yet.
    // The returned frame stays valid until the next call.
    pub fn poll_frame(&mut self) -> Option<&Frame> {
        let mut exchange = self.shared.lock();
        let frame = exchange.latest.take()?;
        if let Some(old) = self.held.replace(frame) {
            recycle(&mut exchange, old.data);
        }
        drop(exchange);

        self.held.as_ref()
    }

    // Reason the capture stopped, None while it is still running
    pub fn error(&self) -> Option<String> {
        self.shared.lock().error.clone()
    }
}

impl Drop for CaptureSource {
    fn drop(&mut self) {
        self.shared.stop.store(true, Ordering::Relaxed);
        if let Some(thread) = self.thread.take() {
            let _ = thread.join();
        }
    }
}

enum Capturer {
    Duplication(DuplicationCapture),
    Graphics(GraphicsCapture),
}

impl Capturer {
    // Capture and publish a frame if a new one is available. Returns
    // whether a frame was published.
    fn capture(
        &mut self,
        shared: &Shared,
        timeout: Duration,
    ) -> Result<bool, CaptureError> {
        match self {
            Capturer::Duplication(c) => c.capture(shared, timeout),
            Capturer::Graphics(c) => c.capture(shared),
        }
    }
}

fn run(mut capturer: Capturer, shared: &Shared) {
    let mut next = Instant::now();

    let error = loop {
        if shared.stop.load(Ordering::Relaxed) {
            break CaptureError::Thread("Capture stopped".into());
        }

        let now = Instant::now();
        if next > now {
            thread::sleep(next - now);
        }

        let interval = shared.interval();
        match capturer.capture(shared, interval) {
            Ok(true) => next = Instant::now() + interval,
            // Duplication already waited for up to `interval`, WGC has no
            // blocking wait so poll a few times per interval instead
            Ok(false) => next = Instant::now() + interval / 4,
            Err(e) => break e,
        }
    };

    shared.lock().error = Some(error.to_string());
}

fn create_device(
    adapter: Option<&IDXGIAdapter1>,
) -> Result<(ID3D11Device, ID3D11DeviceContext), CaptureError> {
    let mut device = None;
    let mut context = None;

    let driver_type = if adapter.is_some() {
        D3D_DRIVER_TYPE_UNKNOWN
    } else {
        D3D_DRIVER_TYPE_HARDWARE
    };

    unsafe {
        D3D11CreateDevice(
            adapter
                .map(|a| a.cast::<IDXGIAdapter>())
                .transpose()?
                .as_ref(),
            driver_type,
            HMODULE::default(),
            D3D11_CREATE_DEVICE_BGRA_SUPPORT,
            None,
            D3D11_SDK_VERSION,
            Some(&mut device),
            None,
            Some(&mut context),
        )?;
    }

    match (device, context) {
        (Some(device), Some(context)) => Ok((device, context)),
        _ => Err(CaptureError::Unsupported),
    }
}

// CPU-readable copy of a GPU texture, recreated when the size changes
#[derive(Default)]
struct Readback {
    staging: Option<(ID3D11Texture2D, u32, u32)>,
}

impl Readback {
    fn copy(
        &mut self,
        device: &ID3D11Device,
        context: &ID3D11DeviceContext,
        texture: &ID3D11Texture2D,
    ) -> Result<(ID3D11Texture2D, u32, u32), CaptureError> {
        let mut desc = D3D11_TEXTURE2D_DESC::default();
        unsafe { texture.GetDesc(&mut desc) };

        let reuse = matches!(&self.staging, Some((_, w, h)) if *w == desc.Width && *h == desc.Height);
        if !reuse {
            let staging_desc = D3D11_TEXTURE2D_DESC {
                Width: desc.Width,
                Height: desc.Height,
                MipLevels: 1,
                ArraySize: 1,
                Format: desc.Format,
                SampleDesc: DXGI_SAMPLE_DESC {
                    Count: 1,
                    Quality: 0,
                },
                Usage: D3D11_USAGE_STAGING,
                BindFlags: 0,
                CPUAccessFlags: D3D11_CPU_ACCESS_READ.0 as u32,
                MiscFlags: 0,
            };

            let mut staging = None;
            unsafe {
                device.CreateTexture2D(
                    &staging_desc,
                    None,
                    Some(&mut staging),
                )?
            };
            let staging = staging.ok_or(CaptureError::Unsupported)?;
            self.staging = Some((staging, desc.Width, desc.Height));
        }

        let (staging, width, height) = self.staging.clone().unwrap();
        unsafe { context.CopyResource(&staging, texture) };
        Ok((staging, width, height))
    }
}

// Map a staging texture and copy (width x height) pixels of it into a new
// frame, undoing the given output rotation
fn read_frame(
    context: &ID3D11DeviceContext,
    staging: &ID3D11Texture2D,
    width: u32,
    height: u32,
    rotation: DXGI_MODE_ROTATION,
    scale: f32,
    shared: &Shared,
) -> Result<(), CaptureError> {
    let mut mapped = D3D11_MAPPED_SUBRESOURCE::default();
    unsafe { context.Map(staging, 0, D3D11_MAP_READ, 0, Some(&mut mapped))? };

    let w = width as usize;
    let h = height as usize;
    let pitch = mapped.RowPitch as usize;
    let src = unsafe {
        std::slice::from_raw_parts(
            mapped.pData as *const u8,
            pitch * (h - 1) + w * 4,
        )
    };

    let rotated = rotation == DXGI_MODE_ROTATION_ROTATE90
        || rotation == DXGI_MODE_ROTATION_ROTATE270;
    let (out_w, out_h) = if rotated { (h, w) } else { (w, h) };
    let mut data = shared.take_buffer(out_w * out_h * 4);

    // Duplicated frames are in the monitor's native orientation, while the
    // desktop is rotated on top of that
    let pixel = |x: usize, y: usize| &src[y * pitch + x * 4..][..4];
    if rotation == DXGI_MODE_ROTATION_ROTATE90 {
        for (i, dst) in data.as_chunks_mut::<4>().0.iter_mut().enumerate() {
            let (x, y) = (i % out_w, i / out_w);
            dst.copy_from_slice(pixel(w - 1 - y, x));
        }
    } else if rotation == DXGI_MODE_ROTATION_ROTATE270 {
        for (i, dst) in data.as_chunks_mut::<4>().0.iter_mut().enumerate() {
            let (x, y) = (i % out_w, i / out_w);
            dst.copy_from_slice(pixel(y, h - 1 - x));
        }
    } else if rotation == DXGI_MODE_ROTATION_ROTATE180 {
        for (y, row) in data.chunks_exact_mut(w * 4).enumerate() {
            let src_row = &src[(h - 1 - y) * pitch..][..w * 4];
            let dst_pixels = row.as_chunks_mut::<4>().0.iter_mut();
            let src_pixels = src_row.as_chunks::<4>().0.iter().rev();
            for (dst, px) in dst_pixels.zip(src_pixels) {
                *dst = *px;
            }
        }
    } else {
        for (y, row) in data.chunks_exact_mut(w * 4).enumerate() {
            row.copy_from_slice(&src[y * pitch..][..w * 4]);
        }
    }

    unsafe { context.Unmap(staging, 0) };

    to_opaque_rgba(&mut data);

    shared.publish(Frame {
        data,
        width: out_w as u32,
        height: out_h as u32,
        scale,
    });
    Ok(())
}

// Captured pixels are BGRA with an alpha that means nothing (often 0 over
// opaque content). The game's textures are RGBA8 on both of its backends
// (OpenGL and Vulkan), so swap red and blue and make every pixel opaque here,
// on the capture thread, and Java uploads the frame as it is.
fn to_opaque_rgba(data: &mut [u8]) {
    for px in data.as_chunks_mut::<4>().0.iter_mut() {
        px.swap(0, 2);
        px[3] = 255;
    }
}

// Monitor capture through the Desktop Duplication API
struct DuplicationCapture {
    monitor: HMONITOR,
    device: ID3D11Device,
    context: ID3D11DeviceContext,
    output: IDXGIOutput1,
    duplication: Option<IDXGIOutputDuplication>,
    rotation: DXGI_MODE_ROTATION,
    scale: f32,
    readback: Readback,
    has_frame: bool,
}

impl DuplicationCapture {
    fn new(monitor: HMONITOR) -> Result<Self, CaptureError> {
        let (adapter, output) = find_output(monitor)?;
        // Duplication requires a device on the adapter driving the output
        let (device, context) = create_device(Some(&adapter))?;

        let mut capture = DuplicationCapture {
            monitor,
            device,
            context,
            output,
            duplication: None,
            rotation: DXGI_MODE_ROTATION::default(),
            scale: win_desktop::monitor_scale(monitor),
            readback: Readback::default(),
            has_frame: false,
        };
        capture.duplicate()?;
        Ok(capture)
    }

    fn duplicate(&mut self) -> Result<(), CaptureError> {
        let duplication = unsafe { self.output.DuplicateOutput(&self.device)? };
        let desc = unsafe { duplication.GetDesc() };

        self.rotation = desc.Rotation;
        self.scale = win_desktop::monitor_scale(self.monitor);
        self.duplication = Some(duplication);
        self.has_frame = false;
        Ok(())
    }

    fn capture(
        &mut self,
        shared: &Shared,
        timeout: Duration,
    ) -> Result<bool, CaptureError> {
        if self.duplication.is_none() {
            // Access gets lost on mode changes, UAC prompts, lock screen, ...
            if win_desktop::monitor_info(self.monitor).is_none() {
                return Err(CaptureError::MonitorGone);
            }

            let recreated =
                find_output(self.monitor).and_then(|(adapter, output)| {
                    let (device, context) = create_device(Some(&adapter))?;
                    self.device = device;
                    self.context = context;
                    self.output = output;
                    self.readback = Readback::default();
                    self.duplicate()
                });

            if recreated.is_err() {
                thread::sleep(timeout.max(Duration::from_millis(250)));
                return Ok(false);
            }
        }

        let duplication = self.duplication.clone().unwrap();
        let mut info = DXGI_OUTDUPL_FRAME_INFO::default();
        let mut resource = None;

        let timeout_ms = timeout.as_millis().min(u32::MAX as u128) as u32;
        let acquired = unsafe {
            duplication.AcquireNextFrame(timeout_ms, &mut info, &mut resource)
        };

        match acquired {
            Ok(()) => {}
            Err(e) if e.code() == DXGI_ERROR_WAIT_TIMEOUT => return Ok(false),
            Err(e) if e.code() == DXGI_ERROR_ACCESS_LOST => {
                self.duplication = None;
                return Ok(false);
            }
            Err(e) => return Err(e.into()),
        }

        // A zero present time means only the mouse pointer changed
        let result = if info.LastPresentTime == 0 && self.has_frame {
            Ok(false)
        } else {
            self.read(shared, resource)
        };

        unsafe {
            let _ = duplication.ReleaseFrame();
        }

        if matches!(result, Ok(true)) {
            self.has_frame = true;
        }
        result
    }

    fn read(
        &mut self,
        shared: &Shared,
        resource: Option<windows::Win32::Graphics::Dxgi::IDXGIResource>,
    ) -> Result<bool, CaptureError> {
        let Some(resource) = resource else {
            return Ok(false);
        };
        let texture: ID3D11Texture2D = resource.cast()?;

        let (staging, width, height) =
            self.readback.copy(&self.device, &self.context, &texture)?;
        read_frame(
            &self.context,
            &staging,
            width,
            height,
            self.rotation,
            self.scale,
            shared,
        )?;
        Ok(true)
    }
}

fn find_output(
    monitor: HMONITOR,
) -> Result<(IDXGIAdapter1, IDXGIOutput1), CaptureError> {
    let factory: IDXGIFactory1 = unsafe { CreateDXGIFactory1()? };

    let mut adapter_index = 0;
    while let Ok(adapter) = unsafe { factory.EnumAdapters1(adapter_index) } {
        let mut output_index = 0;
        while let Ok(output) = unsafe { adapter.EnumOutputs(output_index) } {
            let desc = unsafe { output.GetDesc()? };
            if desc.Monitor == monitor {
                return Ok((adapter, output.cast()?));
            }
            output_index += 1;
        }
        adapter_index += 1;
    }

    Err(CaptureError::MonitorGone)
}

// Window (or fallback monitor) capture through Windows.Graphics.Capture
struct GraphicsCapture {
    window: Option<HWND>,
    monitor: Option<HMONITOR>,
    device: ID3D11Device,
    context: ID3D11DeviceContext,
    winrt_device: IDirect3DDevice,
    pool: Direct3D11CaptureFramePool,
    session: GraphicsCaptureSession,
    closed: Arc<AtomicBool>,
    size: SizeInt32,
    readback: Readback,
}

const FRAME_FORMAT: DirectXPixelFormat =
    DirectXPixelFormat::B8G8R8A8UIntNormalized;

impl GraphicsCapture {
    fn for_window(window: HWND) -> Result<Self, CaptureError> {
        if !win_desktop::window_exists(window) {
            return Err(CaptureError::WindowClosed);
        }

        Self::new(Some(window), None, |interop| unsafe {
            interop.CreateForWindow(window)
        })
    }

    fn for_monitor(monitor: HMONITOR) -> Result<Self, CaptureError> {
        Self::new(None, Some(monitor), |interop| unsafe {
            interop.CreateForMonitor(monitor)
        })
    }

    fn new(
        window: Option<HWND>,
        monitor: Option<HMONITOR>,
        create_item: impl FnOnce(
            &IGraphicsCaptureItemInterop,
        )
            -> windows::core::Result<GraphicsCaptureItem>,
    ) -> Result<Self, CaptureError> {
        unsafe {
            // Fails harmlessly if this thread was already initialized
            let _ = RoInitialize(RO_INIT_MULTITHREADED);
        }

        if !GraphicsCaptureSession::IsSupported()? {
            return Err(CaptureError::Unsupported);
        }

        let interop = windows::core::factory::<
            GraphicsCaptureItem,
            IGraphicsCaptureItemInterop,
        >()?;
        let item = create_item(&interop)?;

        let (device, context) = create_device(None)?;
        let dxgi_device: IDXGIDevice = device.cast()?;
        let inspectable: IInspectable =
            unsafe { CreateDirect3D11DeviceFromDXGIDevice(&dxgi_device)? };
        let winrt_device: IDirect3DDevice = inspectable.cast()?;

        let size = item.Size()?;
        let pool = Direct3D11CaptureFramePool::CreateFreeThreaded(
            &winrt_device,
            FRAME_FORMAT,
            2,
            size,
        )?;
        let session = pool.CreateCaptureSession(&item)?;

        // Without this Windows draws a yellow border around the captured
        // window on the real desktop. Not available on older builds.
        let _ = GraphicsCaptureAccess::RequestAccessAsync(
            GraphicsCaptureAccessKind::Borderless,
        )
        .and_then(|op| op.join());
        let _ = session.SetIsBorderRequired(false);

        let closed = Arc::new(AtomicBool::new(false));
        let closed_handler = closed.clone();
        item.Closed(&TypedEventHandler::new(move |_, _| {
            closed_handler.store(true, Ordering::Relaxed);
            Ok(())
        }))?;

        session.StartCapture()?;

        Ok(GraphicsCapture {
            window,
            monitor,
            device,
            context,
            winrt_device,
            pool,
            session,
            closed,
            size,
            readback: Readback::default(),
        })
    }

    fn capture(&mut self, shared: &Shared) -> Result<bool, CaptureError> {
        let window_gone =
            self.window.is_some_and(|w| !win_desktop::window_exists(w));
        if self.closed.load(Ordering::Relaxed) || window_gone {
            return Err(match self.window {
                Some(_) => CaptureError::WindowClosed,
                None => CaptureError::MonitorGone,
            });
        }

        // Only the newest frame is interesting
        let mut latest = None;
        while let Ok(frame) = self.pool.TryGetNextFrame() {
            if let Some(old) = latest.replace(frame) {
                let _ = old.Close();
            }
        }
        let Some(frame) = latest else {
            return Ok(false);
        };

        let content = frame.ContentSize()?;
        let result = self.read(shared, &frame, content);
        let _ = frame.Close();

        // Window was resized, buffers need to match the new size
        if content.Width > 0
            && content.Height > 0
            && (content.Width != self.size.Width
                || content.Height != self.size.Height)
        {
            self.size = content;
            self.pool
                .Recreate(&self.winrt_device, FRAME_FORMAT, 2, content)?;
        }

        result
    }

    fn read(
        &mut self,
        shared: &Shared,
        frame: &windows::Graphics::Capture::Direct3D11CaptureFrame,
        content: SizeInt32,
    ) -> Result<bool, CaptureError> {
        let access: IDirect3DDxgiInterfaceAccess = frame.Surface()?.cast()?;
        let texture: ID3D11Texture2D = unsafe { access.GetInterface()? };

        let (staging, tex_width, tex_height) =
            self.readback.copy(&self.device, &self.context, &texture)?;

        // The pool texture can be larger than the content while resizing
        let width = (content.Width.max(0) as u32).min(tex_width);
        let height = (content.Height.max(0) as u32).min(tex_height);
        if width == 0 || height == 0 {
            return Ok(false);
        }

        let scale = match (self.window, self.monitor) {
            (Some(window), _) => win_desktop::window_scale(window),
            (None, Some(monitor)) => win_desktop::monitor_scale(monitor),
            (None, None) => 1.0,
        };

        read_frame(
            &self.context,
            &staging,
            width,
            height,
            DXGI_MODE_ROTATION::default(),
            scale,
            shared,
        )?;
        Ok(true)
    }
}

impl Drop for GraphicsCapture {
    fn drop(&mut self) {
        let _ = self.session.Close();
        let _ = self.pool.Close();
    }
}
