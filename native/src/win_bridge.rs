#![allow(non_snake_case)]
#![allow(clippy::too_many_arguments)]

use crate::win_capture::{CaptureError, CaptureSource, CaptureTarget};
use crate::win_desktop::{self, FocusResult, Hotkey};
use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jboolean, jdouble, jint, jlong};
use jni::{Env, bind_java_type};
use std::collections::HashMap;
use std::ffi::c_void;
use thiserror::Error;
use windows::Win32::Foundation::{HWND, POINT, RECT};
use windows::Win32::Graphics::Gdi::HMONITOR;

pub(crate) struct CaptureHost {
    minecraft_window: HWND,
    sources: HashMap<jlong, CaptureSource>,
    next_source: jlong,
    hotkey: Option<Hotkey>,
}

bind_java_type! {
    rust_type = JCaptureTarget,
    java_type = com.mineux.screens.bridge.CaptureTarget,

    constructors {
        fn new(
            kind: jint,
            handle: jlong,
            title: JString,
            detail: JString,
            width: jint,
            height: jint,
            scale: jfloat,
            primary: jboolean,
            blocked: jboolean
        )
    },
}

bind_java_type! {
    rust_type = JCapturedFrame,
    java_type = com.mineux.screens.bridge.CapturedFrame,

    constructors {
        fn new(ptr: jlong, width: jint, height: jint, stride: jint, scale: jfloat)
    },
}

bind_java_type! {
    rust_type = ScreensWinBridge,
    java_type = com.mineux.screens.bridge.ScreensWinBridge,

    type_map {
        JCaptureTarget => com.mineux.screens.bridge.CaptureTarget,
        JCapturedFrame => com.mineux.screens.bridge.CapturedFrame,
    },

    native_methods {
        static extern fn init {
            sig = (minecraft_window: jlong) -> jlong,
            fn = init,
        },
        static extern fn shutdown {
            sig = (instance: jlong),
            fn = shutdown,
        },
        static extern fn monitors {
            sig = (instance: jlong) -> JCaptureTarget[],
            fn = monitors,
        },
        static extern fn windows {
            sig = (instance: jlong) -> JCaptureTarget[],
            fn = windows,
        },
        static extern fn start_capture {
            sig = (
                instance: jlong,
                kind: jint,
                handle: jlong,
                rate: jint
            ) -> jlong,
            fn = start_capture,
        },
        static extern fn stop_capture {
            sig = (instance: jlong, source: jlong),
            fn = stop_capture,
        },
        static extern fn set_capture_rate {
            sig = (instance: jlong, source: jlong, rate: jint),
            fn = set_capture_rate,
        },
        static extern fn poll_frame {
            sig = (instance: jlong, source: jlong) -> JCapturedFrame,
            fn = poll_frame,
        },
        static extern fn capture_error {
            sig = (instance: jlong, source: jlong) -> JString,
            fn = capture_error,
        },
        static extern fn tab_into {
            sig = (instance: jlong, source: jlong, x: jdouble, y: jdouble) -> jint,
            fn = tab_into,
        },
        static extern fn focus_minecraft {
            sig = (instance: jlong) -> jint,
            fn = focus_minecraft,
        },
        static extern fn set_return_hotkey {
            sig = (instance: jlong, modifiers: jint, scancode: jint) -> jboolean,
            fn = set_return_hotkey,
        },
    },
}

// Capture target kinds, mirrored in CaptureTarget.java
const KIND_MONITOR: jint = 0;
const KIND_WINDOW: jint = 1;

