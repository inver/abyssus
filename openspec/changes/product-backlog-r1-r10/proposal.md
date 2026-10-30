# Proposal

## Why

A product review of the current state (2026-10-13) found that creators cannot start a project inside the editor, that 12
open changes (5 with no task done) hide what is actually finished, that open specs still name pre-split Gradle modules
and paths, and that ray tracing claims more platforms than were verified. This change turns that review's backlog
(R1–R10) into one ordered piece of work: one new user-facing capability, plus the triage, repair and verification work
that keeps the rest honest.

## What Changes

- **R5 New Project and New Scene.** Add **New Abyssus Project...** and **New Scene...** actions that create a valid native
  project (`.abss`, `assets/`, `scenes/`, one starter scene) and add scenes to an existing project, so no sample folder has
  to be copied by hand.
- **R6 Discoverability.** An Abyssus view with no project shows a **New Abyssus Project...** entry; the empty state of a
  project with no scene shows **New Scene...**. Further hints follow observed first-run sessions, not guesses.
- **R1 Work-in-progress cap.** Triage the 12 open changes and record a finish / schedule / park decision for each. Park
  `add-realistic-water` and `add-remote-asset-library` (R10), schedule `add-project-fps-counter`,
  `add-scene-view-antialiasing` and `redesign-game-ui-editor-layout` (R7, R8), and finish or archive the near-complete ones,
  ending with at most four open changes.
- **R2 Spec repair.** Replace stale Gradle module names, paths and cross-references in open changes (`:core:`,
  `:raytracing:`, plain `:test`, `src/main/kotlin/net/nevinsky/abyssus/sceneview/...`, `decouple-from-mundus`,
  `add-cloud-scene-lighting`, `add-scene-shadows`) with the current ones, and mark `docs/reviews/design-review-2026-10.md`
  as historical.
- **R3 UI tests.** Finish groups 1 and 2 of `add-plugin-ui-tests` (runner, fixtures, tree, properties, rename, skybox
  chooser). Screenshot baselines (its group 3) are deferred.
- **R4 Ray tracing gate.** Run the open manual checks of `add-scene-raytracing` on the available hardware, record the
  keep / narrow / park decision, then remove the feasibility spike (its task 1.10). Documentation names only verified
  platforms.
- **R7, R8.** After R1, implement `add-project-fps-counter`, `add-scene-view-antialiasing` and
  `redesign-game-ui-editor-layout` through their own changes. This change only sequences them.
- **R9 Export decision.** Record whether the editor builds and packages games or leaves that to the game's own Gradle
  project.
- **R10 Parked.** `add-realistic-water` and `add-remote-asset-library` stay parked until R5 and R9 are done.

Out of scope: building water, the remote asset library, screenshot baselines, collaboration, a marketplace, any
import or migration of unmarked legacy files, and any export or packaging feature (R9 only decides).

**Native files read or written.** New Project writes `<name>.abss`, a starter `scenes/*.scene` and a starter procedural-sky
asset `meta.json`, each with `format: "abyssus"` and integral `formatVersion: 1`. New Scene writes one `.scene` file. No
existing field, version or default changes, and existing files are never overwritten. Existing documents are validated with
`AbyssusDocumentFormat` before New Scene adds to their project.

## Capabilities

### New Capabilities
- `abyssus-project-creation`: creating a native project and adding a scene from the IDE, including the empty-state entry
  points, naming and overwrite rules, and the valid starter documents.

### Modified Capabilities

None yet. The existing `abyssus-project-view` requirement "These three actions are the only ones that write to an asset file"
is already out of date (other actions write asset files). This change does not make it more wrong, and fixing that wording
is part of task 1.3.

## Impact

- **Code.** `lib-core-editor` (pure starter-document builders and name validation), `plugin-abyssus` (`projectView/` actions,
  one dialog, bundle strings, empty-state text), and the existing `assetfiles` file-store path for creating files.
- **Docs.** `README.md` user section (keeping the `<!-- Plugin description -->` markers), `CHANGELOG.md`, `docs/ai/file-formats.md`
  (starter documents), `projects/plugin-abyssus/.../projectView/README.md`, and the open changes edited by R2.
- **Process.** `openspec/changes/*` are edited, parked or archived by R1, R2 and R10. No runtime behavior changes there.
- **Risks.** Mixed scope in one change: R5 is the only item with new spec requirements. The rest are tracked as tasks so the
  change can be applied in order and stopped at a gate (see design.md).
