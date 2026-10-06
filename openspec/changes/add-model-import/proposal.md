# Proposal

## Why

Abyssus model assets are glTF. Most free and purchased 3D content comes as OBJ, FBX or 3DS. Bringing such a model in
today means converting it in another tool and guessing its unit and up axis. FBX characters and props also lose their
skeleton and animations on the way. Abyssus already reads these formats through Assimp. What is missing is an import
that turns them into clean native assets, with a preview to check scale, orientation and animation before anything is
written.

## What Changes

- **New Import Model... action** on a recognised project's Assets node. It accepts `.obj`, `.fbx` and `.3ds` files and
  writes one ordinary native MODEL asset.
- **The dialog shows a live 3D preview of the converted model.**
  - The preview has a ground grid, a 1 m reference marker and an orbit camera.
  - A drop-down chooses which animation it plays.
  - A status line gives the size in metres, the number of animations and textures, and what was left out.
  - The preview shows the same converted data that Create writes.
- **Settings:**
  - a unique folder name, defaulting to `model_` plus the file name;
  - a source unit (m, cm, mm, in, ft), pre-filled from an FBX header's unit scale, otherwise metres;
  - an up axis (Y or Z), pre-filled from the file (FBX header; Z for 3DS; Y for OBJ);
  - optional fit-to-size: scale so that the largest extent, or the height, is a chosen number of metres.
- **The converted model:**
  - It always stands on its lowest point at height 0 and is centred on X and Z.
  - It is written as one binary glTF (`model.glb`) that keeps the node hierarchy, meshes, materials and, for FBX,
    skins and every animation.
  - Textures (files next to the source, or embedded in FBX) become PNG files in the asset folder.
  - Phong-style materials are mapped to metallic-roughness. Specular colour is lost, and the dialog says so.
  - Cameras, lights, missing textures and non-triangle geometry are left out and listed.
- **The import is one undoable operation.** Undo removes the folder, and Redo restores the same bytes and `uuid`. Scenes
  and the `.abss` file are not touched, so the asset starts unused. Nothing is ever written next to the source file.
- **Provenance:** `source.json` records the source file name and SHA-256, its format, the detected and chosen unit and
  up axis, the size setting, and everything that was left out.
- **The FlightGear importer moves onto the new shared glTF writer.** Its models keep the same nodes, materials, frame and
  size, and its tests keep passing. This amends a task of the open `import-flightgear-aircraft` change.

Native fields:
- The import writes a new asset `meta.json`:
  - `format: "abyssus"`, integral `formatVersion: 1`, `version: 1`, `lastModified`, a fresh `uuid`, `type: "MODEL"`;
  - `additional.file: "model.glb"`, `format: "GLTF"`, `binary: true`, `materials: []`.
- The meta is validated as a native document before it is staged. The project's `.abss` is read only to validate it
  and find the `assets` folder.
- `source.json` is an asset side file that loaders ignore. Its `importer` is `"model"`.
- There is no format version change, and no new model format value. Imported assets are glTF, like every other model.

Out of scope:
- Other source formats (DAE, BLEND, glTF/GLB input, STL, PLY) and importing from a URL or an archive.
- Re-importing into an existing asset, or updating an asset when its source changes.
- Choosing which parts to keep. Removing nodes from a skinned FBX can break the skeleton.
- A forward-axis setting; a wrong facing is fixed with the rotate gizmo in the scene.
- Material editing, LODs, mesh simplification, collision shapes.
- Placing the imported model in a scene automatically.
- Animation retargeting, trimming or renaming; animations are kept exactly as the source has them.

## Capabilities

### New Capabilities

- `model-import`: importing an OBJ, FBX or 3DS file into a project as a native glTF model asset, through a dialog with
  a live preview, unit, up-axis and size settings, with animations kept, provenance recorded and one undoable write.

### Modified Capabilities

None. Imported assets are ordinary glTF model assets, so `scene-model-rendering`, `scene-model-animation` and
`abyssus-project-assets` hold as written. The open `import-flightgear-aircraft` change keeps its requirements; only its
implementation moves onto the shared writer.

## Impact

- **`gdx-model`:**
  - a glTF 2.0 binary writer from its model data (nodes, meshes, metallic-roughness materials, skins, animations), using
    libGDX's JSON writer so the module gains no dependency;
  - unit and up-axis detection exposed from the existing scene normalizer.
- **`core`:**
  - a plain-JVM, constructor-wired `modelimport` package: source inspection, the unit, axis, fit, grounding and centring
    transform, texture gathering to PNG, and staging of `meta.json`, `model.glb`, textures and `source.json`;
  - `core.flightgear.GlbWriter` is replaced by the shared writer.
- **Plugin (`projectView`):** the Import Model action and its dialog, with a GL preview canvas that follows the scene
  view's canvas rules; `plugin.xml` and `AbyssusBundle.properties`. The write reuses `AssetFileCommand` and the
  reference guard that New Terrain and the FlightGear import use.
- **Tests:** small OBJ, 3DS and animated FBX fixtures.
- **Docs:** `docs/ai/file-formats.md` (`source.json` of a model import), `gdx-model/README.md`, `core/README.md`,
  `src/main/kotlin/net/nevinsky/abyssus/projectView/README.md`, `README.md` (user section) and `CHANGELOG.md`.
- **No new runtime dependencies.** Assimp, libGDX and ImageIO are already present.
