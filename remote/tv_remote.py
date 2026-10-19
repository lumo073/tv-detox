#!/usr/bin/env python3
"""Phone remote for an Android TV, served from this laptop over Wi-Fi. Only needs Python and adb.

    python remote/tv_remote.py 192.168.1.69          then open the printed link on your phone

Everything goes through adb, like the scripts in this repo: buttons, opening apps, typing into
search boxes from the phone's keyboard, a live screenshot, wallpapers and a few clean-up tools.
The link has a secret in it (kept in remote/.token), so only people you give it to can use it.
When the TV is asleep, adb can't reach it: wake it with the real remote first.
"""
import html
import json
import os
import secrets
import shlex
import shutil
import socket
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
PORT = int(os.environ.get("PORT", "8765"))
WALLS = "/sdcard/Android/data/com.ansub.tvhome/files/wallpapers"

# Friendly names for common TV apps; anything else shows its own package name.
NAMES = {
    "com.google.android.youtube.tv": "YouTube", "com.netflix.ninja": "Netflix",
    "com.amazon.amazonvideo.livingroom": "Prime Video", "com.disney.disneyplus": "Disney+",
    "com.google.android.youtube.tvmusic": "YouTube Music", "com.android.vending": "Play Store",
    "com.android.tv.settings": "Settings", "com.spotify.tv.android": "Spotify",
    "com.hulu.livingroomplus": "Hulu", "com.jio.media.stb.ondemand": "JioCinema",
    "in.startv.hotstar": "Hotstar", "com.mxtech.videoplayer.television": "MX Player",
    "org.videolan.vlc": "VLC", "com.plexapp.android": "Plex", "com.xiaomi.mitv.mediaexplorer": "USB Files",
    "com.mitv.videoplayer": "Video Player", "com.mitv.gallery": "Gallery", "com.xiaomi.mimusic2": "Music",
    "com.xiaomi.mitv.tvmanager": "TV Manager", "com.ansub.tvhome": "Home",
}
SKIP = {"com.ansub.tvhome", "com.google.android.tvlauncher", "com.android.tv"}


