#!/usr/bin/env bash
# Personal sound override for YOUR TV only. Nothing here is ever committed or shipped: the clips live in the
# git-ignored ./private/sounds/ folder on your Mac and are copied to the TV's app files folder.
#
#   private/sounds/tick.*    played when you move up/down/left/right   (any audio/video file; trimmed to ~1.2 s)
#   private/sounds/ok.*      played on select
#   private/sounds/back.*    played on back
#   private/sounds/music.*   Wave background music (used as wave_music.*)
#
#   ./scripts/private-sounds.sh            convert + push whatever is in private/sounds/
#   ./scripts/private-sounds.sh --remove   delete the overrides from the TV (back to the built-in sounds)
#   add --restart to restart Shelf so it picks them up (only if Shelf is in front)
#
# You need to supply the clips yourself, and you are responsible for having the right to use them.
# Needs ffmpeg (brew install ffmpeg).
set -uo pipefail
cd "$(dirname "$0")/.."
TV="${TV:-$(head -1 .tv-address 2>/dev/null)}"
[ -n "$TV" ] || { echo "Set TV=ip:5555 or put it in .tv-address"; exit 2; }
PKG=app.shelf.launcher; DEST=/sdcard/Android/data/$PKG/files
RESTART=0; REMOVE=0
for a in "$@"; do case "$a" in --restart) RESTART=1 ;; --remove) REMOVE=1 ;; esac; done
A() { adb -s "$TV" "$@"; }
adb connect "$TV" >/dev/null 2>&1

if [ "$REMOVE" = 1 ]; then
  A shell "rm -f $DEST/sfx_tick.* $DEST/sfx_ok.* $DEST/sfx_back.* $DEST/wave_music.*"
  echo "Removed the personal overrides from the TV."
else
  command -v ffmpeg >/dev/null || { echo "ffmpeg is needed: brew install ffmpeg"; exit 2; }
  SRC=private/sounds; mkdir -p "$SRC"
  OUT=$(mktemp -d); n=0
  A shell "mkdir -p $DEST"
  for name in tick ok back; do
    f=$(ls "$SRC/$name".* 2>/dev/null | head -1)
    [ -n "$f" ] || continue
    # drop leading silence, keep at most 1.2 s, short fade-out, mono 44.1 kHz wav
    ffmpeg -loglevel error -y -i "$f" -vn -af "silenceremove=start_periods=1:start_threshold=-45dB,afade=t=out:st=1.0:d=0.2" \
      -t 1.2 -ac 1 -ar 44100 "$OUT/sfx_$name.wav" || { echo "Could not convert $f"; continue; }
    A shell "rm -f $DEST/sfx_$name.*"; A push "$OUT/sfx_$name.wav" "$DEST/sfx_$name.wav" >/dev/null && echo "  sfx_$name  <- $f" && n=$((n+1))
  done
  m=$(ls "$SRC"/music.* 2>/dev/null | head -1)
  if [ -n "$m" ]; then
    ffmpeg -loglevel error -y -i "$m" -vn -c:a aac -b:a 160k "$OUT/wave_music.m4a" && \
      { A shell "rm -f $DEST/wave_music.*"; A push "$OUT/wave_music.m4a" "$DEST/wave_music.m4a" >/dev/null && echo "  wave_music  <- $m" && n=$((n+1)); }
  fi
  [ "$n" = 0 ] && echo "Nothing found in $SRC (expected tick.*, ok.*, back.*, music.*)."
  rm -rf "$OUT"
fi

if [ "$RESTART" = 1 ]; then
  fg=$(A shell dumpsys window | grep -m1 mCurrentFocus)
  case "$fg" in *"$PKG"*) A shell "am force-stop $PKG; am start -n $PKG/.HomeActivity" >/dev/null; echo "Restarted Shelf." ;;
    *) echo "Someone is using the TV; not restarting. Restart Shelf yourself to apply." ;; esac
else
  echo "Restart Shelf to apply (or run again with --restart)."
fi
