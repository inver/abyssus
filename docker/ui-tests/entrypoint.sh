#!/usr/bin/env bash
# Starts the virtual display and a minimal window manager, then runs the given command.
set -euo pipefail

rm -f /tmp/.X99-lock
Xvfb "$DISPLAY" -screen 0 "$ABYSSUS_UI_SCREEN" -nolisten tcp -ac >/tmp/xvfb.log 2>&1 &
for _ in $(seq 1 50); do xdpyinfo -display "$DISPLAY" >/dev/null 2>&1 && break; sleep 0.2; done
xdpyinfo -display "$DISPLAY" >/dev/null 2>&1 || { echo "Xvfb did not start:" >&2; cat /tmp/xvfb.log >&2; exit 1; }

# A window manager gives the IDE frame focus and a stable position.
openbox >/tmp/openbox.log 2>&1 &

exec "$@"