def find_adb():
    for c in (os.environ.get("ADB"), shutil.which("adb"),
              os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe"),
              os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")):
        if c and os.path.isfile(c):
            return c
    sys.exit("adb not found. Install Android platform-tools, or set ADB=/path/to/adb")


ADB = find_adb()
TV = ""
lock = threading.Lock()  # one adb command at a time keeps button presses in order
sleep_timer = {"at": 0, "timer": None}  # sleep timer: when it fires, and the timer itself


def set_sleep_timer(minutes):
    """Puts the TV to sleep in so many minutes, like a TV's own sleep timer. 0 cancels.
    It runs on this laptop, so the laptop has to stay on until then."""
    if sleep_timer["timer"]:
        sleep_timer["timer"].cancel()
    sleep_timer.update(at=0, timer=None)
    if minutes > 0:
        def fire():
            with lock:
                if connected():
                    sh("input keyevent KEYCODE_SLEEP")
            sleep_timer.update(at=0, timer=None)
        t = threading.Timer(minutes * 60, fire)
        t.daemon = True
        t.start()
        sleep_timer.update(at=time.time() + minutes * 60, timer=t)


def adb(*args, timeout=15, binary=False):
    r = subprocess.run([ADB, "-s", TV, *args], capture_output=True, timeout=timeout)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace").replace("\r", "")


def sh(cmd, **kw):
    return adb("shell", cmd, **kw)


def connected():
    state = subprocess.run([ADB, "-s", TV, "get-state"], capture_output=True, timeout=5).stdout.decode().strip()
    if state != "device":
        subprocess.run([ADB, "connect", TV], capture_output=True, timeout=8)
        state = subprocess.run([ADB, "-s", TV, "get-state"], capture_output=True, timeout=5).stdout.decode().strip()
    return state == "device"


KEYS = {  # button name -> Android key code
    "up": "DPAD_UP", "down": "DPAD_DOWN", "left": "DPAD_LEFT", "right": "DPAD_RIGHT", "ok": "DPAD_CENTER",
    "back": "BACK", "home": "HOME", "menu": "MENU", "volup": "VOLUME_UP", "voldown": "VOLUME_DOWN",
    "mute": "VOLUME_MUTE", "play": "MEDIA_PLAY_PAUSE", "rew": "MEDIA_REWIND", "ff": "MEDIA_FAST_FORWARD",
    "prev": "MEDIA_PREVIOUS", "next": "MEDIA_NEXT", "sleep": "SLEEP", "del": "DEL", "enter": "ENTER",
    "input": "TV_INPUT", "search": "SEARCH",
}


def type_text(text):
    # `input text` takes ASCII only; %s is its way of writing a space.
    safe = "".join(c for c in text if 32 <= ord(c) < 127).replace("%", "").replace(" ", "%s")
    if safe:
        sh("input text " + shlex.quote(safe))


def apps():
    out = sh("cmd package query-activities --brief -a android.intent.action.MAIN "
             "-c android.intent.category.LEANBACK_LAUNCHER")
    pkgs = sorted({l.strip().split("/")[0] for l in out.splitlines() if "/" in l} - SKIP,
                  key=lambda p: (p not in NAMES, NAMES.get(p, p).lower()))
    return [{"pkg": p, "name": NAMES.get(p, p.split(".")[-1])} for p in pkgs]


def status():
    mem = {}
    for line in sh("cat /proc/meminfo").splitlines()[:5]:
        k, _, v = line.partition(":")
        mem[k] = int(v.split()[0]) // 1024 if v.strip() else 0
    focus = sh("dumpsys window | grep -m1 mCurrentFocus").strip()
    app = focus.split(" u0 ")[-1].split("/")[0] if " u0 " in focus else ""
    return {"free_mb": mem.get("MemAvailable", 0), "total_mb": mem.get("MemTotal", 0),
            "app": NAMES.get(app, app), "awake": "mWakefulness=Awake" in sh("dumpsys power | grep -m1 mWakefulness"),
            "sleep_in": max(0, round((sleep_timer["at"] - time.time()) / 60)) if sleep_timer["at"] else 0}


def act(a, q):
    if a == "key":
        k = KEYS.get(q.get("k", ""))
        if k:
            sh(("input keyevent --longpress KEYCODE_" if q.get("long") else "input keyevent KEYCODE_") + k)
    elif a == "text":
        type_text(q.get("t", ""))
        if q.get("enter"):
            sh("input keyevent KEYCODE_ENTER")
    elif a == "open":
        pkg = q.get("pkg", "")
        if pkg.replace(".", "").replace("_", "").isalnum():
            sh("monkey -p %s -c android.intent.category.LEANBACK_LAUNCHER 1" % pkg)
    elif a == "wall":
        name = q.get("name", "")
        if name.replace(".", "").replace("_", "").replace("-", "").isalnum():
            sh("am start -n com.ansub.tvhome/.MainActivity --es stamp %d --es wall %s"
               % (int.from_bytes(os.urandom(4), "big"), name))
            sh("input keyevent KEYCODE_HOME")
    elif a == "free":   # stop apps sitting in the background; nothing is lost
        sh("am kill-all")
    elif a == "clean":  # drop cached files apps can rebuild
        sh("pm trim-caches 99G")
    elif a == "screensaver":
        sh("am start -n com.android.systemui/.Somnambulator")
    elif a == "timer":
        set_sleep_timer(int(q.get("mins", "0")) if q.get("mins", "0").isdigit() else 0)
    elif a == "reboot":
        adb("reboot", timeout=5)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, code, body, ctype="application/json"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body if isinstance(body, bytes) else body.encode())

    def do_GET(self):
        u = urlparse(self.path)
        parts = u.path.strip("/").split("/")
        if not parts or parts[0] != TOKEN:
            return self.send(404, "Not found", "text/plain")
        what = parts[1] if len(parts) > 1 else ""
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        try:
            if what == "":
                return self.send(200, PAGE.replace("__TV__", html.escape(TV)), "text/html; charset=utf-8")
            with lock:
                if not connected():
                    return self.send(503, json.dumps({"error": "TV not reachable. Is it on?"}))
                if what == "shot":
                    return self.send(200, adb("exec-out", "screencap", "-p", binary=True, timeout=20), "image/png")
                if what == "status":
                    return self.send(200, json.dumps(status()))
                if what == "apps":
                    return self.send(200, json.dumps(apps()))
                if what == "walls":
                    names = [n for n in sh("ls %s 2>/dev/null" % WALLS).split() if "." in n]
                    return self.send(200, json.dumps(names))
                if what == "wallimg":
                    n = q.get("name", "")
                    if not n.replace(".", "").replace("_", "").replace("-", "").isalnum():
                        return self.send(400, "{}")
                    return self.send(200, adb("exec-out", "cat", WALLS + "/" + n, binary=True), "image/jpeg")
                act(what, q)
                return self.send(200, "{}")
        except subprocess.TimeoutExpired:
            return self.send(504, json.dumps({"error": "The TV took too long to answer."}))


def lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect((TV.split(":")[0], 5555))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def load_token():
    path = os.path.join(HERE, ".token")
    if os.path.exists(path):
        return open(path).read().strip()
    t = secrets.token_urlsafe(9)
    with open(path, "w") as f:
        f.write(t)
    return t


PAGE = open(os.path.join(HERE, "remote.html"), encoding="utf-8").read()
TOKEN = load_token()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("Usage: python remote/tv_remote.py <TV_IP>")
    TV = sys.argv[1] if ":" in sys.argv[1] else sys.argv[1] + ":5555"
    url = "http://%s:%d/%s/" % (lan_ip(), PORT, TOKEN)
    print("TV remote for %s\n\n  On your phone (same Wi-Fi), open:\n  %s\n\n"
          "If Windows asks about the firewall, allow Python on private networks.\nCtrl+C stops it." % (TV, url), flush=True)
    try:
        ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
    except KeyboardInterrupt:
        pass
