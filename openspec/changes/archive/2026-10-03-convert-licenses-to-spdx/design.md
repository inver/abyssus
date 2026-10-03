## Context

See proposal.md - Why. The repo has 221 source files carrying the same 12-line Apache-2.0
boilerplate header. The change converts that header to SPDX notation while preserving the
copyright holder line.

## Goals / Non-Goals

**Goals:**
- Every source file ends up with an SPDX license identifier (`Apache-2.0`) that scanners and
  linters can match without parsing natural-language text.
- The copyright holder line (`Copyright 2023-2026 Alexey Nevinsky`) is preserved adjacent to the
  SPDX line.
- The change is mechanical: no logic, formatting, or file-format content moves.

**Non-Goals:**
- No change to any `.scene`, `.abss`, `meta.json`, or other data file content.
- No change to the standalone `LICENSE` file (it remains the authoritative full text).
- No new tooling, CI step, or license scanner is introduced by this change.

## Decisions

- **SPDX form**: `// SPDX-License-Identifier: Apache-2.0` for `//`-style files (Kotlin, Java,
  shell, batch) and `/* SPDX-License-Identifier: Apache-2.0 */` for block-comment files where
  the header is already a `/* */` block. The existing block headers are replaced wholesale with
  a two-line block header:
  ```
  /*
   * Copyright 2023-2026 Alexey Nevinsky
   * SPDX-License-Identifier: Apache-2.0
   */
  ```
  This matches the SPDX recommended style and keeps the copyright line.
- **Where the SPDX line goes**: immediately after the copyright line, inside the existing comment
  block, at the top of the file.
- **gradlew.bat**: it is a generated Gradle wrapper script with its own copyright
  (`Copyright 2015 the original author or authors`). It is left untouched; it is not Abyssus code.
- **Generated sources** (`src/main/gen`) are git-ignored and regenerated; they are not edited.
- **Third-party / bundled files** (e.g. inside `.intellijPlatform/`) are out of scope.

## Risks / Trade-offs

- [Risk] A regex-based replacement could mangle a file that has a slightly different header
  variant → Mitigation: review the diff per file type before committing; the header is uniform
  across the repo (verified: one boilerplate form in 221 files).
- [Risk] Tooling that greps for the old `"Licensed under the Apache License, Version 2.0"` string
  stops matching → Mitigation: this is the intended outcome; update any internal scripts in the
  same change that match that text.
- [Trade-off] SPDX notation is less human-readable than the full boilerplate → accepted; the
  full text remains in `LICENSE`.

## Migration Plan

1. Apply the header replacement across all 221 files.
2. Update `README.md`'s license reference line to SPDX notation.
3. Verify with `grep` that no file still contains the old boilerplate `Licensed under the Apache
   License, Version 2.0` text (except `LICENSE` and `gradlew.bat`, which are intentionally kept).
4. Run `./gradlew check` and `scripts/check-docs.sh`.

## Open Questions

- None. The approach is mechanical and the scope is fully enumerated by the existing header form.