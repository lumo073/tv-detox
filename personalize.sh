#!/bin/sh
# Personal touches for the home screen: your wallpapers, a greeting, watch time and a photo frame.
#
#   sh personalize.sh <TV_IP> [name] [bedtime]
#   sh personalize.sh 192.168.1.69 Lumo 23:00
#
# - Pictures in wallpapers/ are shrunk to the TV's screen size (needs Python with Pillow; without it
#   they're sent as they are, which also works, just bigger) and copied to the home screen's folder.
#   The home screen shows a new one each day; the picture icon by the clock picks one instead.
# - The greeting under the clock uses your name; from bedtime until 5 am it says it's time to wind down.
# - Watch time today needs usage access, which this turns on for the home screen only.
# - The screensaver becomes a photo frame of the same pictures. The TV still sleeps when it did before.
set -e
export MSYS2_ARG_CONV_EXCL="/sdcard;/data;/system" # Git Bash on Windows: keep TV paths as they are
cd "$(dirname "$0")"

TARGET="$1"; NAME="$2"; BEDTIME="$3"
[ -n "$TARGET" ] || { echo "Usage: sh personalize.sh <TV_IP> [name] [bedtime like 23:00]" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV. Is the TV on?" >&2; exit 1; }
sh_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
sh_tv pm list packages com.ansub.tvhome | grep -q . || { echo "Install the home screen first: sh install.sh $TARGET" >&2; exit 1; }

DIR=/sdcard/Android/data/com.ansub.tvhome/files/wallpapers
if [ -d wallpapers ] && ls wallpapers | grep -qiE '\.(jpe?g|png|webp)$'; then
  size=$(sh_tv wm size | tail -1 | sed 's/.*: *//')      # like 1280x720
  W=${size%x*}; H=${size#*x}
  tmp=$(mktemp -d)
  if python -I -c "import PIL" 2>/dev/null; then
    echo "Shrinking wallpapers to ${W}x${H}…"
    python -I - "$tmp" "$W" "$H" wallpapers/* <<'EOF'
import sys, os
from PIL import Image, ImageOps
out, w, h = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
for src in sys.argv[4:]:
    if not src.lower().endswith((".jpg", ".jpeg", ".png", ".webp")): continue
    im = ImageOps.exif_transpose(Image.open(src)).convert("RGB")
    im = ImageOps.fit(im, (w, h), Image.LANCZOS)  # center crop to the screen's shape
    name = os.path.splitext(os.path.basename(src))[0] + ".jpg"
    im.save(os.path.join(out, name), quality=90, optimize=True)
    print("  %s  %d KB" % (name, os.path.getsize(os.path.join(out, name)) // 1024))
EOF
  else
    echo "Python with Pillow isn't here, so the pictures go as they are."
    cp wallpapers/* "$tmp"/
  fi
  sh_tv "rm -rf $DIR; mkdir -p $DIR"
  for f in "$tmp"/*; do adb -s "$TV" push "$f" "$DIR/" >/dev/null; done
  rm -rf "$tmp"
  echo "Sent $(sh_tv ls $DIR | wc -l) wallpapers."
else
  echo "No pictures in wallpapers/, skipping wallpapers."
fi

# Watch time under the clock.
sh_tv appops set com.ansub.tvhome GET_USAGE_STATS allow

# Name, bedtime and a fresh wallpaper pick, applied once (see saveConfig in MainActivity).
set -- --es stamp "$(date +%s)" --es wall daily
[ -n "$NAME" ] && set -- "$@" --es name "\"$NAME\""
[ -n "$BEDTIME" ] && set -- "$@" --es bedtime "$BEDTIME"
sh_tv am start -n com.ansub.tvhome/.MainActivity "$@" >/dev/null

# Photo frame screensaver after 5 minutes. Sleep stays as it was (it ends the photo frame).
sh_tv settings put secure screensaver_components com.ansub.tvhome/.PhotoFrame
sh_tv settings put secure screensaver_enabled 1
sh_tv settings put secure screensaver_activate_on_sleep 0
sh_tv settings put system screen_off_timeout 300000
echo "Photo frame screensaver on: it starts after 5 minutes of no remote use."
adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "Done. Press Home on your remote."
