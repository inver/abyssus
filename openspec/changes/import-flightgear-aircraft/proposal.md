# Proposal

## Why

FlightGear's aircraft archives are a large, freely redistributable source of complete aircraft models, but Abyssus can
only use glTF models. Getting one into a project today means hand-converting AC3D geometry and SGI textures and
guessing its axes and scale. The Control Line game's Trainer is a generated box model. A recognisable Cessna 172
(FlightGear `c172r`, https://mirrors.ibiblio.org/flightgear/ftp/Aircraft-2024/c172r.zip) would make it a real-looking
trainer and prove the importer on a real archive.

## What Changes

- New **Import FlightGear Aircraft...** on a recognised project's Assets node. It reads a FlightGear aircraft `.zip` and
  writes one ordinary native MODEL asset:
  - The user picks the aircraft (an archive may hold several `*-set.xml`), a unique folder name and a size: the original
    metres, or scaled to a chosen wingspan. They also pick which named parts to keep.
  - The model XML's AC3D geometry, its nested sub-models and its offsets become one GLB. It keeps the part names,
    uses the Abyssus plane frame (nose +Z, up +Y, left wing +X) and stands on its lowest point.
  - Textures (SGI `.rgb/.rgba/.sgi/.bw`, PNG, JPEG) become PNG files beside it.
  - `select` animations are resolved to the aircraft's state at rest (every property 0), so a static propeller is kept
    and its spinning disc dropped.
  - Instrument panels, effects, sounds, flight dynamics, liveries and unsupported texture formats are left out and
    listed in the dialog.
- The import is one undoable operation. Undo removes the new asset folder and Redo restores the same bytes. Scenes and
  the project file are not touched, so the asset starts unused.
- The asset records its origin in `source.json`: archive name and SHA-256, aircraft, model path, authors from the set
  file, the licence found in the archive, and the conversion settings. Any licence file in the archive is copied in.
  When the archive has no licence, the dialog says so before Create, and the asset records the licence as unknown.
- **Control Line:** the Trainer's model is replaced by the imported Cessna 172, scaled to the Trainer's 1.0 m span.
  It keeps the cabin exterior and drops the interior parts and the propeller disc.
  - The `model_trainer` asset folder keeps its name and identity.
  - The Trainer's flight settings, line length, leadouts, mass and plane name are unchanged.
  - Its parked pose in `Field.scene` changes from tail-down to level, because the Cessna has a nose wheel.
  - The archive has no licence file. FlightGear aircraft are distributed under GPL-2.0-or-later, so the asset ships
    that notice (`COPYING`) with credit to David Megginson (3D model). The user approved bundling it as a separately
    licensed asset.
  - `tools/PlaneModels.kt` stops generating the trainer, and a Gradle task re-imports it from the checksummed archive.

Native fields:
- The import writes a new asset `meta.json`: `format: "abyssus"`, integral `formatVersion: 1`, a fresh `uuid`, `type:
  "MODEL"`, and `additional.file` / `format: "GLTF"` / `binary: true` / `materials: []`. It validates the project file
  first.
- The Trainer re-import replaces only `model.glb`, `textures/`, `source.json` and `COPYING` in `model_trainer`, plus
  `additional.file` in its meta through `editSceneJson`. The `uuid` and other fields stay.
- The Trainer's `PositionComponent.localPosition.y` and `localRotation` in `Field.scene` change through
  `editSceneJson`. Unrelated keys and number text are preserved.

There is no format version change. This is an asset importer that produces native assets. It is not an importer or
migration of foreign Abyssus documents, which the native-format contract still rules out.

Out of scope:
- Animating the imported parts: propeller spin, control surfaces, gear.
- FlightGear flight models (JSBSim/YASim), sounds, 2D/3D panels, Nasal, effects and shaders, liveries, LOD ranges.
- Model formats other than AC3D, and DDS textures.
- Importing from a folder or URL instead of a `.zip`.
- A model preview in the dialog.
- Changing the Trainer's flight behaviour or name.

## Capabilities

### New Capabilities

- `flightgear-aircraft-import`: importing a FlightGear aircraft archive into a project as a native model asset (parts,
  frame, scale, textures, provenance), as one undoable operation.

### Modified Capabilities

None. The Trainer's new model is bundled game content. The Control Line flight, game-flow and scoring requirements
still hold as written, and their tests verify them.

## Impact

- `core`: a plain-JVM FlightGear package (archive, model XML, AC3D reader, SGI decoder, GLB writer, import staging),
  wired by constructors.
- Plugin: the Import action and its dialog under `projectView`, `plugin.xml`, `AbyssusBundle.properties`. It reuses the
  undoable asset-file command New Terrain uses.
- Control Line: `model_trainer`, `Field.scene` (Trainer pose), `tools/PlaneModels.kt`, a new import task in
  `games/control-line/build.gradle.kts`, the README asset licences.
- Docs: `docs/ai/file-formats.md` (`source.json` of imported assets), `src/main/kotlin/net/nevinsky/abyssus/projectView/README.md`, `core/README.md`.
- No new runtime dependencies: zip, XML, ImageIO and PNG come from the JDK.
