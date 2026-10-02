#!/usr/bin/env bash
#
# Fails when AGENTS.md or docs/ai/*.md quote a repository path that does not exist, so the docs cannot silently rot.
#
# A backtick-quoted span is checked as a path when it contains "/" or ends in a known file extension, and has no
# spaces, globs, placeholders or "=" (so `Gdx.*`, `./gradlew check` and `-Dabyssus.glTests=true` are skipped).
# An elided path such as `src/.../X.kt` is checked, and fails: write the real path.
# A path passes when it exists from the repository root, is git-ignored (generated output such as src/main/gen), or
# names the tail of a tracked file or folder (`dto/AssetReader.kt`, `Gltf.bnf`), since the docs cite paths relative
# to the package they describe. Fenced code blocks are not checked.
#
# Usage: scripts/check-docs.sh [file...]   (defaults to AGENTS.md and docs/ai/*.md)

set -euo pipefail

root="$(git rev-parse --show-toplevel)"
cd "$root"

if [ "$#" -gt 0 ]; then
    files=("$@")
else
    files=(AGENTS.md docs/ai/*.md)
fi

# Every tracked file and every folder above one, one per line.
known="$(mktemp)"
trap 'rm -f "$known"' EXIT
git ls-files | awk -F/ '{ p = ""; for (i = 1; i <= NF; i++) { p = (i == 1) ? $i : p "/" $i; print p } }' | sort -u > "$known"

ext='\.(kt|kts|java|md|json|xml|properties|bnf|flex|sh|yml|yaml|scene|abss|png|jpg|glsl|frag|vert|gltf|glb|zip)$'

is_path() {
    local s="$1"
    case "$s" in
        *" "* | *"*"* | *"?"* | *"["* | *"{"* | *"<"* | *">"* | *"="* | -* | "" ) return 1 ;;
    esac
    # a bare extension (`.scene`, `.scene.bak`) is not a path; a dotted folder (`.agents/commands/`) is
    [[ "$s" == .* && "$s" != */* ]] && return 1
    [[ "$s" == */* ]] || [[ "$s" =~ $ext ]]
}

exists() {
    local s="${1%/}"
    [ -e "$s" ] && return 0
    git check-ignore -q "$s" 2>/dev/null && return 0
    awk -v s="$s" '$0 == s || (length($0) > length(s) && substr($0, length($0) - length(s)) == "/" s) { found = 1; exit }
        END { exit !found }' "$known"
}

missing=0
checked=0
for f in "${files[@]}"; do
    [ -f "$f" ] || { echo "check-docs: no such file: $f" >&2; missing=$((missing + 1)); continue; }
    # drop fenced code blocks, then pull out `inline code` spans
    spans="$(awk '/^```/ { fence = !fence; next } !fence' "$f" | grep -o '`[^`]*`' | tr -d '`' || true)"
    while IFS= read -r span; do
        [ -n "$span" ] || continue
        is_path "$span" || continue
        checked=$((checked + 1))
        if ! exists "$span"; then
            echo "$f: \`$span\` does not exist" >&2
            missing=$((missing + 1))
        fi
    done <<< "$spans"
done

if [ "$missing" -gt 0 ]; then
    echo "check-docs: $missing broken path(s)" >&2
    exit 1
fi
echo "check-docs: $checked path(s) OK in ${#files[@]} file(s)"
