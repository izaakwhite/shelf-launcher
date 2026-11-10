#!/usr/bin/env bash
# End-to-end tests against a real Android TV running the installed Shelf build.
#
#   ./e2e/run.sh            run the suite (TV address from $TV or .tv-address)
#   ./e2e/run.sh --perf     also check frame jank while scrolling (needs the sim-kb input device)
#
# The app logs one "ShelfState" line per UI state change (style / focus / overlay / edit). The tests drive the
# remote with adb and assert on those lines. Nothing is launched over another app: the suite refuses to start
# unless Shelf (or Projectivy) is in the foreground. The original style is restored at the end.
set -uo pipefail
cd "$(dirname "$0")/.."
TV="${TV:-$(head -1 .tv-address 2>/dev/null)}"
[ -n "$TV" ] || { echo "Set TV=ip:5555 or put it in .tv-address"; exit 2; }
PKG=app.shelf.launcher
PERF=0; [ "${1:-}" = "--perf" ] && PERF=1

A() { adb -s "$TV" "$@"; }
pass=0; fail=0; failed=()
ok()   { echo "  ✓ $1"; pass=$((pass + 1)); }
bad()  { echo "  ✗ $1${2:+ — $2}"; fail=$((fail + 1)); failed+=("$1"); }
fg()   { A shell dumpsys activity activities | grep -m1 mResumedActivity | grep -o '[a-z.]*/[A-Za-z.]*'; }
key()  { A shell input keyevent "$@"; sleep 1.2; }
mark() { A logcat -c; }
# Last value of a ShelfState field logged since the last mark().
state() { A logcat -d -s ShelfState:I | grep -o "$1=[^ ]*" | tail -1 | cut -d= -f2-; }
wait_state() { # field expected timeout-seconds
  for _ in $(seq 1 "${3:-8}"); do [ "$(state "$1")" = "$2" ] && return 0; sleep 1; done; return 1; }

# Mean brightness (0-255) of the TV screen right now, from a real screenshot. Catches what the logs cannot:
# a screen stuck on the white splash, or blank. Needs ffmpeg on the host; those checks are skipped without it.
HAVE_FFMPEG=0; command -v ffmpeg >/dev/null 2>&1 && HAVE_FFMPEG=1
lum() { A exec-out screencap -p 2>/dev/null | ffmpeg -loglevel error -i - -vf scale=1:1:flags=area -f rawvideo -pix_fmt gray - 2>/dev/null | od -An -tu1 | tr -d ' \n'; }
# Poll until the screen's brightness satisfies "<op> <n>" (e.g. "-lt 200"), up to N seconds.
wait_lum() { local op=$1 n=$2 t=${3:-8} v; for _ in $(seq 1 "$t"); do v=$(lum); [ -n "$v" ] && [ "$v" "$op" "$n" ] && return 0; sleep 1; done; return 1; }
skip() { echo "  - $1 (skipped: $2)"; }

FILES=/sdcard/Android/data/$PKG/files
cleanup() { A shell rm -f "$FILES/no_music" >/dev/null 2>&1; }
trap cleanup EXIT

