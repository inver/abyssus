#!/usr/bin/env bash
# Signs a macOS .app for notarization: every Mach-O file inside it, the native libraries inside its jars (the notarization
# service looks into archives and rejects an unsigned .dylib there), then the bundle itself.
#
#   scripts/sign-macos-app.sh <App.app> <identity> <entitlements.plist>
#
# <identity> is a "Developer ID Application: ..." name or its SHA-1 hash. Set MACOS_KEYCHAIN to sign from a keychain that is
# not in the search list. Used by the packaging build (JPackage) when ABYSSUS_MAC_SIGN_IDENTITY is set.
set -euo pipefail

app="${1:?usage: sign-macos-app.sh <App.app> <identity> <entitlements.plist>}"
identity="${2:?missing signing identity}"
entitlements="${3:?missing entitlements file}"

sign_args=(--force --timestamp --options runtime --entitlements "$entitlements" --sign "$identity")
if [ -n "${MACOS_KEYCHAIN:-}" ]; then sign_args+=(--keychain "$MACOS_KEYCHAIN"); fi

# the native libraries inside jars: unpack, sign, and put them back into the jar
while IFS= read -r -d '' jar; do
  jar="$(cd "$(dirname "$jar")" && pwd)/$(basename "$jar")"
  # list first: `unzip | grep -q` can die of SIGPIPE, and under pipefail the jar would be skipped without a word
  entries="$(unzip -Z1 "$jar")"
  grep -qE '\.(dylib|jnilib)$' <<< "$entries" || continue
  work="$(mktemp -d)"
  (
    cd "$work"
    unzip -q "$jar" '*.dylib' '*.jnilib' 2>/dev/null || true
    find . -type f \( -name '*.dylib' -o -name '*.jnilib' \) -print0 | xargs -0 -n1 codesign "${sign_args[@]}"
    find . -type f \( -name '*.dylib' -o -name '*.jnilib' \) | sed 's|^\./||' | zip -q "$jar" -@
  )
  # prove the jar now holds Developer ID signed libraries with a secure timestamp (what notarization checks)
  check="$(mktemp -d)"
  unzip -q "$jar" '*.dylib' '*.jnilib' -d "$check" 2>/dev/null || true
  while IFS= read -r -d '' lib; do
    info="$(codesign -dvv "$lib" 2>&1)"
    if ! grep -q 'Authority=Developer ID Application' <<< "$info" || ! grep -q '^Timestamp=' <<< "$info"; then
      echo "error: $lib in $(basename "$jar") is not Developer ID signed with a timestamp" >&2
      echo "$info" >&2
      exit 1
    fi
  done < <(find "$check" -type f \( -name '*.dylib' -o -name '*.jnilib' \) -print0)
  rm -rf "$check"
  rm -rf "$work"
  echo "signed native libraries in $(basename "$jar")"
done < <(find "$app" -type f -name '*.jar' -print0)

# every Mach-O file of the bundle, deepest first, the bundle's own executable and the bundle last
while IFS= read -r -d '' f; do
  if file -b "$f" | grep -q 'Mach-O'; then codesign "${sign_args[@]}" "$f"; fi
done < <(find "$app/Contents" -depth -type f -not -path "$app/Contents/MacOS/*" -print0)
find "$app/Contents/MacOS" -type f -print0 | xargs -0 -n1 codesign "${sign_args[@]}"
codesign "${sign_args[@]}" "$app"

codesign --verify --deep --strict --verbose=2 "$app"
