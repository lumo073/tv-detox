#!/bin/sh
# Keeps the TV clean on its own. Run it from Task Scheduler (or cron) every day: when the TV is on,
# it puts back anything an update turned on again and clears caches; when it's off, it does nothing.
#
#   sh maintain.sh <TV_IP> [list.txt]          (list defaults to my-tv.txt)
#
# Results go to maintain.log next to this script.
cd "$(dirname "$0")"
export MSYS2_ARG_CONV_EXCL="/sdcard;/data;/system" # Git Bash on Windows: keep TV paths as they are
TARGET="$1"; LIST="${2:-my-tv.txt}"
[ -n "$TARGET" ] || { echo "Usage: sh maintain.sh <TV_IP> [list.txt]" >&2; exit 1; }
# Task Scheduler starts with a bare PATH: look for adb where Android Studio and Homebrew put it.
for d in "$LOCALAPPDATA/Android/Sdk/platform-tools" "$HOME/AppData/Local/Android/Sdk/platform-tools" \
         "$HOME/Library/Android/sdk/platform-tools" /opt/homebrew/bin /usr/local/bin; do
  [ -x "$d/adb" ] || [ -x "$d/adb.exe" ] && PATH="$d:$PATH"
done
LOG=maintain.log
{
  echo "== $(date '+%Y-%m-%d %H:%M')"
  if sh check.sh "$TARGET" --fix "$LIST"; then :; else echo "(see above)"; fi
  case "$TARGET" in *:*) TV=$TARGET ;; *) TV=$TARGET:5555 ;; esac
  if [ "$(adb -s "$TV" get-state 2>/dev/null)" = device ]; then
    adb -s "$TV" shell pm trim-caches 99G
    echo "Caches cleared. Free memory: $(adb -s "$TV" shell grep MemAvailable /proc/meminfo | tr -d '\r' | awk '{print int($2/1024)}') MB"
  fi
} >> "$LOG" 2>&1
# Keep the log short.
tail -n 400 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
