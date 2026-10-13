# Design

## Context

See proposal.md for motivation and scope. The build is multi-module: the root `build.gradle.kts` only declares plugin versions (IntelliJ Platform Gradle Plugin 2.19.0) and aggregates coverage, and the plugin is `projects/plugin-abyssus` (build file `plugin-abyssus.gradle.kts`) on IC 2025.2.4, Java 21, and JUnit 4. `runIdeForUiTests` is defined there; it already installs robot-server on port 8082 and trusts all projects, but takes no project or sandbox inputs (unlike `runIde`, which accepts `-PideProject`), and no Remote Robot client dependency or dedicated test source set exists. The manual UI workflow starts an IDE across three operating systems, uses Java 11, and runs ordinary `test` rather than UI tests. `DesignScreenshotTest` paints isolated components and reports design measurements; it is not a real IDE pixel regression suite.

Entity controls already expose component names (`field-<Kind>-<field>`, `error-<Kind>-<field>`, `remove-<Kind>`, `add-component`) and asset property editors expose `asset-field-<key>`. `SkyboxChooserDialog` has no component names; locate it by dialog title, class and row text (its `rows`/`foundLabel` hooks are IDE-process internal and not callable from the client). Tree eye and chooser actions are painted inside rows rather than separate Swing buttons. Skybox thumbnails load asynchronously. These affect locators and readiness checks.

