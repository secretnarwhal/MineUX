// Desktop helpers for the Windows backend: monitor and window enumeration,
// bringing windows to the foreground and the global "return to Minecraft"
// hotkey. Nothing in here synthesizes input.

use std::ffi::c_void;
use std::sync::mpsc;
use std::thread::{self, JoinHandle};
use windows::Win32::Foundation::{
    CloseHandle, E_FAIL, HANDLE, HWND, LPARAM, POINT, RECT, WPARAM,
};
use windows::Win32::Graphics::Dwm::{
    DWMWA_CLOAKED, DWMWA_EXTENDED_FRAME_BOUNDS, DwmGetWindowAttribute,
};
use windows::Win32::Graphics::Gdi::{
    EnumDisplayMonitors, GetMonitorInfoW, HDC, HMONITOR, MONITORINFO,
    MONITORINFOEXW,
};
use windows::Win32::Security::{
    GetTokenInformation, TOKEN_ELEVATION, TOKEN_QUERY, TokenElevation,
};
use windows::Win32::System::Threading::{
    AttachThreadInput, GetCurrentProcess, GetCurrentThreadId, OpenProcess,
    OpenProcessToken, PROCESS_NAME_WIN32, PROCESS_QUERY_LIMITED_INFORMATION,
    QueryFullProcessImageNameW,
};
use windows::Win32::UI::HiDpi::{
    GetDpiForMonitor, GetDpiForWindow, MDT_EFFECTIVE_DPI,
};
use windows::Win32::UI::Input::KeyboardAndMouse::{
    HOT_KEY_MODIFIERS, MAPVK_VSC_TO_VK_EX, MOD_NOREPEAT, MapVirtualKeyW,
    RegisterHotKey, UnregisterHotKey,
};
use windows::Win32::UI::WindowsAndMessaging::{
    BringWindowToTop, EnumWindows, GW_OWNER, GWL_EXSTYLE, GetForegroundWindow,
    GetMessageW, GetWindow, GetWindowLongW, GetWindowRect,
    GetWindowTextLengthW, GetWindowTextW, GetWindowThreadProcessId, IsIconic,
    IsWindow, IsWindowVisible, MONITORINFOF_PRIMARY, MSG, PM_NOREMOVE,
    PeekMessageW, PostThreadMessageW, SW_RESTORE, SetCursorPos,
    SetForegroundWindow, ShowWindow, WM_HOTKEY, WM_QUIT, WS_EX_APPWINDOW,
    WS_EX_NOACTIVATE, WS_EX_TOOLWINDOW, WS_EX_TRANSPARENT,
};
use windows::core::BOOL;

pub struct MonitorInfo {
    pub handle: HMONITOR,
    pub name: String,
    pub device: String,
    pub rect: RECT,
    pub scale: f32,
    pub primary: bool,
}

pub struct WindowInfo {
    pub handle: HWND,
    pub title: String,
    pub exe_name: String,
    pub width: i32,
    pub height: i32,
    pub scale: f32,
    // Belongs to an elevated process while Minecraft is not elevated, so
    // Windows won't let us bring it to the foreground (UIPI)
    pub blocked: bool,
}

// Result codes of `focus_window`, mirrored in ScreensWinBridge.java
#[derive(Clone, Copy, PartialEq, Eq)]
pub enum FocusResult {
    Focused = 0,
    NoWindow = 1,
    Blocked = 2,
    Failed = 3,
}

pub fn monitors() -> Vec<MonitorInfo> {
    unsafe extern "system" fn callback(
        monitor: HMONITOR,
        _hdc: HDC,
        _rect: *mut RECT,
        data: LPARAM,
    ) -> BOOL {
        let list = unsafe { &mut *(data.0 as *mut Vec<MonitorInfo>) };
        if let Some(info) = monitor_info(monitor) {
            list.push(info);
        }
        true.into()
    }

    let mut list: Vec<MonitorInfo> = vec![];
    unsafe {
        let _ = EnumDisplayMonitors(
            None,
            None,
            Some(callback),
            LPARAM(&mut list as *mut _ as isize),
        );
    }
    list
}

