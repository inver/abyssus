#!/usr/bin/env bash
# Runs scripts/run-ui-tests.sh inside the canonical Linux/Xvfb container (docker/ui-tests).
#
#   scripts/run-ui-tests-docker.sh [--fast] [--keep] [--clean] [--stop] [--tests <class-or-pattern>] [-- <extra gradle args>]
#
#   (default)  Canonical run: the container's platform is ABYSSUS_UI_PLATFORM (the screenshot baselines are linux/amd64), screenshots
#              are compared, and the container is removed afterwards.
#   --fast     Iteration run: the Docker host's own platform (no emulation on Apple Silicon), screenshot checks off, and --keep.
#              Never records or accepts baselines; use the canonical run for those and for the final verification.
#   --keep     Keeps the container, its Gradle daemon and the sandbox IDE running between runs, so a repeat run skips the IDE and
#              daemon start. The IDE restarts by itself when the plugin build changes.
#   --clean    Starts from an empty workspace (no build outputs of earlier runs); use it for the full runs that must come from fresh state.
#   --stop     Removes the kept container of the selected platform and exits.
#
# The working tree is copied into the container's workspace volume (abyssus-ui-work[-platform]), which keeps the build outputs of
# earlier runs so only changed modules are compiled again. Reports come back to projects/plugin-abyssus/build/reports/ui-tests.
# The Gradle cache (including the downloaded IDE) lives in the named volume abyssus-ui-gradle (abyssus-ui-gradle-linux-amd64 for
# ABYSSUS_UI_PLATFORM=linux/amd64: one volume per platform, since Gradle locks its cache and the IDE download differs).
# Baseline update (canonical platform only): scripts/run-ui-tests-docker.sh -- -PupdateUiScreenshots=true -PuiScreenshotScenarios=a,b
# Environment: ABYSSUS_UI_PLATFORM (e.g. linux/amd64, default: the Docker host's own; ignored by --fast), ABYSSUS_UI_IMAGE
# (default abyssus-ui-tests:dev).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${ABYSSUS_UI_IMAGE:-abyssus-ui-tests:dev}"
OUT="$ROOT/projects/plugin-abyssus/build/reports/ui-tests"
BASELINES="$ROOT/projects/plugin-abyssus/src/uiTest/resources/screenshots/linux"

FAST=0 KEEP=0 CLEAN=0 STOP=0
ARGS=()
while (($#)); do
  case "$1" in
    --fast) FAST=1; KEEP=1; shift ;;
    --keep) KEEP=1; shift ;;
    --clean) CLEAN=1; shift ;;
    --stop) STOP=1; shift ;;
    *) ARGS+=("$1"); shift ;;
  esac
done

PLATFORM_NAME="${ABYSSUS_UI_PLATFORM:-}"
((FAST)) && PLATFORM_NAME=""
SUFFIX=""
PLATFORM=()
if [[ -n "$PLATFORM_NAME" ]]; then PLATFORM=(--platform "$PLATFORM_NAME"); SUFFIX="-${PLATFORM_NAME//\//-}"; fi
VOLUME="abyssus-ui-gradle$SUFFIX"
WORKSPACE_VOLUME="abyssus-ui-work$SUFFIX"
KEEP_NAME="abyssus-ui-keep$SUFFIX"
((FAST)) && IMAGE="${ABYSSUS_UI_IMAGE:-abyssus-ui-tests:dev}-native" && KEEP_NAME="abyssus-ui-keep-native" && VOLUME="abyssus-ui-gradle-native" && WORKSPACE_VOLUME="abyssus-ui-work-native"

if ((STOP)); then docker rm -f "$KEEP_NAME" >/dev/null 2>&1 || true; echo "Removed $KEEP_NAME"; exit 0; fi

