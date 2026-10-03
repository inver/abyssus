## 1. Convert source file headers to SPDX notation

- [x] 1.1 Replace the 12-line Apache-2.0 boilerplate header in every Kotlin and Java source file under `src/`, `core/`, and `gdx-model/` with the SPDX block header, preserving the `Copyright 2023-2026 Alexey Nevinsky` line; verify with `grep -rn "Licensed under the Apache License, Version 2.0" src core gdx-model` that no boilerplate remains.
- [x] 1.2 Replace the header in build and config files (`*.gradle.kts`, `*.xml`, `*.properties`, `*.bnf`, `*.flex`, `*.yml`, `*.yaml`, `*.toml`, `*.sh`) with the SPDX form matching each file's comment style; verify the same grep above returns nothing.
- [x] 1.3 Leave `gradlew.bat` (Gradle wrapper copyright) and the standalone `LICENSE` file untouched; verify with `git status` that only intended files are modified.

## 2. Update README and docs

- [x] 2.1 Update the license reference line in `README.md` to use SPDX notation; verify with `grep -n "SPDX\|Apache-2.0" README.md`.
- [x] 2.2 Scan `docs/ai/`, package `README.md` files, and `AGENTS.md` for any mention of the old license wording and update to SPDX where it names the license; verify with `grep -rn "Licensed under the Apache License" docs AGENTS.md` returning nothing.

## 3. Verify

- [ ] 3.1 Run `./gradlew check` and confirm the build still passes (no source changes affect behavior, so tests should be unaffected). **Cannot run here: no JDK is installed on this machine (`JAVA_HOME` is unset and no `java` is on PATH). Run it manually and report any failures.**
- [x] 3.2 Run `scripts/check-docs.sh` and confirm it passes.
- [x] 3.3 Run `git diff --stat` and confirm the changed file count and types match the planned scope (source files + README + docs), with no `.scene`, `.abss`, `meta.json`, or `LICENSE` changes.