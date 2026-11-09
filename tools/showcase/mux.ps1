<#
  Drives and captures a MineUX dev client (./gradlew runClient) for docs/showcase.

  The dev client is a GLFW window on a java process. This finds it, makes it a borderless window
  of an exact client size, captures it with PrintWindow (so nothing else on the screen, the
  taskbar included, ever lands in a picture) and sends it keys, text and clicks with SendInput
  after bringing it to the front.

    mux.ps1 setup [x y w h]       borderless window at x,y with a w x h client (default 1920,0 1920x1080)
    mux.ps1 shot <file.png>       capture the client area
    mux.ps1 focus
    mux.ps1 keys "alt+Return" "Escape" ...   key chords, pressed in order
    mux.ps1 hold <key> <ms>       hold a key down
    mux.ps1 type "text"           type text (unicode characters)
    mux.ps1 line "command"        type text, then Return
    mux.ps1 click <x> <y> [right] click at client coordinates
    mux.ps1 move <x> <y>          move the cursor to client coordinates
    mux.ps1 drag x1 y1 x2 y2 [mod|none] [right]   drag, holding a modifier (alt+drag moves a window)
    mux.ps1 wait <ms>
  Several commands can be chained in one call, separated by ";;".

  Key names: letters, digits, Return, Escape, Tab, space, BackSpace, F1..F12, left right up down,
  rshift lshift ctrl alt, t, slash, Home ...  Chords join with +.
#>
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args0)

