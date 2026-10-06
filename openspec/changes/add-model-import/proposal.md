# Proposal

## Why

Abyssus model assets are glTF. Most free and purchased 3D content comes as OBJ, FBX, 3DS, DAE or a glTF that was not
authored for Abyssus. Bringing such a model in
today means converting it in another tool and guessing its unit and up axis. FBX characters and props also lose their
skeleton and animations on the way. Abyssus already reads these formats through Assimp. What is missing is an import
that turns them into clean native assets, with a preview to check scale, orientation and animation before anything is
written.

## What Changes

- **New Import Model... action** on a recognised project's Assets node. It accepts `.obj`, `.fbx`, `.3ds`, `.dae`,
  `.gltf` and `.glb` files and writes one ordinary native MODEL asset. A `.blend` file is refused with a hint to export
  glTF from Blender.
- **glTF and GLB input is converted like the other formats**, not copied: it gets the same grounding, centring, size
  fit, preview and provenance. Its metallic-roughness materials pass through unchanged.
- **The dialog shows a live 3D preview of the converted model.**
  - The preview has a ground grid, a 1 m reference marker and an orbit camera.
  - A drop-down chooses which animation it plays.
  - A status line gives the size in metres, the number of animations and textures, and what was left out.
  - The preview shows the same converted data that Create writes.
- **Settings:**
  - a unique folder name, defaulting to `model_` plus the file name;
  - a source unit (m, cm, mm, in, ft), pre-filled from an FBX header's unit scale, otherwise metres;
  - an up axis (Y or Z), pre-filled from the file (FBX header; DAE `<asset>`; Z for 3DS; Y for OBJ, glTF and GLB);
    the DAE unit is pre-filled from its `<asset>` too, and glTF is metres by definition;
  - optional fit-to-size: scale so that the largest extent, or the height, is a chosen number of metres;
  - **Add to scene**, on by default when a scene view is selected: Create also adds the model to that scene, as Add
    Asset from the Scene view does (`Model <id>`, at the point the view orbits around), and selects it.
- **The converted model:**
  - It always stands on its lowest point at height 0 and is centred on X and Z.
  - It is written as one binary glTF (`model.glb`) that keeps the node hierarchy, meshes, materials and, for FBX,
    skins and every animation.
  - Textures (files next to the source, embedded in FBX or GLB, or glTF data URIs) become PNG files in the asset
    folder.
  - Phong-style materials are mapped to metallic-roughness. Specular colour is lost, and the dialog says so.
  - Cameras, lights, missing textures and non-triangle geometry are left out and listed, as are glTF morph targets and
    material extensions the renderer does not use.
- **The import is one undoable operation.** Undo removes the folder and, with Add to scene, the new entity; Redo
  restores the same bytes, `uuid` and entity. Without Add to scene, scenes are not touched, so the asset starts unused.
  The `.abss` file is never touched, and nothing is ever written next to the source file.
- **Provenance:** `source.json` records the source file name, its path (relative to the project folder when inside it,
  otherwise absolute) and SHA-256, its format, the detected and chosen unit and up axis, the size setting, and
  everything that was left out. The path is what a later re-import will use to find the source.
- **The FlightGear importer moves onto the new shared glTF writer.** Its models keep the same nodes, materials, frame and
  size, and its tests keep passing. The archived `flightgear-aircraft-import` requirements are unaffected.

Native fields:
- The import writes a new asset `meta.json`:
  - `format: "abyssus"`, integral `formatVersion: 1`, `version: 1`, `lastModified`, a fresh `uuid`, `type: "MODEL"`;
  - `additional.file: "model.glb"`, `format: "GLTF"`, `binary: true`, `materials: []`.
- The meta is validated as a native document before it is staged. The project's `.abss` is read only to validate it
  and find the `assets` folder.
- `source.json` is an asset side file that loaders ignore. Its `importer` is `"model"`.
- The placed entity is the same entity Add Asset writes (`scene-asset-placement`), written through `editSceneJson`.
- There is no format version change, and no new model format value. Imported assets are glTF, like every other model.

Out of scope:
- BLEND input: Assimp's Blender importer does not read current Blender files, and running Blender would need an
  external process. Users export glTF from Blender instead.
- Other source formats (STL, PLY) and importing from a URL or an archive.
- Copying a glTF or GLB source as is, without conversion.
- Re-importing into an existing asset, or updating an asset when its source changes. This change records `sourcePath`
  so that a later re-import change can find the source.
- Choosing which parts to keep. Removing nodes from a skinned FBX can break the skeleton.
- A forward-axis setting; a wrong facing is fixed with the rotate gizmo in the scene.
- Material editing, of a shared model asset and of one instance in a scene. It is planned as its own change.
- LODs, mesh simplification, collision shapes.
- Placing the model in a scene other than the selected scene view's, or several copies at once.
- Animation retargeting, trimming or renaming; animations are kept exactly as the source has them.

## Capabilities

### New Capabilities

- `model-import`: importing an OBJ, FBX, 3DS, DAE, glTF or GLB file into a project as a native glTF model asset,
  through a dialog with a live preview, unit, up-axis and size settings, with animations kept, provenance recorded, an
  optional placement in the open scene, and one undoable write.

### Modified Capabilities

None. Imported assets are ordinary glTF model assets, so `scene-model-rendering`, `scene-model-animation` and
`abyssus-project-assets` hold as written. Placement writes the entity that `scene-asset-placement` defines, so that
capability holds as written too. `flightgear-aircraft-import` keeps its requirements; only its implementation
moves onto the shared writer.

## Impact

- **`gdx-model`:**
  - a glTF 2.0 binary writer from its model data (nodes, meshes, metallic-roughness materials, skins, animations), using
    libGDX's JSON writer so the module gains no dependency;
  - unit and up-axis detection exposed from the existing scene normalizer, plus a small reader of a DAE file's
    `<asset>` unit and up axis, and an un-normalized load that also stops Assimp applying them.
- **`core`:**
  - a plain-JVM, constructor-wired `modelimport` package: source inspection, the unit, axis, fit, grounding and centring
    transform, texture gathering to PNG, and staging of `meta.json`, `model.glb`, textures and `source.json`;
  - `core.flightgear.GlbWriter` is replaced by the shared writer.
- **Plugin (`projectView`):** the Import Model action and its dialog, with a GL preview canvas that follows the scene
  view's canvas rules; `plugin.xml` and `AbyssusBundle.properties`. The write reuses `AssetFileCommand` and the
  reference guard that New Terrain and the FlightGear import use. Placement reuses `SceneComponentEdits.addAsset`, in
  the same command as the folder write.
- **Tests:** small OBJ, 3DS, DAE, GLB, animated glTF and animated FBX fixtures.
- **Docs:** `docs/ai/file-formats.md` (`source.json` of a model import), `gdx-model/README.md`, `core/README.md`,
  `src/main/kotlin/net/nevinsky/abyssus/projectView/README.md`, `README.md` (user section) and `CHANGELOG.md`.
- **No new runtime dependencies.** Assimp, libGDX and ImageIO are already present.
