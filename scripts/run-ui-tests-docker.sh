#!/usr/bin/env bash
# Runs scripts/run-ui-tests.sh inside the canonical Linux/Xvfb container (docker/ui-tests).
#
#   scripts/run-ui-tests-docker.sh [--tests <class-or-pattern>] [-- <extra gradle args>]
#
# The working tree is copied into the container, so Linux builds never touch the host's build directories. Reports come
# back to projects/plugin-abyssus/build/reports/ui-tests. The Gradle cache (including the downloaded IDE) lives in the
# named volume abyssus-ui-gradle (abyssus-ui-gradle-linux-amd64 for ABYSSUS_UI_PLATFORM=linux/amd64: one volume
# per platform, since Gradle locks its cache and the IDE download differs).
# Baseline update (canonical platform only): scripts/run-ui-tests-docker.sh -- -PupdateUiScreenshots=true -PuiScreenshotScenarios=a,b
# Environment: ABYSSUS_UI_PLATFORM (e.g. linux/amd64, default: the Docker host's own), ABYSSUS_UI_IMAGE (default
# abyssus-ui-tests:dev).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${ABYSSUS_UI_IMAGE:-abyssus-ui-tests:dev}"
OUT="$ROOT/projects/plugin-abyssus/build/reports/ui-tests"
BASELINES="$ROOT/projects/plugin-abyssus/src/uiTest/resources/screenshots/linux"
VOLUME="abyssus-ui-gradle"
PLATFORM=()
if [[ -n "${ABYSSUS_UI_PLATFORM:-}" ]]; then PLATFORM=(--platform "$ABYSSUS_UI_PLATFORM"); VOLUME="abyssus-ui-gradle-${ABYSSUS_UI_PLATFORM//\//-}"; fi

docker build "${PLATFORM[@]+"${PLATFORM[@]}"}" -t "$IMAGE" "$ROOT/docker/ui-tests"
rm -rf "$OUT/failures" "$OUT/junit"
mkdir -p "$OUT" "$BASELINES"

set +e
docker run --rm "${PLATFORM[@]+"${PLATFORM[@]}"}" --shm-size=1g \
  -v "$ROOT":/src:ro \
  -v "$VOLUME":/home/ui/.gradle \
  -v "$OUT":/out \
  -v "$BASELINES":/baselines-out \
  ${CI:+-e CI="$CI"} \
  "$IMAGE" bash -c '
    rsync -a --delete --exclude .git --exclude build --exclude .gradle --exclude .intellijPlatform --exclude .kotlin /src/ /workspace/
    cd /workspace
    scripts/run-ui-tests.sh "$@"
    status=$?
    # baselines are copied back to the host only for an explicit update run, and only the images
    if printf "%s\n" "$@" | grep -q "updateUiScreenshots"; then
      rsync -a --include="*.png" --exclude="*" projects/plugin-abyssus/src/uiTest/resources/screenshots/linux/ /baselines-out/
    fi
    rsync -a projects/plugin-abyssus/build/reports/ui-tests/ /out/ 2>/dev/null
    rsync -a projects/plugin-abyssus/build/reports/tests/uiTest/ /out/junit/ 2>/dev/null
    exit $status' ui-tests "$@"
status=$?
exit $status
