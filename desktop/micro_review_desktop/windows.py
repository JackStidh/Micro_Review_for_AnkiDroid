"""What Windows can tell: idle time, games, autostart. Every function is safe to call elsewhere: it then answers
"nothing" (no idle time, no game), so the add-on can be loaded and tested on any system.
"""
from __future__ import annotations

import os
import subprocess
import sys
from typing import List, Optional, Tuple

from .rules import GameSigns

IS_WINDOWS = sys.platform == "win32"
RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
RUN_NAME = "MicroReviewDesktop"

if IS_WINDOWS:
    import ctypes
    import winreg
    from ctypes import wintypes

    _user32 = ctypes.WinDLL("user32", use_last_error=True)
    _kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    _shell32 = ctypes.WinDLL("shell32", use_last_error=True)

    class _LASTINPUTINFO(ctypes.Structure):
        _fields_ = [("cbSize", wintypes.UINT), ("dwTime", wintypes.DWORD)]

    class _RECT(ctypes.Structure):
        _fields_ = [("left", wintypes.LONG), ("top", wintypes.LONG), ("right", wintypes.LONG), ("bottom", wintypes.LONG)]

    class _MONITORINFO(ctypes.Structure):
        _fields_ = [("cbSize", wintypes.DWORD), ("rcMonitor", _RECT), ("rcWork", _RECT), ("dwFlags", wintypes.DWORD)]

    _kernel32.GetTickCount.restype = wintypes.DWORD
    _kernel32.GetTickCount64.restype = ctypes.c_ulonglong
    _user32.GetLastInputInfo.argtypes = [ctypes.POINTER(_LASTINPUTINFO)]
    _user32.GetForegroundWindow.restype = wintypes.HWND
    _user32.GetWindowRect.argtypes = [wintypes.HWND, ctypes.POINTER(_RECT)]
    _user32.MonitorFromWindow.argtypes = [wintypes.HWND, wintypes.DWORD]
    _user32.MonitorFromWindow.restype = wintypes.HMONITOR
    _user32.GetMonitorInfoW.argtypes = [wintypes.HMONITOR, ctypes.POINTER(_MONITORINFO)]
    _user32.GetClassNameW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    _user32.GetWindowTextLengthW.argtypes = [wintypes.HWND]
    _user32.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    _user32.IsWindowVisible.argtypes = [wintypes.HWND]
    _user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    _kernel32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    _kernel32.OpenProcess.restype = wintypes.HANDLE
    _kernel32.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
    _kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    _WNDENUMPROC = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    _user32.EnumWindows.argtypes = [_WNDENUMPROC, wintypes.LPARAM]

_SHELL_CLASSES = {"Progman", "WorkerW", "Shell_TrayWnd", "Shell_SecondaryTrayWnd"}
_PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
_QUNS_RUNNING_D3D_FULL_SCREEN = 3
_CREATE_NO_WINDOW = 0x08000000


def idle_seconds() -> float:
    """Seconds since the last mouse or keyboard input, on any app."""
    if not IS_WINDOWS:
        return 0.0
    info = _LASTINPUTINFO()
    info.cbSize = ctypes.sizeof(info)
    if not _user32.GetLastInputInfo(ctypes.byref(info)):
        return 0.0
    return ((_kernel32.GetTickCount() - info.dwTime) & 0xFFFFFFFF) / 1000.0


def _process_name(hwnd) -> Optional[str]:
    pid = wintypes.DWORD()
    _user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    if not pid.value:
        return None
    handle = _kernel32.OpenProcess(_PROCESS_QUERY_LIMITED_INFORMATION, False, pid.value)
    if not handle:
        return None
    try:
        size = wintypes.DWORD(1024)
        buffer = ctypes.create_unicode_buffer(size.value)
        if not _kernel32.QueryFullProcessImageNameW(handle, 0, buffer, ctypes.byref(size)):
            return None
        return os.path.basename(buffer.value)
    finally:
        _kernel32.CloseHandle(handle)


