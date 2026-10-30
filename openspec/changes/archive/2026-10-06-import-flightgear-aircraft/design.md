# Design

## Context

See proposal.md for motivation. The facts below were checked against `c172r.zip` (SHA-256
`592fe840e6ab9f69619fe25045bd3b766a9931c1d36965b3b255cbabe6162b2e`, 102 KB):

- `c172r-set.xml` names `Aircraft/c172r/Models/c172-dpm.xml` and authors, but no `license` entry. The archive has no
  licence file.
- The model XML has one AC3D `<path>`, a `<panel>` (skipped), `range` LODs, and `select` animations: `Propeller` when
  rpm < 500, `Propeller.2` (a blurred disc) when rpm > 300. It also has translate/rotate animations, which are not
  imported.
- `c172-dpm.ac` has 33 named `poly` objects with 862 vertices and 797 surfaces. Its textures `c172-01.rgb` and
  `c172-02.rgb` are referenced by absolute paths (`/home/david/src/blender/...`) and are SGI RLE images.
- AC3D axes: nose toward -X (propeller hub at x ≈ -1.96), up +Y, right wing toward -Z. The span is 10.54 m and the
  length 8.18 m.

Abyssus loads models through Assimp, but `ModelMeta.Format` only knows `GLTF`. New Terrain already stages files and
writes them as one undoable `AssetFileCommand` (`assetfiles`). The Control Line Trainer is a generated 1.0 m-span box
model (`tools/PlaneModels.kt`) with nose +Z, up +Y, left wing +X. It parks tail-down
(`localRotation` ≈ 13° nose-up) on a convex-hull collider built from its model.

## Goals / Non-Goals

**Goals:** a static, faithful conversion into the existing GLTF model path. Logic stays in plain JVM code that tests
cover headless. The import is one undoable command. The Trainer is reproducible from the checksummed archive.

**Non-Goals:** a general AC3D→Abyssus format (no new `ModelMeta.Format`), and runtime FlightGear support. Out-of-scope
features are listed in the proposal.

## Decisions

### Convert to GLB instead of loading `.ac`

The importer writes `model.glb` (glTF 2.0 binary) with external `textures/*.png`, as the airfield models already do. The
alternative, keeping `.ac` and adding an `AC3D` format to `ModelMeta`, would widen the native format contract and leave
the SGI textures, which nothing in the stack decodes. Baking the frame, the scale and the offsets also keeps the scenes
free of import-specific transforms.

### Plain JVM pieces in `core` (`net.nevinsky.abyssus.lib.gdx.flightgear`)

All are constructor-wired, with no `object`, so `checkNoSingletons` holds. All are free of Swing, GL and the platform,
and are tested headless.

- `FlightGearArchive`: lists the archive's aircraft (`*-set.xml` with `sim/model/path`). It resolves `Aircraft/<dir>/`
  paths inside the archive, and rejects `..` and absolute entries (zip-slip). It reads entries lazily. Size limits: no
  entry over 64 MB, at most 4096 entries, and nested models at most 8 levels deep.
- `FlightGearModelXml`: parses with the JDK's DOM, with DTDs and external entities disabled. It reads the model path,
  `offsets` (x/y/z-m, pitch/roll/heading-deg), nested `<model>` entries with their offsets, and `select` animations with
  their `condition`.
- `RestState`: evaluates `condition` trees (`and`, `or`, `not`, `equals`, `not-equals`, `less-than`,
  `less-than-equals`, `greater-than`, `greater-than-equals`, `property` and `value` operands) with every property 0. An
  unknown element counts as "shown", so nothing disappears by surprise. It decides the default ticks.
- `Ac3dReader`: parses AC3D text (`AC3Db*`): MATERIAL lines, nested OBJECTs with `name`, `loc`, `rot`, `texture`,
  `texrep`/`texoff`, `crease`, `numvert`, and `numsurf` with `SURF` flags, `mat` and `refs` with UVs. It keeps only
  polygon surfaces (type 0): closed and open lines are skipped and counted. Polygons are triangulated as fans, which is
  enough for AC3D's convex faces; a later concave-polygon fix would not change the specs.
- `SgiImage`: decodes SGI images (magic 474, verbatim and RLE, BPC 1, 1-4 channels, bottom-up rows) into ARGB, and
  writes PNG through `javax.imageio`.
- `GlbWriter`: writes glTF 2.0 JSON with one node per kept part, named after the AC object, holding its mesh. Each mesh
  has positions, normals and UVs, with one primitive per material and texture pair. It writes materials (AC diffuse as
  `baseColorFactor`, the texture as `baseColorTexture`, `doubleSided` from the SURF two-sided flag), external image
  URIs, and one BIN chunk.
- Normals: smooth within the AC object's `crease` angle (default 61°), flat for surfaces without the smooth flag.
- `FlightGearImport`: stages an import. Inputs are the archive, the aircraft, the folder name, the size
  (`Original` | `Span(metres)`) and the kept parts. It returns the files (path to bytes) and a report (skipped items and
  the licence). It applies the frame mapping, as one rotation: Abyssus X = AC z, Y = AC y, Z = -AC x. Offsets are
  converted from FlightGear body axes (x aft, y right, z up) by the same convention. It then scales, centres on the span
  and length midpoints, and sets the lowest vertex to y = 0. `meta.json` gets a fresh random `uuid` and is validated with
  `AbyssusDocumentFormat` before it is staged.

Rejected alternatives: Assimp's AC3D importer, which would need an exporter that gdx-model doesn't have and wouldn't
handle SGI; and Blender, which isn't available inside the IDE.

### Plugin: action, dialog, undoable write

