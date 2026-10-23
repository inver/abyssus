#!/usr/bin/env bash
# Runs the Remote Robot UI tests end to end: copies the Untitled fixture (or ABYSSUS_UI_FIXTURE), starts a sandbox IDE with robot-server,
# waits for it, runs :plugin-abyssus:uiTest, and stops the IDE it started (and only that one).
#
#   scripts/run-ui-tests.sh [--tests <class-or-pattern>] [-- <extra gradle args>]
#
# Environment: ABYSSUS_UI_PORT (default 8082), ABYSSUS_UI_STARTUP_TIMEOUT seconds (default 300),
# ABYSSUS_UI_KEEP_WORK=1 keeps the temporary project and sandbox.
# ABYSSUS_UI_REUSE_IDE=1 leaves the sandbox IDE running after the run and reuses it (and its project copy) on the next run as long as the
# plugin build, the fixture and the port are unchanged; a changed plugin build restarts it. Stop it by killing the process or the container.
# ABYSSUS_UI_FIXTURE=<project folder, absolute or relative to the repository> runs against that project instead of Untitled
# (e.g. projects/app-game-control-line/project/ControlLine with --tests '*ControlLine*'); each test class runs only on its own project.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${ABYSSUS_UI_PORT:-8082}"
STARTUP_TIMEOUT="${ABYSSUS_UI_STARTUP_TIMEOUT:-300}"
FIXTURE="${ABYSSUS_UI_FIXTURE:-$ROOT/projects/plugin-abyssus/src/test/testData/project/Untitled}"
[[ "$FIXTURE" == /* ]] || FIXTURE="$ROOT/$FIXTURE"
FIXTURE="$(cd "$FIXTURE" && pwd)"
REPORTS="$ROOT/projects/plugin-abyssus/build/reports/ui-tests"
LOCK="${TMPDIR:-/tmp}/abyssus-ui-tests-$PORT.lock"
REUSE="${ABYSSUS_UI_REUSE_IDE:-0}"
STATE="${TMPDIR:-/tmp}/abyssus-ui-reuse-$PORT"
KEEP_IDE=0

FILTER=()
if [[ "${1:-}" == "--tests" ]]; then FILTER=("-PuiTestFilter=$2"); shift 2; fi
[[ "${1:-}" == "--" ]] && shift

# One run per port: the sandbox, project copy and robot-server port are not shared.
if ! mkdir "$LOCK" 2>/dev/null; then
  echo "Another UI test run holds $LOCK (port $PORT). Remove it if that run is gone." >&2
  exit 3
fi

WORK=""
PROJECT=""
SANDBOX=""
IDE_LOG=""
IDE_PID=""

if command -v shasum >/dev/null 2>&1; then HASHER="shasum -a 256"; else HASHER="sha256sum"; fi
hash_fixture() { (cd "$FIXTURE" && find . -type f ! -name .DS_Store ! -path './.idea/*' -print0 | sort -z | xargs -0 $HASHER | $HASHER); }

stop_ide() {
  [[ -n "$IDE_PID" ]] && kill "$IDE_PID" 2>/dev/null || true
  # The IDE is a child of the Gradle daemon, so also match it by its unique sandbox path.
  [[ -n "$SANDBOX" ]] || return 0
  pkill -f "$SANDBOX" 2>/dev/null || true
  for _ in $(seq 1 30); do pgrep -f "$SANDBOX" >/dev/null 2>&1 || return 0; sleep 1; done
  pkill -9 -f "$SANDBOX" 2>/dev/null || true
}

cleanup() {
  local status=$?
  [[ $status -ne 6 ]] || KEEP_IDE=0
  [[ "$KEEP_IDE" == "1" ]] || { [[ -n "$SANDBOX" ]] && stop_ide; rm -rf "$STATE"; }
  mkdir -p "$REPORTS/ide-logs"
  [[ -n "$SANDBOX" ]] && find "$SANDBOX" -type d -name "log_*" -exec cp -R {}/. "$REPORTS/ide-logs/" \; 2>/dev/null || true
  [[ -n "$IDE_LOG" ]] && cp "$IDE_LOG" "$REPORTS/ide-logs/gradle-runIdeForUiTests.log" 2>/dev/null || true
  [[ "${ABYSSUS_UI_KEEP_WORK:-0}" == "1" || "$KEEP_IDE" == "1" || -z "$WORK" ]] || rm -rf "$WORK"
  rmdir "$LOCK" 2>/dev/null || true
  exit "$status"
}
trap cleanup EXIT INT TERM

before="$(hash_fixture)"

# A running IDE from an earlier run is reusable when it still answers, serves this fixture and the plugin build is unchanged.
reusable=0
if [[ "$REUSE" == "1" && -f "$STATE/work" ]]; then
  OLD_WORK="$(cat "$STATE/work")"; OLD_PID="$(cat "$STATE/pid" 2>/dev/null || true)"
  if [[ -d "$OLD_WORK" && "$(cat "$STATE/fixture" 2>/dev/null)" == "$FIXTURE" && -n "$OLD_PID" ]] && kill -0 "$OLD_PID" 2>/dev/null \
     && curl -fs -o /dev/null "http://127.0.0.1:$PORT"; then reusable=1; fi
fi

cd "$ROOT"
./gradlew --console=plain :plugin-abyssus:prepareSandbox_runIdeForUiTests :plugin-abyssus:uiTestClasses
# prepareSandbox always runs, so the plugin build is fingerprinted by the size and time of the module jars (GNU stat: the container)
fingerprint="$(find projects/*/build/libs -name '*.jar' -exec stat -c '%n %s %Y' {} + 2>/dev/null | sort | $HASHER | cut -d' ' -f1)" || fingerprint=""
plugin_unchanged=0
[[ "$reusable" == "1" && "$(cat "$STATE/fingerprint" 2>/dev/null)" == "$fingerprint" ]] && plugin_unchanged=1

if [[ "$reusable" == "1" && "$plugin_unchanged" == "1" ]]; then
  echo "Reusing the running sandbox IDE (pid $OLD_PID)"
  WORK="$OLD_WORK"; IDE_PID="$OLD_PID"
  PROJECT="$WORK/project/$(basename "$FIXTURE")"; SANDBOX="$WORK/sandbox"; IDE_LOG="$WORK/ide-gradle.log"
else
  if [[ "$reusable" == "1" ]]; then
    echo "The plugin build changed: restarting the sandbox IDE"
    WORK="$OLD_WORK"; SANDBOX="$WORK/sandbox"; IDE_PID="$OLD_PID"
    stop_ide; rm -rf "$WORK" "$STATE"; IDE_PID=""
  fi
  WORK="$(mktemp -d "${TMPDIR:-/tmp}/abyssus-ui.XXXXXX")"
  PROJECT="$WORK/project/$(basename "$FIXTURE")"
  SANDBOX="$WORK/sandbox"
  IDE_LOG="$WORK/ide-gradle.log"
  mkdir -p "$PROJECT" "$SANDBOX"
  rsync -a --exclude .idea --exclude .DS_Store "$FIXTURE/" "$PROJECT/"

  ./gradlew --console=plain :plugin-abyssus:runIdeForUiTests \
    "-PuiProject=$PROJECT" "-PuiSandbox=$SANDBOX" "-PuiPort=$PORT" >"$IDE_LOG" 2>&1 &
  IDE_PID=$!

  deadline=$((SECONDS + STARTUP_TIMEOUT))
  until curl -fs -o /dev/null "http://127.0.0.1:$PORT"; do
    if ! kill -0 "$IDE_PID" 2>/dev/null; then echo "IDE exited before robot-server answered; see $IDE_LOG" >&2; tail -30 "$IDE_LOG" >&2; exit 4; fi
    if ((SECONDS > deadline)); then echo "robot-server did not answer on port $PORT within ${STARTUP_TIMEOUT}s" >&2; tail -30 "$IDE_LOG" >&2; exit 5; fi
    sleep 3
  done
fi

# The IDE answers: remember it for the next run (a failing test run keeps it too; the tests reset their own state).
if [[ "$REUSE" == "1" ]]; then
  KEEP_IDE=1
  mkdir -p "$STATE"
  printf '%s' "$WORK" >"$STATE/work"; printf '%s' "$IDE_PID" >"$STATE/pid"; printf '%s' "$FIXTURE" >"$STATE/fixture"
  printf '%s' "$fingerprint" >"$STATE/fingerprint"
fi

./gradlew --console=plain :plugin-abyssus:uiTest "-PuiProject=$PROJECT" "-PuiFixtureSource=$FIXTURE" "-PuiRobotUrl=http://127.0.0.1:$PORT" ${FILTER[@]+"${FILTER[@]}"} "$@"

after="$(hash_fixture)"
[[ "$before" == "$after" ]] || { echo "The committed Untitled fixture changed during the run" >&2; exit 6; }
