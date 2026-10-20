#!/usr/bin/env sh

set -e

# Gradle must run from the repository root, whatever the caller's working folder
cd "$(dirname "$0")/.."

./gradlew :app-game-control-line:installDist -Pabyssus.universalDist=true --no-daemon
sh projects/app-game-control-line/build/install/app-game-control-line/bin/app-game-control-line
