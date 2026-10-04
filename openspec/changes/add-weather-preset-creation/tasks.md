# Tasks

Starts after `add-sky-clouds` and `add-asset-editing-and-terrain-generation` are applied. Manual checks use a copy
of `Untitled` (never the fixture itself).

## 1. Draft and action

- [ ] 1.1 Re-read the applied `AssetFileCommand` and folder-name validation APIs, and record any difference from this
  design in `design.md`. Verify with `openspec validate add-weather-preset-creation --strict`.
- [ ] 1.2 Add `WeatherPresetDraft` (resolve, report an unreadable preset, serialize in key order with fully resolved
  bands). Verify with `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.sky.clouds.WeatherPresetDraftTest'`:
  - a built-in storm with a sky cirrus override;
  - a folder preset;
  - a missing preset (only the sky's bands, with a notice);
  - the key order;
  - every band field written;
  - a round trip through `WeatherPresetReader`.
- [ ] 1.3 Add `NewWeatherPresetAction`, its dialog (with the `weather_<sky>` suggestion and the `:` rule) and its
  `plugin.xml` registration and bundle strings, creating through `AssetFileCommand`. Verify with
  `./gradlew :test --tests 'net.nevinsky.abyssus.projectView.NewWeatherPresetActionTest'`:
  - shown only for procedural skies with `clouds`;
  - name, path, `:` and collision rejections;
  - the created `meta.json` content, including the native `format` / `formatVersion` markers;
  - selection after creation;
  - the sky, the `.scene` and the `.abss` unchanged byte for byte;
  - Undo removes the unchanged folder;
  - Redo reuses the UUID;
  - the new preset is listed unused.
- [ ] 1.4 Document the action in the user section of `README.md`, the projectView README and the changelog. Verify
  with `scripts/check-docs.sh`.

## 2. Integration

- [ ] 2.1 Do runIde check 1 on a copy of `Untitled`:
  1. Give `skybox_physical` storm clouds with a cirrus override.
  2. Create `weather_mystorm`; it is selected and unused.
  3. Point a second sky at it by hand; it looks the same.
  4. Undo the creation in a fresh copy.
- [ ] 2.2 Verify that `./gradlew check` and `openspec validate add-weather-preset-creation --strict` pass.