#[derive(Debug, Error)]
enum WinBridgeError {
    #[error(transparent)]
    JniError(#[from] jni::errors::Error),
    #[error(transparent)]
    Capture(#[from] CaptureError),
    #[error("Null Screens instance handle given. Function: {0}")]
    NullInstancePtr(&'static str),
    #[error("Unknown capture source {0}")]
    UnknownSource(jlong),
    #[error("Unknown capture target kind {0}")]
    UnknownKind(jint),
}

fn instance<'a>(
    ptr: jlong,
    location: &'static str,
) -> Result<&'a mut CaptureHost, WinBridgeError> {
    let ptr = (ptr as usize) as *mut CaptureHost;
    if ptr.is_null() {
        Err(WinBridgeError::NullInstancePtr(location))
    } else {
        Ok(unsafe { &mut *ptr })
    }
}

impl CaptureHost {
    fn source(
        &mut self,
        source: jlong,
    ) -> Result<&mut CaptureSource, WinBridgeError> {
        self.sources
            .get_mut(&source)
            .ok_or(WinBridgeError::UnknownSource(source))
    }
}

fn init<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    minecraft_window: jlong,
) -> Result<jlong, WinBridgeError> {
    let instance = Box::new(CaptureHost {
        minecraft_window: HWND(minecraft_window as isize as *mut c_void),
        sources: HashMap::new(),
        next_source: 1,
        hotkey: None,
    });

    Ok(Box::into_raw(instance).addr() as jlong)
}

fn shutdown<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), WinBridgeError> {
    // Stops all capture threads and the hotkey thread. No calls into the
    // bridge are allowed afterwards.
    let ptr = instance as *mut CaptureHost;
    if !ptr.is_null() {
        let _ = unsafe { Box::from_raw(ptr) };
    }
    Ok(())
}

struct TargetInfo<'a> {
    kind: jint,
    handle: jlong,
    title: &'a str,
    detail: &'a str,
    width: i32,
    height: i32,
    scale: f32,
    primary: bool,
    blocked: bool,
}

fn targets_to_java<'local>(
    env: &mut Env<'local>,
    targets: &[TargetInfo],
) -> Result<JObjectArray<'local, JCaptureTarget<'local>>, WinBridgeError> {
    let array = JObjectArray::<JCaptureTarget>::new(
        env,
        targets.len(),
        &JCaptureTarget::null(),
    )?;

    for (index, t) in targets.iter().enumerate() {
        let title = JString::new(env, t.title)?;
        let detail = JString::new(env, t.detail)?;
        let target = JCaptureTarget::new(
            env, t.kind, t.handle, &title, &detail, t.width, t.height, t.scale,
            t.primary, t.blocked,
        )?;
        array.set_element(env, index, &target)?;
    }

    Ok(array)
}

fn monitors<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JObjectArray<'local, JCaptureTarget<'local>>, WinBridgeError> {
    let _ = self::instance(instance, "monitors")?;
    let monitors = win_desktop::monitors();

    let targets: Vec<_> = monitors
        .iter()
        .map(|m| TargetInfo {
            kind: KIND_MONITOR,
            handle: m.handle.0 as isize as jlong,
            title: &m.name,
            detail: &m.device,
            width: m.rect.right - m.rect.left,
            height: m.rect.bottom - m.rect.top,
            scale: m.scale,
            primary: m.primary,
            blocked: false,
        })
        .collect();

    targets_to_java(env, &targets)
}

fn windows<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JObjectArray<'local, JCaptureTarget<'local>>, WinBridgeError> {
    let instance = self::instance(instance, "windows")?;
    let windows = win_desktop::windows(instance.minecraft_window);

    let targets: Vec<_> = windows
        .iter()
        .map(|w| TargetInfo {
            kind: KIND_WINDOW,
            handle: w.handle.0 as isize as jlong,
            title: &w.title,
            detail: &w.exe_name,
            width: w.width,
            height: w.height,
            scale: w.scale,
            primary: false,
            blocked: w.blocked,
        })
        .collect();

    targets_to_java(env, &targets)
}

fn start_capture<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    kind: jint,
    handle: jlong,
    rate: jint,
) -> Result<jlong, WinBridgeError> {
    let instance = self::instance(instance, "startCapture")?;

    let target = match kind {
        KIND_MONITOR => CaptureTarget::Monitor(handle as isize),
        KIND_WINDOW => CaptureTarget::Window(handle as isize),
        _ => return Err(WinBridgeError::UnknownKind(kind)),
    };

    let source = CaptureSource::start(target, rate.max(1) as u32)?;
    let id = instance.next_source;
    instance.next_source += 1;
    instance.sources.insert(id, source);

    Ok(id)
}