$ErrorActionPreference = 'Stop'
if (-not ('MuxWin' -as [type])) {
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Text;
using System.Threading;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class MuxWin {
    [DllImport("user32.dll")] static extern bool SetProcessDPIAware();
    delegate bool EnumProc(IntPtr h, IntPtr l);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc f, IntPtr l);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetClassName(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] static extern int GetWindowLong(IntPtr h, int i);
    [DllImport("user32.dll")] static extern int SetWindowLong(IntPtr h, int i, int v);
    [DllImport("user32.dll")] static extern bool SetWindowPos(IntPtr h, IntPtr a, int x, int y, int w, int hh, uint f);
    [DllImport("user32.dll")] static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f);
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, IntPtr p);
    [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] static extern bool AttachThreadInput(uint a, uint b, bool f);
    [DllImport("user32.dll")] static extern bool BringWindowToTop(IntPtr h);
    [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr h, int c);
    [DllImport("user32.dll")] static extern uint SendInput(uint n, INPUT[] i, int size);
    [DllImport("user32.dll")] static extern uint MapVirtualKey(uint c, uint t);
    [DllImport("user32.dll")] static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] static extern bool AdjustWindowRectEx(ref RECT r, int style, bool menu, int ex);

    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx, dy; public uint data, flags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort vk, scan; public uint flags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Explicit)] struct UNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public UNION u; }

    static MuxWin() { SetProcessDPIAware(); }

    public static IntPtr Find() {
        IntPtr found = IntPtr.Zero;
        EnumWindows((h, l) => {
            var c = new StringBuilder(64); GetClassName(h, c, 64);
            var t = new StringBuilder(256); GetWindowText(h, t, 256);
            if (c.ToString().StartsWith("GLFW") && IsWindowVisible(h) && t.ToString().Contains("Minecraft")) { found = h; return false; }
            return true;
        }, IntPtr.Zero);
        if (found == IntPtr.Zero) throw new Exception("no Minecraft window");
        return found;
    }

    public static string Setup(int x, int y, int w, int h) {
        IntPtr win = Find();
        ShowWindow(win, 9);
        int style = GetWindowLong(win, -16);
        style &= ~(0x00C00000 | 0x00040000 | 0x00020000 | 0x00010000); // caption, thickframe, min/max box
        SetWindowLong(win, -16, style);
        SetWindowPos(win, IntPtr.Zero, x, y, w, h, 0x0020 | 0x0040); // framechanged, showwindow
        RECT r; GetClientRect(win, out r);
        return (r.R - r.L) + "x" + (r.B - r.T);
    }

    public static void Focus() {
        IntPtr win = Find();
        if (GetForegroundWindow() == win) return;
        uint me = GetCurrentThreadId();
        uint fg = GetWindowThreadProcessId(GetForegroundWindow(), IntPtr.Zero);
        AttachThreadInput(me, fg, true);
        BringWindowToTop(win);
        SetForegroundWindow(win);
        AttachThreadInput(me, fg, false);
        Thread.Sleep(150);
    }

    public static void Shot(string path) {
        IntPtr win = Find();
        RECT wr; GetWindowRect(win, out wr);
        RECT cr; GetClientRect(win, out cr);
        POINT o = new POINT(); ClientToScreen(win, ref o);
        int ww = wr.R - wr.L, wh = wr.B - wr.T;
        using (var bmp = new Bitmap(ww, wh, PixelFormat.Format32bppArgb)) {
            using (var g = Graphics.FromImage(bmp)) {
                IntPtr dc = g.GetHdc();
                PrintWindow(win, dc, 2); // PW_RENDERFULLCONTENT: what DWM composes, GL/Vulkan included
                g.ReleaseHdc(dc);
            }
            var crop = new Rectangle(o.X - wr.L, o.Y - wr.T, cr.R - cr.L, cr.B - cr.T);
            using (var c = bmp.Clone(crop, PixelFormat.Format24bppRgb)) c.Save(path, ImageFormat.Png);
        }
    }

    static INPUT Key(ushort vk, bool up) {
        var i = new INPUT(); i.type = 1;
        i.u.ki.vk = vk;
        i.u.ki.scan = (ushort)MapVirtualKey(vk, 0);
        uint f = 0;
        if (vk == 0x25 || vk == 0x26 || vk == 0x27 || vk == 0x28 || vk == 0x24 || vk == 0x23 || vk == 0x2D || vk == 0x2E || vk == 0x21 || vk == 0x22) f |= 1; // extended
        if (up) f |= 2;
        i.u.ki.flags = f;
        return i;
    }

    public static void Chord(ushort[] vks, int holdMs) {
        var down = new INPUT[vks.Length];
        for (int k = 0; k < vks.Length; k++) down[k] = Key(vks[k], false);
        foreach (var d in down) { SendInput(1, new[] { d }, Marshal.SizeOf(typeof(INPUT))); Thread.Sleep(25); }
        Thread.Sleep(holdMs);
        for (int k = vks.Length - 1; k >= 0; k--) { SendInput(1, new[] { Key(vks[k], true) }, Marshal.SizeOf(typeof(INPUT))); Thread.Sleep(25); }
    }

    public static void Type(string text) {
        foreach (char ch in text) {
            var a = new INPUT(); a.type = 1; a.u.ki.scan = ch; a.u.ki.flags = 4;
            var b = new INPUT(); b.type = 1; b.u.ki.scan = ch; b.u.ki.flags = 4 | 2;
            SendInput(2, new[] { a, b }, Marshal.SizeOf(typeof(INPUT)));
            Thread.Sleep(12);
        }
    }

    public static void Move(int x, int y) {
        IntPtr win = Find();
        POINT o = new POINT(); ClientToScreen(win, ref o);
        SetCursorPos(o.X + x, o.Y + y);
    }

    /** A drag from one client point to another, with a modifier held (0 for none). */
    public static void Drag(int x1, int y1, int x2, int y2, ushort mod, bool right) {
        int sz = Marshal.SizeOf(typeof(INPUT));
        if (mod != 0) { SendInput(1, new[] { Key(mod, false) }, sz); Thread.Sleep(60); }
        Move(x1, y1); Thread.Sleep(80);
        var d = new INPUT(); d.type = 0; d.u.mi.flags = right ? 0x0008u : 0x0002u;
        SendInput(1, new[] { d }, sz); Thread.Sleep(80);
        for (int s = 1; s <= 20; s++) { Move(x1 + (x2 - x1) * s / 20, y1 + (y2 - y1) * s / 20); Thread.Sleep(20); }
        Thread.Sleep(80);
        var u = new INPUT(); u.type = 0; u.u.mi.flags = right ? 0x0010u : 0x0004u;
        SendInput(1, new[] { u }, sz); Thread.Sleep(60);
        if (mod != 0) SendInput(1, new[] { Key(mod, true) }, sz);
    }

    public static void Click(int x, int y, bool right) {
        Move(x, y); Thread.Sleep(60);
        var d = new INPUT(); d.type = 0; d.u.mi.flags = right ? 0x0008u : 0x0002u;
        var u = new INPUT(); u.type = 0; u.u.mi.flags = right ? 0x0010u : 0x0004u;
        SendInput(1, new[] { d }, Marshal.SizeOf(typeof(INPUT))); Thread.Sleep(50);
        SendInput(1, new[] { u }, Marshal.SizeOf(typeof(INPUT)));
    }
}
'@
}