The Untitled fixture's `Main Scene.scene` currently has scene ID 0, name `Ololo`, `fogEnabled: true`, and `skyboxName: skybox_physical`. Entity 0 is `Model 0` and entity 4 is `Camera 4`. Read these values during setup instead of assuming historical documentation labels. The document writer and panel collaborators have been refactored (`editSceneJson`, `lib-core-editor`'s panel model); tests must drive the installed UI without depending on their constructors. Asset editing has landed: supported terrain, cube-skybox and procedural-sky properties are editable, while HDR skyboxes and unsupported assets stay read-only with the `read-only` suffix. The fixture has one skybox of each kind: `skybox_physical` (procedural, the scene's current sky, editable), `skybox_default` (cube, editable) and `skybox_hdr` (HDR, read-only). Initial screenshots record the implemented asset panels.

## Goals / Non-Goals

**Goals:** Verify user workflows in a real installed plugin, produce reproducible visual failures, and keep fixture data and regular tests isolated from IDE automation.

**Non-Goals:** No production behavior changes, renderer hooks, GL context initialization, platform-wide baseline matrix, or replacement of existing tests. This tooling-only change skips spec deltas.

## Decisions

### 1. Dedicated test process and controlled lifecycle

Create `projects/plugin-abyssus/src/uiTest/kotlin` and a `:plugin-abyssus:uiTest` Gradle Test task using JUnit 4, Remote Robot, and remote-fixtures. Keep its dependency configuration separate from IntelliJ's in-process platform test runtime and from plugin runtime dependencies. Pin one compatible Remote Robot release for client, fixtures, and server after a real IC 2025.2.4 connection test. No IDE or Gradle plugin upgrade is part of this change; report incompatibility if it cannot be solved within that constraint.

Use a repository runner, proposed as `scripts/run-ui-tests.sh`, for the complete Linux/local POSIX lifecycle: prepare fixture copies and the dedicated sandbox, launch `./gradlew :plugin-abyssus:runIdeForUiTests` in the background, poll robot-server with a timeout, run `./gradlew :plugin-abyssus:uiTest`, and terminate the launched process tree in an exit trap. Preserve IDE logs before cleanup. Prebuild the test classes/plugin before launching and use distinct Gradle invocations for the blocking IDE task and tests. The test task can also attach to an explicitly supplied local robot URL for debugging; it must fail promptly when no IDE is available. Use a configurable reserved port, serialize scenarios within an IDE, and prevent concurrent runs sharing sandbox/port paths.

Extend the existing launcher to accept the prepared project path (mirroring `runIde`'s `-PideProject`), test sandbox and port as Gradle properties rather than modifying normal `runIde` behavior. Start without a scene editor and without `abyssus.openView` auto-opening a GL view. Select the Abyssus project view and open its Properties window through UI actions. Disable consent/tips and native macOS dialogs where needed for local interaction tests.

Alternative: Starter/Driver handles more lifecycle concerns, but the user explicitly selected Remote Robot. An additional ide-launcher dependency duplicates the current Gradle launcher and is unnecessary for the first suite.

### 2. Disposable projects and observable assertions

Copy Untitled into a unique directory with a stable basename, excluding existing IDE workspace state. Restore source bytes between mutating scenarios and refresh the IDE through its normal project/VFS behavior; wait for the tree and panel to show restored state. Record source fixture hashes before/after the suite. Read identity/labels from the copied scene. Use `lib-core-editor`'s `SceneJson` (key order and number text preserved) and `AbyssusDocumentFormat` in test-side assertions; it is a plain JVM library, so the client pulls in no IntelliJ platform runtime. Copies must remain valid native documents (`format: "abyssus"`, `formatVersion: 1`). Compare expected byte replacements for preservation checks and parse JSON only for value checks. No remote script directly writes scene files or calls mutation actions to bypass input.

Drive tree expansion, selection, dialogs, field commits, and Undo with mouse/keyboard. Read-only remote queries may retrieve row bounds, component names, selection, document text, and loading state. Custom painted actions use row bounds plus the existing action geometry, not desktop pixel coordinates. Locate a fresh row/component after every refresh. Only add stable Swing component names to production containers if scoped class/text locators prove insufficient; these identifiers must not change visible behavior.

For unsaved edits, inspect the IDE document using a read-only query, then save through the IDE and verify disk bytes. Check the expected changed field, preservation of unrelated text, and byte-for-byte restoration after Undo. Keep screenshots independent of prior mutation history.

### 3. Initial coverage and capture inventory

| Area | Interaction checks | Baseline images |
|---|---|---|
| Tree | Select Abyssus; expand project, Scenes, Assets and ECS; toggle fog; Undo; switch views and return without duplicates | Expanded project/tree/footer; disabled fog state |
| Properties | Empty selection; select model, terrain, cube, procedural and HDR skybox; select entity 0 and Camera component of entity 4; edit entity name and Undo; reject a malformed camera number | `props-empty`, `props-asset-model`, `props-asset-terrain`, `props-asset-sky-cube`, `props-asset-sky-procedural`, `props-asset-sky-hdr`, entity, camera component, numeric validation error |
| Rename Scene | Open from the scene context menu; Cancel preserves bytes; rename changes only `name`; Undo restores bytes | Open input dialog |
| Skybox chooser | Open through Choose; list three skyboxes plus None; filter `hdr`; filter with no match; Cancel; Assign `skybox_default`; Undo | Unfiltered chooser, filtered HDR result, no-match state |

Use real fixture metadata, including its asset names and preview images. Choose a numeric Camera field actually present in its modeled editors; verify the error text and unchanged scene document. No scene viewport needs to open for these cases. Add/remove component workflows and terrain creation dialogs can follow in another change; they are not prerequisites for this first inventory.

### 4. Canonical screenshot environment

Use Ubuntu 24.04 x86_64 with Xvfb 1920x1080x24, IC 2025.2.4 and its bundled JBR, fixed dark IDE theme, UI scale 1, English locale, UTC timezone, and fixed IDE/tool-window/dialog dimensions. Pin the font package versions and record JBR, font, theme and scaling details in a visual-environment manifest next to baselines. Verify effective settings at runtime, including the IDE process timezone, and reject unsupported profiles before comparison or updating. Reuse the same environment for baseline generation and CI. Other hosts may run interaction checks and collect diagnostic captures with screenshot assertions explicitly disabled; they must not silently skip visual checks in the canonical job.

Capture component images from the live IDE using the official fixture screenshot API. Prefer component painting for the Swing-only regions to exclude desktop/window-manager decorations, and use the same capture mode for generation and comparison. Preserve fixed selection/focus, remove blinking text carets by focusing a stable non-editing control, and move the pointer out of the target before capture. Wait for indexing/tree population, preview completion and stable component layout, then require consecutive identical captures within a bounded wait. Do not treat a stable placeholder as a loaded thumbnail; verify the expected image state first. Fixed sleeps and retrying assertions until a regression disappears are not readiness mechanisms.

Alternative: independent baselines per OS multiply maintenance; whole-desktop baselines include unrelated IDE chrome and notifications. Neither is needed for the approved first scope.

### 5. Baseline comparison and review

Name baselines `<scenario>.png` (e.g. `tree-expanded`, `tree-fog-off`, `props-entity`, `props-camera-error`, `rename-scene-dialog`, `skybox-chooser`, `skybox-chooser-hdr`, `skybox-chooser-nomatch`); the design inventory above is the single list. Capture Swing components or dialogs only, never the 3D viewport. Store canonical images under `projects/plugin-abyssus/src/uiTest/resources/screenshots/linux/` with stable scenario names and the environment manifest. Use an exact decoded RGBA pixel comparison after readiness; dimension mismatch, missing/unreadable baseline, and unsupported environment fail explicitly. No global tolerance or broad masking in the first suite. Extract `ScreenshotComparator` as pure test logic using image data, with no Swing, GL, or IntelliJ dependencies; `ScreenshotArtifacts` handles PNG/report I/O. Unit tests use small synthetic images to prove identical images pass, pixel changes fail, dimensions fail, and diffs retain changed-pixel evidence.

Write actual images, expected images where available, highlighted diff PNGs, changed-pixel count/bounds and environment metadata under `build/reports/ui-tests/`. On any interaction failure also collect the screen, component hierarchy, IDE logs and test reports. An explicit local `-PupdateUiScreenshots=true` mode writes only selected scenario baselines in a matching canonical environment. Missing baselines fail during normal runs. Updates never run on CI; any accidental update request there fails. Do not overwrite an old baseline until the scenario's behavioral assertions and readiness checks pass. Include visual review of every generated baseline and diff in the implementation checklist.

### 6. Threads and boundaries

Client tests, fixture copying, polling, image comparisons and artifact writes run in the external test/runner process. Remote UI interactions and component capture use the robot's IDE/EDT mechanisms; read-only Swing queries explicitly run on the EDT where required. Disk I/O and image comparisons remain off the IDE EDT. The suite adds no libGDX calls; existing application GL code remains governed by `GdxRuntime.withContext` and safe on-screen canvases. Keep all helper code out of production sources of every module (`lib-core`, `lib-core-editor`, `lib-gdx`, `lib-physics`, `lib-raytracing`, `plugin-abyssus`); the new source set is test-only and adds no cycles or singletons to the checked modules (`checkPackageCycles`, `checkNoSingletons`).

### 7. CI integration and rollout

Replace the obsolete UI workflow matrix with a canonical Linux job using the Gradle wrapper, Java 21, pinned visual environment, bounded runner timeout and always-upload diagnostics. Run on manual dispatch and pull requests, independently of headless `check`. Make zero discovered UI tests an error. Retain ordinary test execution in the existing build workflow. Document complete-run and attach commands plus the canonical baseline update procedure in `AGENTS.md` and `docs/ai/testing.md` alongside the corresponding implementation groups.

First land the harness/interaction checks, then the comparator and reviewed baselines, and finally enable the CI job. Acceptance requires two clean consecutive canonical visual runs and evidence that a deliberately altered baseline fails and produces a useful diff. Rollback can disable the dedicated workflow or revert test tooling without changing plugin runtime behavior.

## Risks / Trade-offs

- Remote Robot maintenance and IC compatibility -> Verify and pin a working release before adding scenarios; do not silently switch frameworks. JetBrains recommends Starter/Driver for new work, but that migration is outside this request.
- Font/JBR updates change pixels -> Version the environment with baselines and regenerate through explicit reviewed updates; runner/font drift must be reported, not hidden with tolerance.
- Asynchronous thumbnails and tree refresh invalidate components -> Wait for content and reacquire fixtures after refresh with bounded timeouts and hierarchy diagnostics.
- Open changes alter appearance (`add-project-fps-counter` adds a Show FPS checkbox to the project row's properties, so the project panel is baselined only if captured after it lands; `add-remote-asset-library` may add tree/import UI) -> Baseline the current implemented UI and update baselines intentionally when those changes land.
- UI mutations can outlive a scenario -> Restore copies and IDE state between cases, assert source fixture hashes, and terminate only processes owned by the runner.

## References

- Official Remote Robot setup, fixtures and screenshots: https://github.com/JetBrains/intellij-ui-test-robot
- IntelliJ Platform custom run/test sandboxes: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-testing-extension.html
- JetBrains framework direction: https://platform.jetbrains.com/t/intellij-ui-test-robot-long-term-support/2139