fn stop_capture<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    source: jlong,
) -> Result<(), WinBridgeError> {
    let instance = self::instance(instance, "stopCapture")?;
    instance.sources.remove(&source);
    Ok(())
}

fn set_capture_rate<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    source: jlong,
    rate: jint,
) -> Result<(), WinBridgeError> {
    let instance = self::instance(instance, "setCaptureRate")?;
    instance.source(source)?.set_rate(rate.max(1) as u32);
    Ok(())
}

fn poll_frame<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    source: jlong,
) -> Result<JCapturedFrame<'local>, WinBridgeError> {
    let instance = self::instance(instance, "pollFrame")?;

    match instance.source(source)?.poll_frame() {
        Some(frame) => Ok(JCapturedFrame::new(
            env,
            frame.data.as_ptr().addr() as jlong,
            frame.width as jint,
            frame.height as jint,
            (frame.width * 4) as jint,
            frame.scale,
        )?),
        None => Ok(JCapturedFrame::null()),
    }
}

fn capture_error<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    source: jlong,
) -> Result<JString<'local>, WinBridgeError> {
    let instance = self::instance(instance, "captureError")?;

    match instance.source(source)?.error() {
        Some(error) => Ok(JString::new(env, error)?),
        None => Ok(JString::null()),
    }
}

// Bring the captured window to the foreground and move the mouse cursor to
// the clicked spot. (x, y) is the clicked pixel of the captured frame, for
// monitors the window visible at that spot gets focused.
// The cursor is only moved, no click is ever sent to the window.
fn tab_into<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    source: jlong,
    x: jdouble,
    y: jdouble,
) -> Result<jint, WinBridgeError> {
    let instance = self::instance(instance, "tabInto")?;
    let minecraft_window = instance.minecraft_window;
    let target = instance.source(source)?.target;

    // Clicked spot in virtual screen coordinates
    let point_in = |rect: RECT| POINT {
        x: rect.left + x.floor() as i32,
        y: rect.top + y.floor() as i32,
    };

    let (window, point) = match target {
        CaptureTarget::Window(w) => {
            let window = HWND(w as *mut c_void);
            (Some(window), win_desktop::window_rect(window).map(point_in))
        }
        CaptureTarget::Monitor(m) => {
            let point = win_desktop::monitor_info(HMONITOR(m as *mut c_void))
                .map(|info| point_in(info.rect));
            let window =
                point.and_then(|p| win_desktop::window_at(p, minecraft_window));
            (window, point)
        }
    };

    let result = match window {
        Some(window) => win_desktop::focus_window(window),
        None => FocusResult::NoWindow,
    };

    if let (FocusResult::Focused, Some(point)) = (&result, point) {
        win_desktop::move_cursor(point);
    }
    Ok(result as jint)
}

fn focus_minecraft<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<jint, WinBridgeError> {
    let instance = self::instance(instance, "focusMinecraft")?;
    Ok(win_desktop::focus_window(instance.minecraft_window) as jint)
}

// Register the global return hotkey, replacing the previous one. The key is
// given as a GLFW scancode, 0 only removes the hotkey.
fn set_return_hotkey<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    modifiers: jint,
    scancode: jint,
) -> Result<jboolean, WinBridgeError> {
    let instance = self::instance(instance, "setReturnHotkey")?;
    instance.hotkey = None;

    if scancode == 0 {
        return Ok(true);
    }

    let Some(virtual_key) =
        win_desktop::scancode_to_virtual_key(scancode as u32)
    else {
        return Ok(false);
    };

    // Fails when another application already owns the combination
    let hotkey = Hotkey::register(
        modifiers as u32,
        virtual_key,
        instance.minecraft_window,
    );
    instance.hotkey = hotkey.ok();

    Ok(instance.hotkey.is_some())
}
