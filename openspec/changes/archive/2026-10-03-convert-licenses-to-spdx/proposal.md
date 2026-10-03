## Why

The repo's 221 source files carry the full Apache-2.0 boilerplate header (copyright line plus the
verbatim "Licensed under the Apache License, Version 2.0..." block). SPDX notation
(`SPDX-License-Identifier: Apache-2.0`) is the machine-readable standard and is what tooling,
scanners and license linters expect. Converting the headers keeps the same legal notice, makes the
license discoverable by automated tooling, and shrinks every file's header by ~10 lines.

## What Changes

- Replace the multi-line Apache-2.0 boilerplate header in every source file with the SPDX form:
  `// SPDX-License-Identifier: Apache-2.0` (or the `/* */` equivalent matching the file's existing
  comment style).
- Keep the copyright holder line (`Copyright 2023-2026 Alexey Nevinsky`) adjacent to the SPDX line.
- Update `README.md`'s license reference to use SPDX notation where it names the license.
- Leave the standalone `LICENSE` file untouched (it is the authoritative full text).

## Capabilities

This is a pure docs/convention change: no runtime behavior, file-format, API, or module-boundary change.
Per the project's OpenSpec rules it therefore declares no capabilities and opts out of specs.

### New Capabilities
- none

### Modified Capabilities
- none

`skip_specs: true` is set in `.openspec.yaml`.

## Impact

- ~221 source files across `src/`, `core/`, `gdx-model/`, and build scripts: `.kt`, `.java`,
  `.gradle.kts`, `.xml`, `.properties`, `.bnf`, `.flex`, `.yml`, `.yaml`, `.toml`, `.sh`, `.bat`.
- `README.md` license reference line.
- No behavior, API, dependency, or file-format change. No test fixtures are edited.
- Tooling that greps for the license string changes its pattern; update any internal scripts that match
  the old boilerplate text.