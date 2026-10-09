# Tasks

## 1. Detection and the glTF writer in `gdx-model` (headless)

- [x] 1.1 `AssimpModelDataLoader` gains `normalize: Boolean = true`. `SceneNormalizer` gains `stated(scene)` (FBX unit
  in metres and up axis, or null). Runtime loads keep their behaviour. Verify, in `:gdx-model:test`:
  - `AssimpLoadingTest.parsesGltfIntoModelData` still passes;
  - new `SceneNormalizerTest` cases: FBX metadata with `UpAxis` 2 and `UnitScaleFactor` 1 gives Z and 0.01 m; no
    metadata gives nulls; `normalize = false` leaves the root transform as identity.
- [x] 1.1a `ColladaAsset.read(file)` (StAX, `core.assimp`) reads `asset/unit@meter` and `asset/up_axis`. With
  `normalize = false`, a DAE load also sets `IMPORT_COLLADA_IGNORE_UNIT_SIZE` (by its string name if lwjgl-assimp has
  no constant) and `IMPORT_COLLADA_IGNORE_UP_DIRECTION`. Verify: `ColladaAssetTest` (0.01 and Z for a cm, Z-up file;
  nulls without `<asset>`; `X_UP` reported as stated but unsupported), and a `normalize = false` load of a 100-unit
  Z-up cm DAE gives an identity root and 100-unit extents.
- [x] 1.2 `PhongToPbr`: diffuse to base colour, metallic 0, shininess to roughness (0.8 without it), opacity below 1 to
  `BLEND`, specular reported as dropped. Verify: `PhongToPbrTest` covers each mapping and the default roughness.
- [x] 1.3 `GltfWriter` (`net.nevinsky.abyssus.lib.gdx.gltf` in `gdx-model`, libGDX `JsonWriter`, no new dependency): nodes with TRS
  and hierarchy, the mesh attributes as accessors, 16- or 32-bit indices, skins with inverse bind matrices, LINEAR
  animations, PBR materials, external image URIs, deterministic bytes, and its own validation. Verify:
  `GltfWriterTest`:
  - the GLB header and chunk lengths are correct;
  - the same input gives the same bytes twice;
  - a 90,000-vertex mesh is written with 32-bit indices;
  - a static model and the `animated.gltf` resource survive a write and re-read through `AssimpModelDataLoader` with
    the same node tree, vertex counts, material colours, animation names and keyframe counts;
  - weights that don't sum to 1 are rejected.
- [x] 1.4 Document the writer, `normalize` and `stated` in `gdx-model/README.md`. Verify: `scripts/check-docs.sh`
  passes.

## 2. Import pipeline in `core.modelimport` (headless)

- [x] 2.1 Add the fixtures under `core/src/test/resources/modelimport/`: `crate.obj` / `crate.mtl` / `wood.png`,
  `crate_missing.obj`, `box.3ds`, `rig.fbx` (two bones, `Idle` and `Run`, one embedded texture, cm, Z up),
  hand-written `crate.dae` (cm, Z up) and `crate_xup.dae`, `crate.glb` (metallic 1, roughness 0.3, texture in the BIN
  chunk), a copy of `gdx-model`'s `animated.gltf`, and hand-written `morph.gltf` (one morph target,
  `KHR_texture_transform`). Generate the binary ones with `MakeImportFixtures`, which runs only with `-Dabyssus.makeFixtures=true`, and pin their SHA-256s.
  If Assimp's FBX export loses the skin or animations, use a small CC0 FBX instead and note its licence beside it.
  Verify: `./gradlew :core:test --tests '*MakeImportFixtures*' -Dabyssus.makeFixtures=true` writes them, and
  `ModelSourceTest.fixturesArePinned` passes.
- [x] 2.2 `ModelSource`: open with `normalize = false`, put embedded textures in a temp folder deleted on `close()`, and
  return the format, stated frame, animation names and durations, SHA-256, and what was left out. Verify:
  `ModelSourceTest`:
  - `rig.fbx` reports cm, Z and `Idle` / `Run`;
  - `crate.obj` reports nulls;
  - `box.3ds` is reported as Z up;
  - `crate.dae` reports cm and Z, and `crate_xup.dae` reports X up as unsupported;
  - `crate.glb` and `animated.gltf` report m and Y as defined by the format; `animated.gltf` lists its animations;
  - `morph.gltf` lists the morph target and `KHR_texture_transform` as left out;
  - `crate_missing.obj` lists `wood.png` as missing;
  - the fixture folder's file list is unchanged after open and close;
  - a truncated FBX throws an import error with a reason.
