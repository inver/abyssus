#!/usr/bin/env bash
# Runs the Remote Robot UI tests end to end: copies the Untitled fixture, starts a sandbox IDE with robot-server,
# waits for it, runs :plugin-abyssus:uiTest, and stops the IDE it started (and only that one).
#
#   scripts/run-ui-tests.sh [--tests <class-or-pattern>] [-- <extra gradle args>]
#
# Environment: ABYSSUS_UI_PORT (default 8082), ABYSSUS_UI_STARTUP_TIMEOUT seconds (default 300),
# ABYSSUS_UI_KEEP_WORK=1 keeps the temporary project and sandbox.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${ABYSSUS_UI_PORT:-8082}"
STARTUP_TIMEOUT="${ABYSSUS_UI_STARTUP_TIMEOUT:-300}"
FIXTURE="$ROOT/projects/plugin-abyssus/src/test/testData/project/Untitled"
REPORTS="$ROOT/projects/plugin-abyssus/build/reports/ui-tests"
LOCK="${TMPDIR:-/tmp}/abyssus-ui-tests-$PORT.lock"

FILTER=()
if [[ "${1:-}" == "--tests" ]]; then FILTER=("-PuiTestFilter=$2"); shift 2; fi
[[ "${1:-}" == "--" ]] && shift

# One run per port: the sandbox, project copy and robot-server port are not shared.
if ! mkdir "$LOCK" 2>/dev/null; then
  echo "Another UI test run holds $LOCK (port $PORT). Remove it if that run is gone." >&2
  exit 3
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/abyssus-ui.XXXXXX")"
PROJECT="$WORK/project/Untitled"
SANDBOX="$WORK/sandbox"
IDE_LOG="$WORK/ide-gradle.log"
IDE_PID=""

if command -v shasum >/dev/null 2>&1; then HASHER="shasum -a 256"; else HASHER="sha256sum"; fi
hash_fixture() { (cd "$FIXTURE" && find . -type f ! -name .DS_Store ! -path './.idea/*' -print0 | sort -z | xargs -0 $HASHER | $HASHER); }

stop_ide() {
  [[ -n "$IDE_PID" ]] && kill "$IDE_PID" 2>/dev/null || true
  # The IDE is a child of the Gradle daemon, so also match it by its unique sandbox path.
  pkill -f "$SANDBOX" 2>/dev/null || true
  for _ in $(seq 1 30); do pgrep -f "$SANDBOX" >/dev/null 2>&1 || return 0; sleep 1; done
  pkill -9 -f "$SANDBOX" 2>/dev/null || true
}

cleanup() {
  local status=$?
  stop_ide
  mkdir -p "$REPORTS/ide-logs"
  find "$SANDBOX" -type d -name "log_*" -exec cp -R {}/. "$REPORTS/ide-logs/" \; 2>/dev/null || true
  cp "$IDE_LOG" "$REPORTS/ide-logs/gradle-runIdeForUiTests.log" 2>/dev/null || true
  [[ "${ABYSSUS_UI_KEEP_WORK:-0}" == "1" ]] || rm -rf "$WORK"
  rmdir "$LOCK" 2>/dev/null || true
  exit "$status"
}
trap cleanup EXIT INT TERM

before="$(hash_fixture)"
mkdir -p "$PROJECT" "$SANDBOX"
rsync -a --exclude .idea --exclude .DS_Store "$FIXTURE/" "$PROJECT/"

cd "$ROOT"
./gradlew --console=plain :plugin-abyssus:prepareSandbox_runIdeForUiTests :plugin-abyssus:uiTestClasses

./gradlew --console=plain :plugin-abyssus:runIdeForUiTests \
  "-PuiProject=$PROJECT" "-PuiSandbox=$SANDBOX" "-PuiPort=$PORT" >"$IDE_LOG" 2>&1 &
IDE_PID=$!

deadline=$((SECONDS + STARTUP_TIMEOUT))
until curl -fs -o /dev/null "http://127.0.0.1:$PORT"; do
  if ! kill -0 "$IDE_PID" 2>/dev/null; then echo "IDE exited before robot-server answered; see $IDE_LOG" >&2; tail -30 "$IDE_LOG" >&2; exit 4; fi
  if ((SECONDS > deadline)); then echo "robot-server did not answer on port $PORT within ${STARTUP_TIMEOUT}s" >&2; tail -30 "$IDE_LOG" >&2; exit 5; fi
  sleep 3
done

./gradlew --console=plain :plugin-abyssus:uiTest "-PuiProject=$PROJECT" "-PuiRobotUrl=http://127.0.0.1:$PORT" ${FILTER[@]+"${FILTER[@]}"} "$@"

after="$(hash_fixture)"
[[ "$before" == "$after" ]] || { echo "The committed Untitled fixture changed during the run" >&2; exit 6; }
