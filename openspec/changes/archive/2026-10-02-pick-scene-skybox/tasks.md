# Tasks

## 1. Skybox row label

- [x] 1.1 Add a display-name map (`skyboxName` → `skybox`) in `DtoTree.kt` and use it in `DtoEntryNode.update` when no
  explicit `label` is set; keep `DtoEntry.name` as `skyboxName`. Verify `AbyssusViewTest.testNodeTree` passes after
  updating its expectations to `skybox: null` (top-level list and the `startsWith` lookup)
- [x] 1.2 Verify toggling the skybox eye still works and keeps the `skyboxName` key: add a `SceneEditFormattingTest`
  case toggling `skyboxEnabled` on a `skyboxName` entry and assert the file has no `"skybox"` key

## 2. Writing the skybox

- [x] 2.1 Add `setSkybox(project, file, name: String?)` in `EnabledToggle.kt` on top of `editJson`, returning false
  without writing when the value is unchanged. Verify with `SceneEditFormattingTest` cases: null → `skybox_default`
  (pretty file stays pretty, other members unchanged, `skyboxEnabled` untouched), `skybox_default` → null, and same value
  leaves the file's modification stamp unchanged
- [x] 2.2 Add `SkyboxChoice(name, faces, formats, sceneCount, unused)` and
  `skyboxChoices(project: ProjectDto, metas: Map<String, JsonNode?>)` (only `SKYBOX`, sorted by folder; faces/formats
  from `additional`'s six face keys; `sceneCount` from `ProjectAssets.sceneReferences`; `unused` from `AssetInfo`).
  Verify with unit tests: mixed asset types including `SKYBOX_HDR` are excluded; four `.png` + two `.jpg` faces give
  `6 faces · jpg, png`; a missing face and a missing `meta.json` give fewer/zero faces without failing; two scenes naming
  a skybox give `sceneCount == 2`; and the `Untitled` fixture yields one `skybox_default` choice with
  `6 faces · png` and `unused == true`

## 3. Chooser dialog

- [x] 3.1 Add `SkyboxPickerModel` (no Swing): `entries` (None first, then case-insensitive name matches),
  `foundCount`, `noMatch`, `selected` (preselected current skybox, or None when null or unlisted, kept when filtered
  out) and `footerText` ("Selected: <name>" / "Selected: none"). Verify with unit tests for each scenario of the
  "Skybox chooser dialog", "Filtering the skybox list" and "Skybox selection and footer" requirements (`NIGHT` matches
  only `abyss-night`, "1 found"; no match leaves only None, "0 found")
- [ ] 3.2 Add `SkyboxChooserDialog` (`DialogWrapper`, title "Choose a skybox") over the model, laid out after the
  design's "Choose asset for scene" board: "N found" header, "Filter by name" `SearchTextField`, `JBList` with a
  two-line renderer (icon, name, detail line, "unused" badge or "used by N scene(s)"), no-match message, "Selected: …"
  footer, Cancel and Assign (renamed OK; double-click also assigns); exposes `chosen: String?`. Add bundle messages for
  the title, "N found", filter label, "None", "clear the field", "unused", "used by N scene(s)", the no-match message,
  the footer, "Assign" and the `...` tooltip. Verify by opening the dialog in `runIde` (task 4.2) and comparing it
  against the design board

## 4. Row icons

- [ ] 4.1 Refactor `EyeTree` from `actionFor` to `actionsFor(row): List<RowAction>` laid out right-to-left (eye
  rightmost), with painting, hover cursor, tooltip and click hit-testing per icon. Verify the eye and "View" icons still
  behave as before by running the plugin (`./gradlew runIde`) and clicking the fog eye and a scene's View icon
- [ ] 4.2 Add the `...` action (`AllIcons.Actions.More`) to a scene's top-level `skyboxName` row when its source scene
  belongs to a project; on click build the choices from `AssetReadCache.of(project).read(abss)` and the `SKYBOX` folders'
  `meta.json`, open the dialog, call `setSkybox` on Assign, then `reselect` the row. Verify in `runIde` against the
  `Untitled` project: row shows `...` left of the eye; the dialog reads "1 found" and lists None and `skybox_default`
  (`6 faces · png`, "unused" badge); typing `xyz` in the filter shows the no-match message; Cancel writes nothing;
  assigning `skybox_default` updates the row to `skybox: skybox_default` and removes the asset's unused mark in the
  tree; Undo restores `null`; and a standalone `.scene` shows no `...`

## 5. Spec reconciliation

- [x] 5.1 Amend `openspec/changes/add-abyssus-project-view/specs/abyssus-project-view/spec.md`: list the skybox chooser
  as a third writer in "Asset node structure" and rename the `skyboxName` node to `skybox` in "Null values are shown".
  Verify `openspec validate add-abyssus-project-view` and `openspec validate pick-scene-skybox` pass
- [x] 5.2 Run the full test suite (`./gradlew test`) and verify it passes