# Screenshot baselines are linux/amd64 only: a fast run does not compare them.
if ((FAST)); then
  has_shots=0; for a in "${ARGS[@]+"${ARGS[@]}"}"; do [[ "$a" == -PuiScreenshots=* || "$a" == -PupdateUiScreenshots=* ]] && has_shots=1; done
  if ((has_shots)); then echo "--fast does not take screenshot options; run the canonical container for baselines" >&2; exit 2; fi
  # -PuiScreenshots=off goes after the "--" separator the inner script understands
  if [[ "${ARGS[0]:-}" == "--tests" ]]; then ARGS=("${ARGS[@]:0:2}" -- -PuiScreenshots=off "${ARGS[@]:2}"); else ARGS=(-- -PuiScreenshots=off "${ARGS[@]+"${ARGS[@]}"}"); fi
  # an explicit "--" the caller wrote now sits after ours: drop the duplicate
  dedup=(); seen=0; for a in "${ARGS[@]}"; do if [[ "$a" == "--" ]]; then ((seen)) && continue; seen=1; fi; dedup+=("$a"); done; ARGS=("${dedup[@]}")
fi

docker build "${PLATFORM[@]+"${PLATFORM[@]}"}" -t "$IMAGE" "$ROOT/docker/ui-tests"
rm -rf "$OUT/failures" "$OUT/junit"
mkdir -p "$OUT" "$BASELINES"

if ((CLEAN)); then
  docker rm -f "$KEEP_NAME" >/dev/null 2>&1 || true
  docker volume rm -f "$WORKSPACE_VOLUME" >/dev/null
fi

MOUNTS=(-v "$ROOT":/src:ro -v "$VOLUME":/home/ui/.gradle -v "$WORKSPACE_VOLUME":/workspace -v "$OUT":/out -v "$BASELINES":/baselines-out)
ENVS=(${CI:+-e CI="$CI"} ${ABYSSUS_UI_FIXTURE:+-e ABYSSUS_UI_FIXTURE="$ABYSSUS_UI_FIXTURE"})
((KEEP)) && ENVS+=(-e ABYSSUS_UI_REUSE_IDE=1)

# The workspace volume keeps build outputs, so rsync must not touch them; stale reports are removed before the run.
INNER='
    rsync -a --delete --exclude .git --exclude /build --exclude /build-logic/build --exclude "/projects/*/build" --exclude .gradle --exclude .intellijPlatform --exclude .kotlin /src/ /workspace/
    cd /workspace
    rm -rf projects/plugin-abyssus/build/reports/ui-tests/failures projects/plugin-abyssus/build/reports/ui-tests/junit projects/plugin-abyssus/build/reports/tests/uiTest
    scripts/run-ui-tests.sh "$@"
    status=$?
    # baselines are copied back to the host only for an explicit update run, and only the images
    if printf "%s\n" "$@" | grep -q "updateUiScreenshots"; then
      rsync -a --include="*.png" --exclude="*" projects/plugin-abyssus/src/uiTest/resources/screenshots/linux/ /baselines-out/
    fi
    rsync -a projects/plugin-abyssus/build/reports/ui-tests/ /out/ 2>/dev/null
    rsync -a projects/plugin-abyssus/build/reports/tests/uiTest/ /out/junit/ 2>/dev/null
    exit $status'

set +e
if ((KEEP)); then
  if [[ -z "$(docker ps -q -f "name=^${KEEP_NAME}$")" ]]; then
    docker rm -f "$KEEP_NAME" >/dev/null 2>&1
    docker run -d --name "$KEEP_NAME" "${PLATFORM[@]+"${PLATFORM[@]}"}" --shm-size=1g "${MOUNTS[@]}" "$IMAGE" sleep infinity >/dev/null || exit 1
  fi
  docker exec "${ENVS[@]+"${ENVS[@]}"}" "$KEEP_NAME" bash -c "$INNER" ui-tests "${ARGS[@]+"${ARGS[@]}"}"
else
  docker run --rm "${PLATFORM[@]+"${PLATFORM[@]}"}" --shm-size=1g "${MOUNTS[@]}" "${ENVS[@]+"${ENVS[@]}"}" "$IMAGE" bash -c "$INNER" ui-tests "${ARGS[@]+"${ARGS[@]}"}"
fi
status=$?
exit $status
