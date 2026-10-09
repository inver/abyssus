# Tasks

The cloud runtime and asset-file transaction infrastructure already exist. No historic change is a pending
prerequisite. Manual checks use a copy of
`projects/plugin-abyssus/src/test/testData/project/Untitled`, never the committed fixture.

## 1. Canonical cloud settings and headless draft

- [x] 1.1 Add canonical metadata round-trip cases to `CloudSettingsReaderTest` and make targeted cloud binding
  corrections in `lib-core` as needed: lowercase technique/type keys, inferred level, `wind` arrays, type defaults,
  and valid-band isolation under the existing cloud specs. Preserve current accepted native reads without
  rewriting documents. Verify with
  `./gradlew :lib-core:test --tests 'net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudSettingsReaderTest'`:
  canonical minimal/complete bands,
  all techniques, all type defaults, invalid-band isolation and unchanged input trees.
- [x] 1.2 Add constructor-wired `WeatherPresetDraft` and its writer in
  `net.nevinsky.abyssus.lib.core.editor.weather`, using admitted source trees and the shared cloud settings binder.
  Verify with
  `./gradlew :lib-core-editor:test --tests 'net.nevinsky.abyssus.lib.core.editor.weather.WeatherPresetDraftTest'`:
  native admission and wrong-type refusal;
  all resolved band fields; absent/invalid bands; stored technique/default shells; canonical lowercase keys and
  wind; root key order; fresh identity fields; retained extension data and numeric text; and a runtime binder
  round trip with the same resolved settings.
- [x] 1.3 Document canonical cloud binding and snapshot serialization in `projects/lib-core/README.md`,
  `projects/lib-core-editor/README.md` and `docs/ai/file-formats.md`, explaining source default omission versus
  explicit defaults in new snapshots. Verify documented fields against the tests in 1.1/1.2 and run
  `scripts/check-docs.sh`.

## 2. Plugin creation workflow

- [x] 2.1 Add source selection and UUID resolution from current native project metadata with unsaved document text
  taking precedence; re-read on Create. Stage a metadata-only `AssetTransaction` with `checkFolderName`,
  `uniqueAssetUuid`, an injected clock and `AssetReferenceGuard`. Verify with
  `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.NewWeatherPresetActionTest'`:
  project-scoped UUID resolution,
  unsaved sky and cloud edits, unavailable/wrong-type/unsupported/empty sources, invalid names including colons,
  reserved names, escaping paths, case-insensitive collisions, a staging-time collision and no writes on refusal.
- [x] 2.2 Add `NewWeatherPresetAction`, its name dialog, `plugin.xml` registration and bundle strings; create through
  `AssetFileCommand.execute` and select with `selectAssetInAbyssusView` after successful VFS refresh. Verify with
  `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.NewWeatherPresetActionTest'`:
  visibility only for supported procedural
  sky rows with nonblank text references, `weather_<sky>` suggestion, canonical metadata content and selection,
  `CLOUDS` listing and unused state, byte-for-byte unchanged source/cloud/scene/project files, unchanged-folder
  Undo, refusal after an edit or extra file, saved and unsaved reference guards, stable UUID/timestamp/bytes on
  Redo, collision refusal and failed-write rollback. Reuse `AssetFileCommandTest` for generic transaction behavior
  rather than duplicating its engine tests.
- [x] 2.3 Document the user action in `README.md`, `CHANGELOG.md` and
  `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/projectView/README.md`. Verify the described
  behavior against 2.1/2.2 and run `scripts/check-docs.sh`; retain the plugin description markers in README.

## 3. Integration

- [ ] 3.1 Perform runIde check 1 with `./gradlew :plugin-abyssus:runIde -PideProject=/path/to/copy`:

  1. In a copy of `Untitled`, create native `assets/clouds_storm/meta.json` with low stratocumulus, mid altostratus
     and high cirrus bands, and point `skybox_physical.additional.clouds` at its UUID.
  2. Leave a band edit unsaved, choose New Weather Preset from Sky, create `weather_mystorm`, and verify its tree
     selection, Properties display, `CLOUDS` type, unused marker and copied settings.
  3. In a separate copy, point a second procedural sky at the snapshot UUID; with both views using Asset technique,
     verify equivalent weather after reload and the snapshot marked used. Verify a toolbar override is not copied.
  4. Undo unchanged, unreferenced creation from the tree/Properties context and Redo it; verify stable metadata.
     Then verify Undo refuses a new saved or unsaved reference or changed folder contents, and Redo refuses a
     collision. Confirm the source cloud asset, sky, scene and project are unchanged by creation itself.

- [x] 3.2 Run `./gradlew check`, `scripts/check-docs.sh` and
  `openspec validate add-weather-preset-creation --strict` and confirm they pass.