adb connect "$TV" >/dev/null 2>&1
A get-state >/dev/null 2>&1 || { echo "TV $TV not reachable"; exit 2; }
A shell pm list packages | grep -q "package:$PKG\$" || { echo "$PKG is not installed (run ./deploy.sh)"; exit 2; }
case "$(fg)" in $PKG/*|com.spocky.projengmenu/*) ;; *) echo "Someone is using the TV ($(fg)). Not running."; exit 3 ;; esac

A shell "mkdir -p $FILES && touch $FILES/no_music"    # keep Wave music off while testing (removed on exit)
echo "Shelf e2e on $TV ($(A shell dumpsys package $PKG | grep -m1 versionName | tr -d ' '))"

# Remember the user's style to restore afterwards.
key KEYCODE_HOME; sleep 2
ORIGINAL_STYLE=$(A shell am start -W -n $PKG/.HomeActivity >/dev/null 2>&1; sleep 2; state style)
[ -n "$ORIGINAL_STYLE" ] || { A shell am force-stop $PKG; mark; key KEYCODE_HOME; sleep 4; ORIGINAL_STYLE=$(state style); }
echo "  (current style: ${ORIGINAL_STYLE:-unknown})"

echo "Home screen"
[ "$(A shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r')" = "$PKG/.HomeActivity" ] \
  && ok "Shelf is the default home app" || bad "Shelf is the default home app"
key KEYCODE_HOME; [[ "$(fg)" == $PKG/* ]] && ok "Home brings Shelf to the front" || bad "Home brings Shelf to the front" "$(fg)"
key KEYCODE_BACK; [[ "$(fg)" == $PKG/* ]] && ok "Back on the home screen does not leave Shelf" || bad "Back leaves Shelf" "$(fg)"

echo "Theme shortcuts"
for s in aero grid wave; do   # wave last: a style that is already active logs no change
  cap="$(echo "${s:0:1}" | tr a-z A-Z)${s:1}"      # portable (macOS bash 3.2 has no ${s^})
  mark; A shell am start -n "$PKG/.Theme$cap" >/dev/null 2>&1
  wait_state style "$s" 10 && ok "Opening \"Shelf $cap\" switches to $s" || bad "Shelf $cap shortcut" "style=$(state style)"
done

echo "Wave navigation"
mark; A shell am start -n "$PKG/.ThemeWave" >/dev/null 2>&1; wait_state style wave 10; key KEYCODE_HOME; sleep 2
f0=$(state focus)
[ -n "$f0" ] && [ "$f0" != "null" ] && ok "Something is focused after Home ($f0)" || bad "Initial focus" "focus=$f0"
key KEYCODE_DPAD_DOWN; f1=$(state focus)
[ -n "$f1" ] && [ "$f1" != "$f0" ] && ok "Down moves to the next item" || bad "Down moves selection" "$f0 -> $f1"
key KEYCODE_DPAD_RIGHT; f2=$(state focus)
[ -n "$f2" ] && [ "$f2" != "$f1" ] && ok "Right switches category" || bad "Right switches category" "$f1 -> $f2"
key KEYCODE_DPAD_LEFT; [ "$(state focus)" = "$f1" ] && ok "Left goes back to the previous category" || bad "Left goes back" "focus=$(state focus) expected $f1"
key KEYCODE_HOME; sleep 2
[ "$(state focus)" = "$f0" ] && ok "Home returns to the first item" || bad "Home returns to start" "focus=$(state focus) expected $f0"

echo "Edit mode"
key --longpress KEYCODE_DPAD_CENTER
e=$(state edit); [ -n "$e" ] && [ "$e" != "null" ] && ok "Hold OK enters edit mode ($e)" || bad "Hold OK enters edit mode" "edit=$e"
key KEYCODE_BACK
[ "$(state edit)" = "null" ] && ok "Back cancels edit mode" || bad "Back cancels edit mode" "edit=$(state edit)"

echo "Settings overlay"
key KEYCODE_HOME
for _ in 1 2 3 4 5 6 7 8; do case "$(state focus)" in sys:*) break ;; esac; key KEYCODE_DPAD_RIGHT; done   # until the Settings category (its items are sys:*)
f=$(state focus)
# The Settings category lists Shelf settings first.
if [ "$f" = "sys:SHELF_SETTINGS" ]; then
  key KEYCODE_DPAD_CENTER
  [ "$(state overlay)" = "SETTINGS" ] && ok "OK on Shelf settings opens the panel" || bad "Settings panel opens" "overlay=$(state overlay)"
  key KEYCODE_BACK
  [ "$(state overlay)" = "NONE" ] && ok "Back closes the panel" || bad "Back closes the panel" "overlay=$(state overlay)"
else
  bad "Reach the Settings category" "focus=$f"
fi

echo "Launching"
key KEYCODE_HOME; for _ in 1 2 3 4 5 6 7 8; do case "$(state focus)" in sys:*) break ;; esac; key KEYCODE_DPAD_RIGHT; done
key KEYCODE_DPAD_DOWN   # Android settings
if [ "$(state focus)" = "sys:ANDROID_SETTINGS" ]; then
  key KEYCODE_DPAD_CENTER; sleep 2
  case "$(fg)" in $PKG/*) bad "OK on Android settings launches it" "still in Shelf" ;; *) ok "OK on Android settings launches another app ($(fg))" ;; esac
  key KEYCODE_HOME; sleep 2
  [[ "$(fg)" == $PKG/* ]] && ok "Home returns from the launched app" || bad "Home returns from the launched app" "$(fg)"
else
  bad "Reach Android settings" "focus=$(state focus)"
fi

echo "Layout persistence (moves an app down, then restores it)"
key KEYCODE_HOME; key KEYCODE_DPAD_RIGHT   # Apps category
k1=$(state focus)
case "$k1" in sys:*|"") bad "Apps category has apps" "focus=$k1" ;; *)
  key --longpress KEYCODE_DPAD_CENTER; key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_CENTER   # pick up, move down, drop
  A shell am force-stop $PKG; sleep 1; mark; key KEYCODE_HOME; sleep 4; key KEYCODE_DPAD_RIGHT
  k2=$(state focus)
  [ -n "$k2" ] && [ "$k2" != "$k1" ] && ok "A moved app stays moved after a restart" || bad "Moved app persists" "first app still $k2"
  # Restore: the original first app is now second; pick it up and move it back up.
  key KEYCODE_DPAD_DOWN
  if [ "$(state focus)" = "$k1" ]; then
    key --longpress KEYCODE_DPAD_CENTER; key KEYCODE_DPAD_UP; key KEYCODE_DPAD_CENTER
    key KEYCODE_HOME; sleep 2; key KEYCODE_DPAD_RIGHT
    [ "$(state focus)" = "$k1" ] && ok "Order restored" || bad "Order restored" "first app is $(state focus), expected $k1 — put it back by hand"
  else
    bad "Order restored" "could not find $k1 to move back — put it back by hand"
  fi ;;
esac

echo "Splash"
if [ "${SPLASH:-0}" != 1 ]; then
  skip "Splash appears and clears" "splash is unwired (run with SPLASH=1 once it is re-enabled)"
elif [ "$HAVE_FFMPEG" = 1 ]; then
  key KEYCODE_HOME; A shell am force-stop $PKG; sleep 1
  A shell am start -n "$PKG/.HomeActivity" >/dev/null 2>&1
  v=$(lum)
  [ -n "$v" ] && [ "$v" -gt 200 ] && ok "Cold start shows the white splash (brightness $v)" || bad "Cold start shows the splash" "brightness=$v"
  wait_lum -lt 150 12 && ok "Splash dissolves into the home screen (brightness $(lum))" || bad "Splash dissolves into the home screen" "brightness=$(lum) after 12 s — splash stuck?"
else
  skip "Splash appears and clears" "install ffmpeg"
fi

echo "Persistence"
mark; A shell am start -n "$PKG/.ThemeGrid" >/dev/null 2>&1; wait_state style grid 10
A shell am force-stop $PKG; sleep 1; mark; key KEYCODE_HOME
wait_state style grid 15 && ok "Style survives a force-stop" || bad "Style survives a force-stop" "style=$(state style)"
if [ "$HAVE_FFMPEG" = 1 ]; then
  wait_lum -lt 150 12 && wait_lum -gt 12 3 && ok "Screen shows the home UI, not blank or white (brightness $(lum))" || bad "Screen shows the home UI" "brightness=$(lum)"
fi

if [ "$PERF" = 1 ]; then
  echo "Performance (Aero, 50 presses)"
  mark; A shell am start -n "$PKG/.ThemeAero" >/dev/null 2>&1; wait_state style aero 10; sleep 2
  A shell dumpsys gfxinfo $PKG reset >/dev/null
  codes="$(for i in $(seq 12); do echo -n '106 '; done; for i in $(seq 12); do echo -n '105 '; done; echo -n '108 106 106 105 105 103 '; for i in $(seq 10); do echo -n '106 '; done; for i in $(seq 10); do echo -n '105 '; done)"
  A shell "for c in $codes; do sendevent /dev/input/event2 1 \$c 1; sendevent /dev/input/event2 0 0 0; sendevent /dev/input/event2 1 \$c 0; sendevent /dev/input/event2 0 0 0; sleep 0.25; done" 2>/dev/null
  jank=$(A shell dumpsys gfxinfo $PKG | grep -m1 'Janky frames' | grep -o '([0-9.]*%)' | tr -d '()%')
  awk "BEGIN{exit !($jank < 5)}" && ok "Janky frames ${jank}% < 5%" || bad "Janky frames < 5%" "${jank}%"
fi

# Restore the user's style.
case "$ORIGINAL_STYLE" in
  wave) A shell am start -n "$PKG/.ThemeWave" >/dev/null 2>&1 ;;
  grid) A shell am start -n "$PKG/.ThemeGrid" >/dev/null 2>&1 ;;
  aero*) A shell am start -n "$PKG/.ThemeAero" >/dev/null 2>&1 ;;
esac
sleep 2; key KEYCODE_HOME

echo; echo "$pass passed, $fail failed"
[ "$fail" = 0 ] || { printf '  failed: %s\n' "${failed[@]}"; exit 1; }
