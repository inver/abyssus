# Proposal

## Why

Control Line draws its sky with `layered` clouds because `shells` held the game near 35 frames a second. A flat
layer reads as a texture: it has no bright sunlit tops, no grey flat bases and no depth. The reference photo
(`Cumulus_AMayes.jpg`) shows crisp, ragged white cumulus on a deep blue sky, and the game cannot reach that look at a
playable frame rate.

## What Changes

- Add a fourth cloud technique, `cumulus`: one flat layer per band, shaded with a puffy height profile (bright tops,
  soft grey bases, ragged edges) from a single noise pass per band. Cost stays close to `layered`.
- `cumulus` sits between `shells` and `layered` in the fallback chain (volumetric, shells, cumulus, layered).
- The scene view's Clouds choice gains a *Cumulus* entry. This is the only editor UI change.
- Control Line's `clouds_fair` asset uses `cumulus`, and the game stops forcing `layered` over the asset's technique.
- Retune the `skybox_physical` atmosphere values in Control Line toward the photo's deeper blue.
- Evidence: a GL render test, a screenshot beside the photo, and a frame-rate measurement against the 35 fps of `shells`.
- No change to the `meta.json` format version, the `shells`, `layered` and `volumetric` techniques, or cloud band fields.

## Capabilities

### New Capabilities

### Modified Capabilities
- `scene-sky-clouds`: four selectable techniques instead of three, the toolbar choice list, and the fallback chain.
- `cloud-assets`: a cloud asset's `technique` may be `cumulus`.

## Impact

- `projects/lib-core`: `CloudTechnique`, `CloudTechniques.simplerThan`, `ProceduralSky` renderer selection, a new
  `clouds_cumulus.frag`, the cloud README and tests.
- `projects/plugin-abyssus`: `CloudControls` and `AbyssusBundle.properties` (one new choice).
- `projects/app-game-control-line`: `FieldRenderer` (`GAME_CLOUDS`), `clouds_fair/meta.json`,
  `skybox_physical/meta.json`, its README and tests.
- Native documents: `technique` gains the value `cumulus`; `format` and `formatVersion` stay `abyssus` / `1`, and a
  reader that does not know `cumulus` is out of scope (no importer or migration, per the hard rules).
- Out of scope: new cloud bands or types, cloud shadows on terrain, band editing in Properties, other games' skies.
