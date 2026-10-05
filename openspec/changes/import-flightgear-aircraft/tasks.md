# Tasks

## 1. Readers in `core` (headless)

- [ ] 1.1 Add a synthetic FlightGear fixture builder in `core` test fixtures. It writes a small zip in memory with an
  `x-set.xml`, a model XML with offsets, a nested model, a `<panel>` and two `select` animations, an AC3D file with
  three named parts (one two-sided, one with a line surface), and a 4×2 RLE SGI texture referenced by an absolute path.
  Verify: the builder's own round-trip test.
- [ ] 1.2 `FlightGearArchive`: list aircraft, resolve `Aircraft/<dir>/` paths, reject `..` and absolute entries, cap
  sizes, entry counts and nesting. Verify: `FlightGearArchiveTest` (fixture aircraft listed; an archive without a set
  file reports none; zip-slip and oversize entries rejected).
- [ ] 1.3 `FlightGearModelXml` + `RestState`: hardened DOM parsing, offsets, nested models, `select` conditions
  evaluated with every property 0. Verify: `RestStateTest` (`Propeller` shown, `Propeller.2` hidden; `and`/`or`/`not`;
  an unknown element shows the part) and `FlightGearModelXmlTest` (a DTD with an external entity is refused).
- [ ] 1.4 `Ac3dReader`: materials, nested objects with `loc`/`rot`, textures, `texrep`/`texoff`, `crease`, surfaces with
  UVs and flags, skipped line surfaces counted. Verify: `Ac3dReaderTest` on the fixture, plus the real
  `c172-dpm.ac` read from `build/flightgear` when present (33 parts, 862 vertices).
- [ ] 1.5 `SgiImage`: verbatim and RLE, 1-4 channels, bottom-up rows, then PNG. Verify: `SgiImageTest` (fixture pixels
  match after a PNG round trip; a bad magic number is rejected).

## 2. Conversion and staging in `core`

- [ ] 2.1 `GlbWriter`: nodes named after the parts, normals within the crease angle, one primitive per material and
  texture, `doubleSided`, external PNG URIs. Verify: `GlbWriterTest` (the GLB validates structurally; node names; and
  `AssimpModelLoader.loadData` reads it back with its textures).
- [ ] 2.2 `FlightGearImport`: frame mapping, offsets, span scaling, centring, lowest point at y = 0, part filter, texture
  resolution by file name, the `meta.json` (fresh uuid, validated by `AbyssusDocumentFormat`) and the `source.json`
  report, and licence files copied. Verify: `FlightGearImportTest`:
  - the fixture's nose part ends at +Z, its left part at +X, the 1.0 m span, min y = 0;
  - an unticked part is absent;
  - the panel, the line surfaces and a missing texture are listed in `skipped`;
  - the licence is `unknown` without a licence file, and the archive SHA-256 is recorded.
- [ ] 2.3 Update `core/README.md` and `docs/ai/file-formats.md` (`source.json` of an imported asset), then run
  `./gradlew :core:test :core:checkNoSingletons` and `scripts/check-docs.sh`.

## 3. Plugin action

- [ ] 3.1 `FlightGearImportSettings`, the Swing-free model: default folder name, uniqueness and validity, size validity,
  default ticks from the rest state, Create enabled only while valid with at least one part ticked. Verify:
  `./gradlew :test --tests 'net.nevinsky.abyssus.projectView.FlightGearImportSettingsTest'`.
- [ ] 3.2 `ImportFlightGearAction`, the dialog and the bundle strings. Staging runs off the EDT; the write is an
  `AssetTransaction` run by `AssetFileCommand` with the reference guard, and the new asset is selected afterwards.
  Register it in `plugin.xml` next to New Terrain. Verify: `./gradlew :test --tests
  'net.nevinsky.abyssus.projectView.ImportFlightGearTest'`, a platform test on a copy of the Untitled fixture:
  - the import creates the folder with a native meta and the project lists it as unused, with no scene or `.abss`
    change;
  - Undo removes it and Redo restores identical bytes and uuid;
  - an invalid `.abss` is refused before writing.
- [ ] 3.3 Update `src/main/kotlin/net/nevinsky/abyssus/projectView/README.md` and the user section of `README.md`.
  Verify: `scripts/check-docs.sh`.
- [ ] 3.4 runIde check 1: in a copy of Untitled, run Assets → Import FlightGear Aircraft... → `c172r.zip`. Expected:
  - the dialog shows "Cessna 172R", the authors, the unknown-licence warning, the skipped panel, and `Propeller.2`
    unticked;
  - Create adds `model_c172r`;
  - placing it in a scene shows a textured Cessna, nose +Z, standing on its wheels;
  - Edit → Undo removes the folder.

## 4. Control Line Trainer

- [ ] 4.1 `tools/TrainerModel.kt` and the `importTrainer` task:
  - download into `build/flightgear/` and check the SHA-256;
  - import at a 1.0 m span without the interior parts and `Propeller.2`;
  - write `model.glb`, `textures/`, `source.json` (licence `GPL-2.0-or-later` with the note) and `COPYING` into
    `model_trainer`, removing `model.gltf` / `model.bin` and keeping the `uuid`.

  Drop the trainer from `tools/PlaneModels.kt`. Verify: `./gradlew :games:control-line:importTrainer` twice gives
  byte-identical files.
- [ ] 4.2 Generalise `AirfieldPatchApplicationTest` into the env-gated `ScenePatchApplicationTest` (set / remove / append
  on any native document) and re-express the airfield patch in that form. Then apply the Trainer patch through
  `editSceneJson`: `model_trainer/meta.json` gets `additional.file` = `model.glb`; `Field.scene` gets the Trainer
  `localRotation` (level, same heading) and `localPosition.y` = 0. Verify: a diff against the pre-edit copies shows only
  those values, and `./gradlew :games:control-line:test` passes, including:
  - `BundledProjectTest` (no load warnings, parked planes at rest);
  - `FlightTest` (Trainer takeoff with a neutral handle);
  - `AirfieldEnvironmentTest`.

  If the takeoff fails, adjust only the leadout height or the parked pose, and record why.
- [ ] 4.3 Update `games/control-line/README.md` (the Trainer is FlightGear's Cessna 172R, GPL-2.0-or-later, credit to
  David Megginson, re-import with `importTrainer`). Verify: `scripts/check-docs.sh`.
- [ ] 4.4 Game check: `./gradlew :games:control-line:run`, plane select → Trainer shows the textured Cessna parked level;
  a flight takes off and flies as before.

## 5. Integrated verification

- [ ] 5.1 Run `./gradlew check` and `scripts/check-docs.sh`. Report failures that come from outside this change, such
  as the unfinished asset-loading refactor's plugin and physics-plugin tests, separately, with their causes.