pub fn monitor_info(monitor: HMONITOR) -> Option<MonitorInfo> {
    let mut info = MONITORINFOEXW::default();
    info.monitorInfo.cbSize = size_of::<MONITORINFOEXW>() as u32;

    let ok = unsafe {
        GetMonitorInfoW(monitor, &mut info as *mut _ as *mut MONITORINFO)
    };
    if !ok.as_bool() {
        return None;
    }

    let device = utf16_until_nul(&info.szDevice);

    // Device names look like \\.\DISPLAY2, which matches the numbering in
    // the Windows display settings closely enough
    let number: String = device
        .chars()
        .rev()
        .take_while(|c| c.is_ascii_digit())
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
        .collect();
    let name = if number.is_empty() {
        device.clone()
    } else {
        format!("Display {number}")
    };

    Some(MonitorInfo {
        handle: monitor,
        name,
        device,
        rect: info.monitorInfo.rcMonitor,
        scale: monitor_scale(monitor),
        primary: info.monitorInfo.dwFlags & MONITORINFOF_PRIMARY != 0,
    })
}

pub fn monitor_scale(monitor: HMONITOR) -> f32 {
    let mut dpi_x = 96;
    let mut dpi_y = 96;
    let result = unsafe {
        GetDpiForMonitor(monitor, MDT_EFFECTIVE_DPI, &mut dpi_x, &mut dpi_y)
    };
    match result {
        Ok(()) if dpi_x > 0 => dpi_x as f32 / 96.0,
        _ => 1.0,
    }
}

pub fn window_scale(window: HWND) -> f32 {
    let dpi = unsafe { GetDpiForWindow(window) };
    if dpi > 0 { dpi as f32 / 96.0 } else { 1.0 }
}

pub fn window_exists(window: HWND) -> bool {
    unsafe { IsWindow(Some(window)).as_bool() }
}

// All top-level windows from topmost to bottommost
fn top_level_windows() -> Vec<HWND> {
    unsafe extern "system" fn callback(window: HWND, data: LPARAM) -> BOOL {
        let list = unsafe { &mut *(data.0 as *mut Vec<HWND>) };
        list.push(window);
        true.into()
    }

    let mut list: Vec<HWND> = vec![];
    unsafe {
        let _ =
            EnumWindows(Some(callback), LPARAM(&mut list as *mut _ as isize));
    }
    list
}

// Windows that would show up in Alt+Tab, in Z order. `exclude` is
// Minecraft's own window.
pub fn windows(exclude: HWND) -> Vec<WindowInfo> {
    let self_elevated =
        process_elevated(unsafe { GetCurrentProcess() }).unwrap_or(false);

    top_level_windows()
        .into_iter()
        .filter(|w| *w != exclude && is_alt_tab_window(*w))
        .filter_map(|window| {
            let title = window_title(window)?;
            let (exe_name, elevated) = process_details(window);
            let rect = window_rect(window)?;

            Some(WindowInfo {
                handle: window,
                title,
                exe_name: exe_name.unwrap_or_default(),
                width: rect.right - rect.left,
                height: rect.bottom - rect.top,
                scale: window_scale(window),
                blocked: elevated && !self_elevated,
            })
        })
        .collect()
}

fn is_alt_tab_window(window: HWND) -> bool {
    unsafe {
        if !IsWindowVisible(window).as_bool() || is_cloaked(window) {
            return false;
        }

        let ex_style = GetWindowLongW(window, GWL_EXSTYLE) as u32;
        if ex_style & (WS_EX_TOOLWINDOW.0 | WS_EX_NOACTIVATE.0) != 0 {
            return false;
        }

        // Owned windows (dialogs, ...) only show up if they ask for it
        let owner = GetWindow(window, GW_OWNER).unwrap_or_default();
        !(!owner.is_invalid() && ex_style & WS_EX_APPWINDOW.0 == 0)
    }
}

