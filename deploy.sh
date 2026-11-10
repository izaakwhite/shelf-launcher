#!/usr/bin/env bash
# Build, push, install (with the TCL `adb install` hang workaround), and launch Shelf.
# Usage: ./deploy.sh [--no-build] [--no-launch]
set -euo pipefail
cd "$(dirname "$0")"

# TV address: $TV, or the first line of .tv-address (git-ignored), e.g. 192.168.1.50:5555
TV="${TV:-$(head -1 .tv-address 2>/dev/null)}"
[ -n "$TV" ] || { echo "Set TV=ip:5555 or put it in .tv-address"; exit 1; }
PKG=app.shelf.launcher
ACT=app.shelf.launcher/.HomeActivity
APK=app/build/outputs/apk/release/app-release.apk
REMOTE=/data/local/tmp/shelf.apk

BUILD=1; LAUNCH=1
for a in "$@"; do
  case "$a" in
    --no-build) BUILD=0 ;;
    --no-launch) LAUNCH=0 ;;
  esac
done

# Toolchain (Homebrew openjdk@17 + android-commandlinetools)
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"

# Run a command with a time limit (macOS has no `timeout`).
with_timeout() { local secs=$1; shift; perl -e 'alarm shift; exec @ARGV' "$secs" "$@"; }

if [ "$BUILD" = 1 ]; then
  ./gradlew assembleRelease -q
fi

adb connect "$TV" >/dev/null
adb -s "$TV" wait-for-device
A() { adb -s "$TV" "$@"; }

# Installing a new HOME candidate makes Android forget the default home app.
# Remember the current default so we can put it back.
current_home() { A shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r'; }
HOME_BEFORE=$(current_home)

echo "Pushing APK..."
A push "$APK" "$REMOTE" >/dev/null

echo "Installing (90 s limit)..."
VER_BEFORE=$(A shell dumpsys package $PKG | grep -m1 lastUpdateTime || true)
if ! with_timeout 90 adb -s "$TV" shell pm install -r "$REMOTE"; then
  echo "pm install timed out or failed; checking whether it landed anyway..."
  adb connect "$TV" >/dev/null || true
  VER_AFTER=$(A shell dumpsys package $PKG | grep -m1 lastUpdateTime || true)
  if A shell pm list packages | grep -q "package:$PKG\$" && [ "$VER_BEFORE" != "$VER_AFTER" ]; then
    echo "Install succeeded despite the hang."
  else
    echo "Retrying install once..."
    with_timeout 90 adb -s "$TV" shell pm install -r "$REMOTE"
  fi
fi
A shell rm -f "$REMOTE"

# Ahead-of-time compile: this SoC is slow and JIT warm-up makes the first minutes janky.
echo "Compiling (AOT)..."
with_timeout 120 adb -s "$TV" shell cmd package compile -m speed -f $PKG >/dev/null || echo "AOT compile skipped"

HOME_AFTER=$(current_home)
if [ "$HOME_AFTER" != "$HOME_BEFORE" ] && [[ "$HOME_BEFORE" == */* ]] && [[ "$HOME_BEFORE" != *ResolverActivity* ]]; then
  echo "Default home was reset by the install; restoring $HOME_BEFORE"
  A shell cmd package set-home-activity "$HOME_BEFORE" >/dev/null
fi

FG_BEFORE=$(A shell dumpsys activity activities | grep -m1 mResumedActivity | grep -o '[a-z.]*/[A-Za-z.]*' || true)
if [ "$LAUNCH" = 1 ] && [ -n "$FG_BEFORE" ] && [[ "$FG_BEFORE" != app.shelf.launcher/* ]] && [[ "$FG_BEFORE" != *Resolver* ]] && [[ "$FG_BEFORE" != com.spocky.projengmenu/* ]]; then
  echo "Not launching: $FG_BEFORE is in the foreground (someone may be using the TV)."
  LAUNCH=0
fi
if [ "$LAUNCH" = 1 ]; then
  # If Shelf is the default home, go "home" so we reuse the real home task instead of a second copy.
  if [ "$(current_home)" = "$PKG/.HomeActivity" ]; then A shell input keyevent KEYCODE_HOME; else A shell am start -n "$ACT" >/dev/null; fi
  echo "Launched $ACT"
fi