- `ImportFlightGearAction` (`projectView`) appears on the Assets node, the same way `NewTerrainAction` finds the owning
  `.abss`. A file chooser picks a `.zip`. Reading and listing the archive runs on `AppExecutorUtil`, off the EDT.
- `FlightGearImportForm` holds a Swing-free model, `FlightGearImportSettings`: the name rules (unique, valid folder
  name), the size validity, and the part ticks with their rest-state defaults. The model is unit-tested. The dialog
  shows the aircraft choice, description, authors, licence (with a warning when it is unknown), skipped items, the folder
  name, the size and a part checklist. Create is enabled only while the settings are valid.
- Create stages the files on a pooled thread. The result becomes an `AssetTransaction` with `createdDirs` = the folder
  and `FileChange(Absent → Bytes)` per file, executed by `AssetFileCommand` on the EDT. That gives one undoable command,
  restored-exact Redo, and rollback on failure. After it, the new asset is selected in the Abyssus view, as
  `createTerrain` does. No GL is used: there is no preview.
- The undo guard reuses `AssetReferenceGuard` (refuse Undo once a scene references the asset), like New Terrain.
- User-facing strings go in `AbyssusBundle.properties`.

### `source.json` of an imported asset

```
{ "importer": "flightgear", "archive": "c172r.zip", "archiveSha256": "...", "aircraft": "c172r",
  "description": "Cessna 172R", "authors": "...", "model": "Models/c172-dpm.xml",
  "license": "unknown" | "<from set file or licence file name>", "licenseFiles": ["COPYING"],
  "size": {"span": 1.0} | "original", "excludedParts": [...], "skipped": [{"item": "...", "reason": "..."}],
  "frame": "nose +Z, up +Y, left wing +X; lowest point at y = 0" }
```

It is documented in `docs/ai/file-formats.md` as an asset side file that the loader ignores.

### Control Line Trainer

- A game tool `tools/TrainerModel.kt` and a Gradle task `importTrainer` run the same `core` importer. The task
  downloads the archive into `build/flightgear/`, checks the SHA-256 above, keeps all parts except the interior and the
  spinning propeller disc, and imports at a 1.0 m span.
  - The interior parts left out: `Cabin`, `Seat.1-4`, `Yoke.1-2`, `Pedal.1-4`, `Throttle`, `Mixture`, `Compass`,
    `Pedestal`.
  - Because the asset is only replaced, the task writes `model.glb`, `textures/`, `source.json` and `COPYING` into
    `model_trainer`, keeping its `uuid`. It removes the old `model.gltf` / `model.bin`.
  - `source.json` records the licence as `GPL-2.0-or-later`, with the note "FlightGear aircraft licence; the archive
    states none", following the user's decision. `COPYING` holds the GPL-2.0 text.
- The meta's `additional.file` (`model.gltf` → `model.glb`) and the Trainer's pose in `Field.scene` are edited through
  `editSceneJson`. The env-gated `AirfieldPatchApplicationTest` is generalised into `ScenePatchApplicationTest`, which
  applies a JSON patch of `set` / `remove` / `append` operations to any native document. The airfield patch is
  re-expressed in that form. The Trainer patch:
  - `localRotation` = the old parking yaw only. The old value was a yaw with a 13° nose-up pitch. The new one is the
    same heading, level.
  - `localPosition.y` = 0.
  - Model origin: wheels at y = 0, so the plane rests on the ground.
- Flight: the `PlaneComponent` values (leadouts at x = 0.48, mass, line length) are unchanged. The convex-hull collider
  now follows the Cessna (high wing, tricycle gear). `FlightTest` (Trainer takeoff, lift-off with a neutral handle) and
  `BundledProjectTest.parkedPlanesRestOnTheGround` decide whether that holds. If takeoff changes, the fix is the
  leadout height or the parked pose, never the flight model.
- `tools/PlaneModels.kt` keeps the racer, stunter and pilot.
- Found during implementation:
  - The planes' models have their origin at the centre of gravity, which physics, the leadouts and the parked pose
    rely on. So `FlightGearImportRequest` gained an `origin`: `Ground` (the default, as the spec requires for the
    dialog), or a `SourcePoint` in FlightGear's body frame. The trainer uses the estimated centre of gravity
    (x 0.35 m aft, at the cabin's mid-height), so it parks at y = 0.136 m.
  - At this base, `tools/` does not compile: `FieldAssets.kt` still names terrain generation that the
    asset-loading refactor moved out of `core`. So the trainer tool lives in its own `importers` source set, and
    `FieldAssets.kt` is left for that refactor.

### Threads

- Archive reading, XML, AC3D, SGI, GLB writing and staging: a pooled background thread, cancellable between entries
  (`runCatchingKeepingCancellation`).
- The dialog and `AssetFileCommand`: the EDT, with writes in a write action.
- No libGDX or GL calls; the game tool runs in its own JVM.

## Risks / Trade-offs

- [The licence is not stated in the archive] → The import records `unknown` and warns. For the Trainer, the user
  accepted GPL-2.0-or-later bundling, recorded in `source.json`, `COPYING` and the game README.
- [AC3D variants and concave polygons] → The fan triangulation and skipped line surfaces are reported in the dialog.
  The fixture tests cover the c172r constructs. Others can extend the reader without changing the specs.
- [Hostile archives (zip slip, zip bombs, XML entities)] → Path normalisation, size and count caps, a hardened XML
  parser.
- [The Cessna hull changes ground contact or takeoff] → Existing flight tests gate the task, and only the pose and
  leadout height may change.
- [Absolute texture paths] → Resolve by file name: the model folder first, then anywhere in the aircraft.

## Migration Plan

No format migration. Rollback: revert the commit. The old trainer can be regenerated with `generatePlaneModels` after
restoring the trainer entry, and `Field.scene` is restored by reverting its patch.