// The topmost window that visibly covers `point` (virtual screen coordinates),
// skipping Minecraft itself and click-through overlays
pub fn window_at(point: POINT, exclude: HWND) -> Option<HWND> {
    top_level_windows().into_iter().find(|window| unsafe {
        if *window == exclude
            || !IsWindowVisible(*window).as_bool()
            || IsIconic(*window).as_bool()
            || is_cloaked(*window)
        {
            return false;
        }

        let ex_style = GetWindowLongW(*window, GWL_EXSTYLE) as u32;
        if ex_style & WS_EX_TRANSPARENT.0 != 0 {
            return false;
        }

        window_rect(*window).is_some_and(|r| {
            point.x >= r.left
                && point.x < r.right
                && point.y >= r.top
                && point.y < r.bottom
        })
    })
}

fn is_cloaked(window: HWND) -> bool {
    let mut cloaked: u32 = 0;
    let result = unsafe {
        DwmGetWindowAttribute(
            window,
            DWMWA_CLOAKED,
            &mut cloaked as *mut u32 as *mut c_void,
            size_of::<u32>() as u32,
        )
    };
    result.is_ok() && cloaked != 0
}

// Visible bounds of a window, without the invisible resize borders
pub fn window_rect(window: HWND) -> Option<RECT> {
    let mut rect = RECT::default();
    let result = unsafe {
        DwmGetWindowAttribute(
            window,
            DWMWA_EXTENDED_FRAME_BOUNDS,
            &mut rect as *mut RECT as *mut c_void,
            size_of::<RECT>() as u32,
        )
    };
    if result.is_err() {
        unsafe { GetWindowRect(window, &mut rect) }.ok()?;
    }
    Some(rect)
}

fn window_title(window: HWND) -> Option<String> {
    let len = unsafe { GetWindowTextLengthW(window) };
    if len <= 0 {
        return None;
    }

    let mut buf = vec![0u16; len as usize + 1];
    let copied = unsafe { GetWindowTextW(window, &mut buf) };
    if copied <= 0 {
        return None;
    }

    Some(String::from_utf16_lossy(&buf[..copied as usize]))
}

// Executable file name and whether the process is elevated. Processes we
// can't even query are treated as elevated.
fn process_details(window: HWND) -> (Option<String>, bool) {
    let mut pid = 0;
    unsafe { GetWindowThreadProcessId(window, Some(&mut pid)) };

    let Ok(process) =
        (unsafe { OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid) })
    else {
        return (None, true);
    };

    let mut buf = [0u16; 1024];
    let mut len = buf.len() as u32;
    let exe_name = unsafe {
        QueryFullProcessImageNameW(
            process,
            PROCESS_NAME_WIN32,
            windows::core::PWSTR(buf.as_mut_ptr()),
            &mut len,
        )
    }
    .ok()
    .map(|_| {
        let path = String::from_utf16_lossy(&buf[..len as usize]);
        path.rsplit(['\\', '/']).next().unwrap_or(&path).to_owned()
    });

    let elevated = process_elevated(process).unwrap_or(true);
    unsafe {
        let _ = CloseHandle(process);
    }

    (exe_name, elevated)
}

fn process_elevated(process: HANDLE) -> Option<bool> {
    let mut token = HANDLE::default();
    unsafe { OpenProcessToken(process, TOKEN_QUERY, &mut token) }.ok()?;

    let mut elevation = TOKEN_ELEVATION::default();
    let mut len = 0;
    let result = unsafe {
        GetTokenInformation(
            token,
            TokenElevation,
            Some(&mut elevation as *mut _ as *mut c_void),
            size_of::<TOKEN_ELEVATION>() as u32,
            &mut len,
        )
    };
    unsafe {
        let _ = CloseHandle(token);
    }

    result.ok().map(|_| elevation.TokenIsElevated != 0)
}

// Move the mouse cursor to a point in virtual screen coordinates
pub fn move_cursor(point: POINT) {
    let _ = unsafe { SetCursorPos(point.x, point.y) };
}