def _visible_windows() -> List[Tuple[str, str]]:
    found = []  # type: List[Tuple[str, str]]

    def callback(hwnd, _):
        try:
            if _user32.IsWindowVisible(hwnd):
                length = _user32.GetWindowTextLengthW(hwnd)
                if length:
                    buffer = ctypes.create_unicode_buffer(length + 1)
                    _user32.GetWindowTextW(hwnd, buffer, length + 1)
                    name = _process_name(hwnd)
                    if name:
                        found.append((name, buffer.value))
        except Exception:
            pass
        return True

    _user32.EnumWindows(_WNDENUMPROC(callback), 0)
    return found


def _processes() -> List[str]:
    """Running process names, windows or not (a UWP game's process has none of its own)."""
    try:
        out = subprocess.run(
            ["tasklist", "/fo", "csv", "/nh"],
            capture_output=True,
            text=True,
            errors="replace",
            timeout=10,
            creationflags=_CREATE_NO_WINDOW,
        ).stdout
    except Exception:
        return []
    names = []
    for line in out.splitlines():
        if line.startswith('"'):
            names.append(line[1:].split('"', 1)[0])
    return names


def _foreground() -> Tuple[bool, Optional[str]]:
    """Whether the window in front covers its whole monitor, and its process."""
    hwnd = _user32.GetForegroundWindow()
    if not hwnd:
        return False, None
    name = _process_name(hwnd)
    cls = ctypes.create_unicode_buffer(256)
    _user32.GetClassNameW(hwnd, cls, 256)
    if cls.value in _SHELL_CLASSES:
        return False, name
    rect = _RECT()
    if not _user32.GetWindowRect(hwnd, ctypes.byref(rect)):
        return False, name
    monitor = _user32.MonitorFromWindow(hwnd, 2)  # MONITOR_DEFAULTTONEAREST
    info = _MONITORINFO()
    info.cbSize = ctypes.sizeof(info)
    if not _user32.GetMonitorInfoW(monitor, ctypes.byref(info)):
        return False, name
    screen = info.rcMonitor
    full = rect.left <= screen.left and rect.top <= screen.top and rect.right >= screen.right and rect.bottom >= screen.bottom
    return full, name


def _exclusive_fullscreen() -> bool:
    state = ctypes.c_int(0)
    try:
        return _shell32.SHQueryUserNotificationState(ctypes.byref(state)) == 0 and state.value == _QUNS_RUNNING_D3D_FULL_SCREEN
    except Exception:
        return False


def _steam_app_id() -> int:
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Valve\Steam") as key:
            value, _ = winreg.QueryValueEx(key, "RunningAppID")
            return int(value or 0)
    except Exception:
        return 0


def game_signs() -> GameSigns:
    """Everything that may mean a game is being played. Takes up to a few tenths of a second: off the main thread."""
    if not IS_WINDOWS:
        return GameSigns()
    windows = _visible_windows()
    titled = {name.lower() for name, _ in windows}
    windows += [(name, "") for name in _processes() if name.lower() not in titled]
    fullscreen, front = _foreground()
    return GameSigns(
        steam_app_id=_steam_app_id(),
        windows=windows,
        exclusive_fullscreen=_exclusive_fullscreen(),
        foreground_fullscreen=fullscreen,
        foreground_process=front,
    )


def anki_executable() -> Optional[str]:
    """Anki's own program, if Anki runs from its installer (not from a Python install)."""
    exe = sys.executable or ""
    if not IS_WINDOWS or not exe.lower().endswith(".exe") or os.path.basename(exe).lower().startswith("python"):
        return None
    return exe


def autostart_command(profile: str) -> Optional[str]:
    """Anki with the profile: the -p tells the add-on it started with Windows, to start hidden."""
    exe = anki_executable()
    return None if exe is None else '"%s" -p "%s"' % (exe, profile)


def autostart() -> Optional[str]:
    """The command Windows starts at sign-in, or None."""
    if not IS_WINDOWS:
        return None
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as key:
            return winreg.QueryValueEx(key, RUN_NAME)[0]
    except OSError:
        return None


def set_autostart(command: Optional[str]) -> bool:
    """Starts Anki with Windows with `command`, or not when None. Returns whether it worked."""
    if not IS_WINDOWS:
        return False
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as key:
            if command is None:
                try:
                    winreg.DeleteValue(key, RUN_NAME)
                except FileNotFoundError:
                    pass
            else:
                winreg.SetValueEx(key, RUN_NAME, 0, winreg.REG_SZ, command)
        return True
    except OSError:
        return False
