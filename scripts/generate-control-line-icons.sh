#!/usr/bin/env bash
#
# Renders Control Line's application icons from packaging/icons/control-line-icon.svg into the same folder:
# control-line.png (1024 px, Linux and the AppImage), control-line.ico (Windows) and control-line.icns (macOS).
# The outputs are committed, so builds and CI need none of these tools; run this only when the SVG changes.
#
# Needs an SVG renderer (`rsvg-convert` or Inkscape; ImageMagick's own SVG renderer drops the transformed plane and the
# stroke colours, so it is not used for that step), ImageMagick (`magick`) to scale and pack, and macOS's `iconutil` for the
# .icns.
#
# Usage: scripts/generate-control-line-icons.sh

set -euo pipefail

root="$(git rev-parse --show-toplevel)"
dir="$root/projects/app-game-control-line/packaging/icons"
svg="$dir/control-line-icon.svg"

for tool in magick iconutil; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "error: '$tool' is required but not on the PATH (ImageMagick provides 'magick', macOS provides 'iconutil')" >&2
        exit 1
    fi
done

# The SVG renderer: rsvg-convert, else Inkscape from the PATH or the macOS application folder.
inkscape="$(command -v inkscape || true)"
[ -n "$inkscape" ] || { [ -x /Applications/Inkscape.app/Contents/MacOS/inkscape ] && inkscape=/Applications/Inkscape.app/Contents/MacOS/inkscape; }
if command -v rsvg-convert >/dev/null 2>&1; then
    render() { rsvg-convert --width 1024 --height 1024 --output "$2" "$1"; }
elif [ -n "${inkscape:-}" ]; then
    render() { "$inkscape" --export-type=png --export-width=1024 --export-height=1024 --export-filename="$2" "$1" >/dev/null; }
else
    echo "error: an SVG renderer is required: install rsvg-convert (brew install librsvg) or Inkscape" >&2
    exit 1
fi
[ -f "$svg" ] || { echo "error: $svg not found" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# One large render, so every smaller size is scaled down from the same pixels
render "$svg" "$work/1024.png"

size() { magick "$work/1024.png" -filter Lanczos -resize "${1}x${1}" "PNG32:$2"; }

cp "$work/1024.png" "$dir/control-line.png"

# Windows: a multi-size .ico
for s in 16 32 48 64 128 256; do size "$s" "$work/ico-$s.png"; done
magick "$work"/ico-16.png "$work"/ico-32.png "$work"/ico-48.png "$work"/ico-64.png "$work"/ico-128.png \
    "$work"/ico-256.png "$dir/control-line.ico"

# macOS: an iconset of the sizes Apple lists, packed by iconutil
iconset="$work/control-line.iconset"
mkdir "$iconset"
for s in 16 32 128 256 512; do
    size "$s" "$iconset/icon_${s}x${s}.png"
    size "$((s * 2))" "$iconset/icon_${s}x${s}@2x.png"
done
iconutil --convert icns --output "$dir/control-line.icns" "$iconset"

echo "wrote $dir/control-line.png, control-line.ico, control-line.icns"