// Bring a top-level window to the foreground. This only works while the
// calling process is allowed to change the foreground window, i.e. it is
// in the foreground itself or just received input (like a hotkey).
pub fn focus_window(window: HWND) -> FocusResult {
    if !window_exists(window) {
        return FocusResult::NoWindow;
    }

    unsafe {
        if IsIconic(window).as_bool() {
            let _ = ShowWindow(window, SW_RESTORE);
        }

        if SetForegroundWindow(window).as_bool() {
            return FocusResult::Focused;
        }

        // Fallback: temporarily share the input state of the thread that
        // owns the current foreground window
        let current = GetCurrentThreadId();
        let foreground = GetWindowThreadProcessId(GetForegroundWindow(), None);
        let attached = foreground != 0
            && foreground != current
            && AttachThreadInput(current, foreground, true).as_bool();

        let _ = BringWindowToTop(window);
        let focused = SetForegroundWindow(window).as_bool();

        if attached {
            let _ = AttachThreadInput(current, foreground, false);
        }

        if focused || GetForegroundWindow() == window {
            return FocusResult::Focused;
        }
    }

    let self_elevated =
        process_elevated(unsafe { GetCurrentProcess() }).unwrap_or(false);
    if process_details(window).1 && !self_elevated {
        FocusResult::Blocked
    } else {
        FocusResult::Failed
    }
}

// GLFW reports Win32 scancodes with 0x100 set for extended (E0 prefixed) keys
pub fn scancode_to_virtual_key(scancode: u32) -> Option<u32> {
    let scancode = if scancode & 0x100 != 0 {
        0xe000 | (scancode & 0xff)
    } else {
        scancode
    };

    let virtual_key = unsafe { MapVirtualKeyW(scancode, MAPVK_VSC_TO_VK_EX) };
    (virtual_key != 0).then_some(virtual_key)
}

const HOTKEY_ID: i32 = 0x574c;

// A system-wide hotkey that brings a window back to the foreground. It has
// to be global because Minecraft doesn't receive key events once another
// window has been tabbed into.
pub struct Hotkey {
    thread_id: u32,
    thread: Option<JoinHandle<()>>,
}

impl Hotkey {
    pub fn register(
        modifiers: u32,
        virtual_key: u32,
        target: HWND,
    ) -> windows::core::Result<Self> {
        // HWND isn't Send, the raw value is fine to move to another thread
        let target = target.0 as isize;
        let (tx, rx) = mpsc::channel();

        let thread = thread::Builder::new()
            .name("screens-hotkey".into())
            .spawn(move || {
                let mut msg = MSG::default();
                let registered = unsafe {
                    // Create the thread message queue first so WM_QUIT can
                    // always be posted to it
                    let _ = PeekMessageW(&mut msg, None, 0, 0, PM_NOREMOVE);
                    RegisterHotKey(
                        None,
                        HOTKEY_ID,
                        HOT_KEY_MODIFIERS(modifiers) | MOD_NOREPEAT,
                        virtual_key,
                    )
                };

                let ok = registered.is_ok();
                let _ = tx
                    .send(registered.map(|_| unsafe { GetCurrentThreadId() }));
                if !ok {
                    return;
                }

                while unsafe { GetMessageW(&mut msg, None, 0, 0) }.0 > 0 {
                    if msg.message == WM_HOTKEY {
                        focus_window(HWND(target as *mut c_void));
                    }
                }

                unsafe {
                    let _ = UnregisterHotKey(None, HOTKEY_ID);
                }
            })
            .map_err(|e| windows::core::Error::new(E_FAIL, e.to_string()))?;

        match rx.recv() {
            Ok(Ok(thread_id)) => Ok(Hotkey {
                thread_id,
                thread: Some(thread),
            }),
            Ok(Err(e)) => {
                let _ = thread.join();
                Err(e)
            }
            Err(_) => {
                let _ = thread.join();
                Err(windows::core::Error::new(E_FAIL, "Hotkey thread died"))
            }
        }
    }
}

impl Drop for Hotkey {
    fn drop(&mut self) {
        unsafe {
            let _ = PostThreadMessageW(
                self.thread_id,
                WM_QUIT,
                WPARAM(0),
                LPARAM(0),
            );
        }
        if let Some(thread) = self.thread.take() {
            let _ = thread.join();
        }
    }
}

fn utf16_until_nul(buf: &[u16]) -> String {
    let len = buf.iter().position(|c| *c == 0).unwrap_or(buf.len());
    String::from_utf16_lossy(&buf[..len])
}
