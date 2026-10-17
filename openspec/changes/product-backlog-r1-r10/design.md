# Design

## Context

See proposal.md for why. Observed in the repository on 2026-10-13:

- `plugin.xml` registers asset-creation actions (`NewTerrain`, `NewFoliage`, `ImportModel`, `NewUiAsset`, ...) but none that
  creates a project or a scene.
- Asset creation already goes through `plugin/assetfiles` (`AssetTransactionEngine`, `AssetFileStore`), which writes several
  files as one transaction and undoes the writes already made when a later one fails. `NewTerrainAction` uses it.
- `editSceneJson` edits an existing document as one undoable command. It cannot create a file, so it is not the path for the
  new files; the existing multi-file transaction is (the AGENTS.md rule asks a plan needing another write path to say why).
- `Untitled.abss` shows the project shape: `format`, `formatVersion`, `name`, `activeSceneName`, `mainCamera`, `settings`.
  A procedural sky `meta.json` needs `type: "SKYBOX_PROCEDURAL"` and an `additional` block with the atmosphere constants.
- Open changes name modules and paths from before the split (counts of files in `openspec/changes` outside `archive`:
  `gradlew :core:` 5, `gradlew :raytracing:` 4, plain `gradlew :test` 8, `decouple-from-mundus` 8, `add-scene-shadows` 6,
  `add-cloud-scene-lighting` 4, the old `sceneview` README path 4).

## Goals / Non-Goals

**Goals:**
- A creator reaches a scene with a sky, a light and a camera from the IDE alone, without copying a folder.
- The R1–R10 work is ordered so that each gate can stop the rest (see Decisions 5).

**Non-Goals:**
- Project templates beyond one starter, a wizard in the IDE's New Project flow, project import, legacy migration.
- Export, packaging or building a game (R9 only records a decision).
- Starter models, terrain or foliage in the template. Creators add them with the existing actions.

## Decisions

1. **Starter content is a sky and a sun, not a model.** The starter project has one procedural sky asset (copied from the
   atmosphere constants the existing fixture and sky creation use) and a scene with a camera and a `Sun` light entity.
   A model needs either bundled binary assets or an import, which would enlarge the plugin and tie the template to one
   format. *Alternative:* ship a cube model; rejected for size and because it would be unused after the first edit.
   The product metric therefore becomes "time to first placed asset" (the creator runs **Import Model...** or **New
   Terrain**), not "time to first scene object".

2. **Pure builders in `lib-core-editor`, IDE glue in the plugin.** `ProjectTemplate` (builds the `.abss`, `.scene` and sky
   `meta.json` text), `NewNameValidator` (empty, separators, reserved characters, trailing dots/spaces, case-insensitive
   duplicates) and `SceneIdAllocator` (next unused numeric id) are plain classes with no IntelliJ, Swing or AWT imports and
   no singletons. They return text and decisions only, so headless tests cover them. Messages come through the injected
   `EditorMessages` and `AbyssusEditorBundle.properties`. The plugin owns the dialog, the actions, the VFS write and refresh,
   and the strings in `AbyssusBundle.properties`.

3. **Writes use the existing multi-file transaction.** All files of one action are written as one `AssetTransaction`, so a
   failure removes the files already written (spec: "Failed creation leaves no partial project"). *Alternative:*
   `editSceneJson` plus a plain create; rejected because it cannot create files and has no rollback. IDE Undo of a creation
   is not promised; the spec does not require it, and the existing asset-creation actions are the precedent.

4. **Threads.** Name validation runs on the EDT while the user types, on data already read (existing scene names are read
   before the dialog opens). The transaction runs under the same mechanism `NewTerrainAction` uses for VFS writes; no libGDX
   call is involved and nothing here touches `Gdx.*`. Action `update` stays on BGT and reads no state held on the action.

5. **One change, sequenced by gates.** Only R5 and R6 have new requirements. The rest are tasks that point at their own open
   changes, so no spec text is duplicated:

   ```
   R2 repair specs -> R1 triage/park -> R5 + R6 (this change's code)
                                    \-> R3 UI tests (add-plugin-ui-tests, groups 1-2)
                                    \-> R4 ray gate -> keep/narrow/park -> remove spike
                       R9 decision (needs R5 done to judge the loop)
                       R7, R8 (own changes) after R1
                       R10 stays parked until R5 and R9
   ```
   R2 goes first because every later task copies commands from those specs. If the R4 gate fails or no second GPU is
   available, R4 is recorded as "verified on Linux/NVIDIA and macOS Metal only" and ray tracing stays experimental; it does
   not block R5.

6. **Documentation claim.** Until R4 records more, user-facing text says ray tracing is verified on macOS Metal and
   Linux/NVIDIA Vulkan, and falls back to the normal renderer elsewhere.

## Risks / Trade-offs

- [Mixed scope in one change] -> Group by capability in tasks.md. Archive after the R5 and R6 groups even if the process
  groups finish later; record any group left open.
- [Starter sky meta drifts from what the sky loader accepts] -> A test loads the generated meta through the same validator
  and sky loader path the editor uses, not a copy of the shape.
- [Unique-id collisions between scenes] -> `SceneIdAllocator` reads every scene's id in the project, with a test for gaps and
  large ids.
- [Parking changes loses context] -> Parking means a `parked.md` note with the reason and the restart condition in that
  change's folder, not deletion.
- [R4 needs hardware the author may not have] -> Gate outcome "not verified" is allowed and recorded; claims shrink instead.
- [Manual first-run trials are small (3 sessions)] -> They are for finding blockers, not for estimating rates.

## Migration Plan

No data migration. New files only; existing projects are not modified. Rollback is removing the two actions and the empty-state
entries from `plugin.xml` and the view.

## Open Questions

- Which IntelliJ entry point suits **New Abyssus Project...** best (New group of the Project view, or File > New)? Both can
  call the same action; the choice can be settled during the runIde check without changing specs.
- Should the starter scene name come from a bundle string so it can be localized? The spec fixes `Main Scene`; localizing it
  later would change only the default, not the format.
