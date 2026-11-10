#!/usr/bin/env bash
# Safe remote control for testing: refuses to send keys unless Shelf is the foreground app.
# Usage: ./tv.sh KEY [KEY...]   (keys like DPAD_RIGHT, DPAD_CENTER, BACK, or "--longpress DPAD_CENTER")
cd "$(dirname "$0")"
TV="${TV:-$(head -1 .tv-address 2>/dev/null)}"
[ -n "$TV" ] || { echo "Set TV=ip:5555 or put it in .tv-address"; exit 1; }
fg() { adb -s "$TV" shell dumpsys activity activities | grep -m1 mResumedActivity | grep -o '[a-z.]*/[A-Za-z.]*'; }
while [ $# -gt 0 ]; do
  cur=$(fg)
  case "$cur" in app.shelf.launcher/*) ;; *) echo "STOP: foreground is $cur, not Shelf"; exit 3 ;; esac
  if [ "$1" = "--longpress" ]; then adb -s "$TV" shell input keyevent --longpress "KEYCODE_$2"; shift 2
  else adb -s "$TV" shell input keyevent "KEYCODE_$1"; shift; fi
  sleep 0.8
done
