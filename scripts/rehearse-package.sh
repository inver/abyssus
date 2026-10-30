#!/usr/bin/env bash
#
# Packaging rehearsal: builds the unsigned plugin zip and checks its contents (no Jolt in lib/, play-host present),
# without publish credentials or a release trigger. CI runs it on Java 21 (the build's baseline). Result: projects/plugin-abyssus/build/distributions/*.zip
#
# Usage: scripts/rehearse-package.sh

set -euo pipefail

root="$(git rev-parse --show-toplevel)"
cd "$root"

# no signing or publishing input may leak into the rehearsal
unset PUBLISH_TOKEN PRIVATE_KEY PRIVATE_KEY_PASSWORD CERTIFICATE_CHAIN

./gradlew :plugin-abyssus:buildPlugin :plugin-abyssus:checkNoJoltInZip
ls -l projects/plugin-abyssus/build/distributions/*.zip