- [x] 2.3 `ImportSettings`: folder name rules (valid, unique among `assets/*`, default `model_<stem>`), unit, up axis,
  and fit (`None`, largest extent or height, each greater than 0). Verify: `ImportSettingsTest` covers `tree` being
  taken in a copy of Untitled's asset names, a size of 0, a name with `/`, and the default for `My Crate.obj`.
- [x] 2.4 `ImportTransform`: up axis, then unit, then rest-pose bounds, then fit, then ground and centre, as an
  `import_root` node wrapping the source roots. Verify: `ImportTransformTest`:
  - the 100-unit crate in cm is 1 m with min y = 0 and centred on X/Z;
  - Z up turns +Z into +Y;
  - a fit to a 1.8 m height gives a 1.8 m height;
  - `rig.fbx`'s posed vertex positions at t = 0 and mid-`Run` match the source's after the transform, up to the
    expected scale and offset.
- [x] 2.5 `TextureGather`: ImageIO decode, PNG re-encode into `textures/`, dedupe by content, rename map, unsupported
  or missing images reported and their slot cleared. Verify: `TextureGatherTest` (two materials using `wood.png` give
  one file; a `.tga` is reported unsupported; the FBX embedded texture becomes a PNG).
- [x] 2.6 `ModelImport.stage`: the file map and the report.
  - `meta.json` has native markers first, a fresh `uuid`, `type: "MODEL"` and `additional` with `file`, `format`,
    `binary` and `materials`, validated by `AbyssusDocumentFormat`.
  - `source.json` follows the design's shape.

  Verify: `ModelImportTest`:
  - `crate.obj` stages `meta.json`, `model.glb`, `source.json` and `textures/wood.png`;
  - the meta passes `AbyssusDocumentFormat`;
  - the GLB reloads through `AssimpModelLoader.loadData` with its texture;
  - `rig.fbx`'s GLB still has both animations with their durations and two joints;
  - `crate.glb`'s GLB keeps metallic 1 and roughness 0.3, its texture becomes `textures/*.png`, and nothing is listed
    as approximated;
  - `crate.dae` imported with its stated cm and Z is 1 m on each side, upright;
  - `source.json` lists the stated and chosen frame and the skipped items;
  - `sourcePath` is `sources/crate.obj` for a source under the project folder, and absolute for one outside it.
- [x] 2.7 Document `core.modelimport` in `core/README.md`, and the model import's `source.json` in
  `docs/ai/file-formats.md`. Verify: `scripts/check-docs.sh` passes.
- [x] 2.8 Confirm `core` stays singleton-free. Verify: `./gradlew :core:checkNoSingletons :core:test`.

## 3. FlightGear on the shared writer

- [x] 3.1 Update every doc that names `core.flightgear.GlbWriter` (`core/README.md`, `docs/ai/`) to name `gdx-model`'s
  `GltfWriter`, and confirm `openspec/specs/flightgear-aircraft-import/spec.md` names no writer. Verify:
  `grep -rn GlbWriter docs core/README.md openspec/specs` finds nothing, and `scripts/check-docs.sh` passes.
- [x] 3.2 `FlightGearImport` builds `ModelData` and calls `GltfWriter`. Delete `core.flightgear.GlbWriter`, its `Gltf*`
  types and `GlbWriterTest`, moving that test's still-relevant checks into `GltfWriterTest`. Verify:
  `./gradlew :core:test --tests 'net.nevinsky.abyssus.lib.gdx.flightgear.*'` passes unchanged, apart from the removed
  `GlbWriterTest`.
- [x] 3.3 Re-import the Control Line Trainer with the new writer. Verify: `./gradlew :games:control-line:importTrainer`
  then `./gradlew :games:control-line:test` pass (`FlightTest`, `BundledProjectTest.parkedPlanesRestOnTheGround`).

## 4. Plugin: action, form and write (headless)

- [x] 4.1 Move `projectRefusal` into a helper used by both import actions. Verify: `ImportFlightGearTest` still passes.
- [x] 4.2 `ModelImportForm` (Swing-free): settings, values read from the file versus defaults, validation messages,
  the animation list, the left-out and approximated lists, and the Create-enabled state. Verify: `ModelImportFormTest`
  (a taken `tree` disables Create; FBX values are marked as read from the file; an unreadable source disables Create
  and shows its reason).
- [x] 4.3 `ImportModelAction` on the Assets node, registered in `plugin.xml` beside `Abyssus.ImportFlightGear`, with
  its texts in `AbyssusBundle.properties`. The chooser accepts `.obj`, `.fbx`, `.3ds`, `.dae`, `.gltf` and `.glb`, and
  a `.blend` is refused with the export-to-glTF hint. Create stages on the pool and writes with `AssetFileCommand` and
  `AssetReferenceGuard`, then selects the asset. Verify: `ImportModelTest` on a copy of Untitled:
  - `crate.obj` creates `assets/model_crate`, listed as an unused MODEL, and the scenes and `.abss` are unchanged;
  - Undo removes it, and Redo restores identical bytes and `uuid`;
  - Undo is refused once `Main Scene.scene` names `model_crate`;
  - a non-native `.abss` is refused before the dialog opens;
  - a `.blend` is refused with the hint, and nothing is written.
