#!/usr/bin/env bash
#
# Startup smoke test of Control Line's Linux packages, run after `packageDeb` and `packageAppImage`:
#   1. unpacks the .deb (no root needed, nothing installed) and runs the editor-code check on its jars;
#   2. starts the unpacked launcher under Xvfb on Mesa software GL for SMOKE_SECONDS (default 20) and fails if it exits
#      early or logs an exception;
#   3. does the same with the AppImage (extract-and-run, as a runner has no FUSE).
# Needs dpkg-deb, xvfb-run and Mesa's software GL (libgl1-mesa-dri). Windows and macOS hosted runners have no usable
# OpenGL 3.3, so their startup is checked by hand (see the game's README).
#
# Usage: scripts/smoke-control-line-linux.sh

set -euo pipefail

root="$(git rev-parse --show-toplevel)"
build="$root/projects/app-game-control-line/build/packaging"
seconds="${SMOKE_SECONDS:-20}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

deb="$(ls "$build"/deb/control-line-*.deb | head -n 1)"
appimage="$(ls "$build"/appimage/control-line-*.AppImage | head -n 1)"

# Runs a launcher for $seconds under Xvfb: still running at the end is a pass, an earlier exit or an exception is a failure.
smoke() {
    local name="$1" log="$work/$1.log"
    shift
    set +e
    LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" timeout "$seconds" "$@" >"$log" 2>&1
    local status=$?
    set -e
    if [ "$status" -ne 124 ]; then
        echo "FAIL $name: the game exited with status $status before $seconds s" >&2
        tail -n 40 "$log" >&2
        exit 1
    fi
    if grep -E "Exception|Error:|NoClassDefFoundError" "$log" >&2; then
        echo "FAIL $name: the game logged an exception" >&2
        exit 1
    fi
    echo "ok   $name: running after $seconds s"
}

echo "unpacking $(basename "$deb")"
dpkg-deb -x "$deb" "$work/deb"
(cd "$root" && ./gradlew :app-game-control-line:checkExtractedPackage "-PpackageDir=$work/deb" --console=plain -q --no-daemon)
echo "ok   payload of the .deb holds the shipped game only"
smoke deb "$work/deb/opt/control-line/bin/Control Line"

chmod +x "$appimage"
APPIMAGE_EXTRACT_AND_RUN=1 smoke appimage "$appimage"