$vk = @{
    'return' = 0x0D; 'enter' = 0x0D; 'escape' = 0x1B; 'esc' = 0x1B; 'tab' = 0x09; 'space' = 0x20; 'backspace' = 0x08
    'left' = 0x25; 'up' = 0x26; 'right' = 0x27; 'down' = 0x28; 'home' = 0x24; 'end' = 0x23; 'delete' = 0x2E
    'rshift' = 0xA1; 'lshift' = 0xA0; 'shift' = 0xA0; 'ctrl' = 0xA2; 'lctrl' = 0xA2; 'alt' = 0xA4; 'lalt' = 0xA4
    'slash' = 0xBF; 'bracketleft' = 0xDB; 'bracketright' = 0xDD; 'minus' = 0xBD; 'equals' = 0xBB
}
function VkOf([string]$name) {
    $n = $name.ToLower()
    if ($vk.ContainsKey($n)) { return [uint16]$vk[$n] }
    if ($n -match '^f(\d{1,2})$') { return [uint16](0x6F + [int]$Matches[1]) }
    if ($n.Length -eq 1 -and $n -match '[a-z0-9]') { return [uint16][int][char]$n.ToUpper() }
    throw "unknown key $name"
}
function Chord([string]$spec, [int]$hold = 40) {
    $keys = $spec.Split('+') | ForEach-Object { VkOf $_ }
    [MuxWin]::Chord([uint16[]]$keys, $hold)
}

# split the arguments into commands on ";;"
$cmds = @(); $cur = @()
foreach ($a in $Args0) { if ($a -eq ';;') { if ($cur) { $cmds += , $cur }; $cur = @() } else { $cur += $a } }
if ($cur) { $cmds += , $cur }

foreach ($c in $cmds) {
    $rest = @($c | Select-Object -Skip 1)
    switch ($c[0]) {
        'setup' {
            if ($rest.Count -ge 4) { [MuxWin]::Setup([int]$rest[0], [int]$rest[1], [int]$rest[2], [int]$rest[3]) }
            else { [MuxWin]::Setup(1920, 0, 1920, 1080) }
        }
        'shot' { [MuxWin]::Shot([IO.Path]::GetFullPath($rest[0])); "saved $($rest[0])" }
        'focus' { [MuxWin]::Focus() }
        'keys' { [MuxWin]::Focus(); foreach ($k in $rest) { Chord $k; Start-Sleep -Milliseconds 120 } }
        'hold' { [MuxWin]::Focus(); Chord $rest[0] ([int]$rest[1]) }
        'type' { [MuxWin]::Focus(); [MuxWin]::Type(($rest -join ' ')) }
        'line' { [MuxWin]::Focus(); [MuxWin]::Type(($rest -join ' ')); Start-Sleep -Milliseconds 80; Chord 'return' }
        'click' { [MuxWin]::Focus(); [MuxWin]::Click([int]$rest[0], [int]$rest[1], ($rest.Count -gt 2 -and $rest[2] -eq 'right')) }
        'move' { [MuxWin]::Move([int]$rest[0], [int]$rest[1]) }
        'drag' {
            # drag x1 y1 x2 y2 [modifier|none] [right]
            [MuxWin]::Focus()
            $mod = if ($rest.Count -gt 4 -and $rest[4] -ne 'none') { VkOf $rest[4] } else { [uint16]0 }
            [MuxWin]::Drag([int]$rest[0], [int]$rest[1], [int]$rest[2], [int]$rest[3], $mod, ($rest.Count -gt 5 -and $rest[5] -eq 'right'))
        }
        'wait' { Start-Sleep -Milliseconds ([int]$rest[0]) }
        default { throw "unknown command $($c[0])" }
    }
}