- [x] 4.3a Placement: `ModelImportForm` holds the target scene or the reason it is disabled (no scene view, playing,
  unreadable). `AssetFileCommand.execute` can hand back its undo action instead of registering it. Create runs one
  outer command: it checks the scene, writes the folder, calls `SceneComponentEdits.addAsset` at the view's orbit
  target, registers the undo action only when both succeed, otherwise reverses the folder and records nothing. Then it
  selects the new entity. Verify: `ModelImportFormTest` covers the three disabled reasons. `ImportModelTest` on a copy
  of Untitled covers:
  - placement into `Main Scene.scene` at `(10, 0, -4)` adds entity `9`, `Model 9`, with asset `MODEL` `model_crate`;
  - one Undo removes the entity and the folder, and one Redo restores both with identical bytes and `uuid`;
  - a scene made unreadable before Create leaves no folder, no scene change, and no undo step;
  - Undo stays refused when `model_crate` was also placed by a separate Add Asset.
- [x] 4.4 Document the action, the form and the preview in `src/main/kotlin/net/nevinsky/abyssus/projectView/README.md`,
  the user section of `README.md` (formats, the Blender hint, Add to scene), and `CHANGELOG.md`. Verify: `scripts/check-docs.sh` passes, and
  `./gradlew patchPluginXml` succeeds.

## 5. Plugin: dialog and live preview

- [x] 5.1 `ImportModelDialog` binds `ModelImportForm`, including the Add to scene checkbox naming the scene, or
  disabled with its reason. It opens the source on the pool with cancellable progress and
  recomputes only the transform on each settings change. Verify: `ModelImportFormTest.settingsChangeReusesSource`
  (the source is opened once over three setting changes).
- [x] 5.2 `ModelPreviewCanvas`: a `GuardedGLCanvas` with its own `GdxRuntime` context. It draws the model, grid, 1 m
  post and orbit camera framed on the bounds; plays the chosen animation in a loop; uploads one texture per frame; and
  rebuilds the model on a new transform. GL is released in `doOKAction`, `doCancelAction` and `dispose` before the
  window hides, and a failed context is shown as text. The framing math stays in a Swing-free `PreviewFraming`.
  Verify: `PreviewFramingTest` (a 1 m crate and a 100 m crate are both framed fully), and the GL checks in group 6.

## 6. Manual checks in runIde (on a copy of Untitled, never the fixture itself)

- [ ] 6.1 runIde check 1: Assets → Import Model... → `crate.obj` with unit cm. Expected: the preview shows a 1 m crate
  beside the 1 m post; the status line reads 1.00 × 1.00 × 1.00 m; Create adds `model_crate` as unused; dragging it
  into `Main Scene.scene` shows the textured crate on the ground.
- [ ] 6.2 runIde check 2: import `rig.fbx` (or a real FBX character). Expected: the unit and up axis are pre-filled and
  marked as read from the file; `Idle` plays; choosing `Run` switches the animation; after Create and placing it in a
  scene, it animates in the scene view.
- [ ] 6.3 runIde check 3: with `Main Scene.scene` open, open the import dialog, resize it, switch the unit several
  times, then Cancel. Repeat with Create and with Esc. Expected: the scene view keeps rendering, there are no GL
  errors in `idea.log`, and on macOS the JVM does not abort.
- [ ] 6.4 runIde check 4: Undo the import from check 1 while the crate is not placed (the folder is removed), then
  Redo (the folder returns). Place it, then try Undo again. Expected: the undo is refused, naming the scene.

- [ ] 6.5 runIde check 5: import a real DAE (exported from SketchUp or Blender) and a real GLB (for example a Khronos
  sample model). Expected: the DAE's unit and up axis are pre-filled from the file and the model stands upright at the
  right size; the GLB's materials look the same as in a glTF viewer, and anything left out is listed.
- [ ] 6.6 runIde check 6: with `Main Scene.scene` open and orbiting a point away from the origin, import `crate.obj`
  with Add to scene on. Expected: the crate appears at the orbit point, its entity is selected, and one Undo removes
  both the crate and `model_crate`, and Redo brings both back. Open the dialog while the scene plays: the option is
  disabled.

## 7. Integration

- [x] 7.1 Run the full checks. Verify: `./gradlew check` and `scripts/check-docs.sh` both pass; report any failure
  outside this change rather than fixing it silently.
