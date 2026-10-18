# File formats

Abyssus owns the JSON format of project `.abss`, scene `.scene` and asset `meta.json` documents.
The plugin reads them with `SceneJson` (`projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/document/SceneJson.kt`),
which keeps key order, `null` members and the exact text of numbers. Writes must keep those, too. The user-facing
description of what the tree shows is in `README.md` ("Abyssus view"); this page is the format reference.

Examples: `projects/plugin-abyssus/src/test/testData/project/Untitled/` (models, terrain, skybox, a camera) and
`projects/plugin-abyssus/src/test/testData/project/Animated/` (an animated model).

## Native document identity

Version 1 requires these root members in each project, scene and asset metadata document:

```json
{ "format": "abyssus", "formatVersion": 1 }
```

`AbyssusDocumentFormat` (`projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/format/AbyssusDocumentFormat.kt`)
validates document identity and reserved scene fields on the caller's thread, without GL or platform services.
Only the exact string `abyssus` and integral JSON version `1` are supported. Missing/null/foreign markers, string
or fractional versions (including `1.0`), negative versions and future versions are unsupported. Asset metadata's
`version` has its own meaning; it is not the document `formatVersion`.

Unsupported documents are not imported or converted. They remain openable as text, with an explanation from the
plugin; automatic formatting and plugin edits must leave their document text and disk bytes unchanged. Supported
siblings remain available. New documents put the markers first; edits preserve their existing positions, unrelated
keys and exact number text. External model/image formats and the terrain recipe do not receive these markers.

## Project layout

`ProjectLayout` (`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/dto/ProjectLayout.kt`) defines it:

```
<project>/
  <name>.abss
  scenes/<scene>.scene        one file per scene
  assets/<folder>/meta.json   one folder per asset, plus the files its meta.json names
```

- **Scene membership:** a `.scene` belongs to a project only when it sits in a `scenes` folder beside an `.abss`.
  Others are standalone and listed at the top level of the Abyssus view.
- **Extension matching:** extensions are matched exactly and case-sensitively. `.SCENE` and `.scene.bak` are not
  asset files.

## `.abss` (project)

The plugin reads:
- `name`: the project label; the file name is used when it is missing.
- `mainCamera`: `position`, `viewPointPosition` (the view direction), `near`, `far` and `fieldOfView`. It is the
  scene view's starting camera (`MainCamera` in `projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/scene/SceneRenderParams.kt`).

- `physicsEnabled`: an optional boolean for editor physics. Missing, false or malformed means off; unsupported
  native documents cannot enable physics. The settings service reads unsaved document text when present.

- `uiAssetNames`: an optional ordered list of UI asset folder names, the explicit registry of the project's UI assets
  (see *UI assets* below). Missing means none and is never inserted on read; entries that are not strings are skipped.

Other members (`settings`, `activeSceneName`, `selectedCamera`, ...) are ignored. Changing Physics in project
properties writes only `physicsEnabled` through `editSceneJson` as one undoable command; off is explicit `false`.
Reads never insert the key or repair malformed values. `SceneFormatListener` may also format a supported `.abss`.

`sceneViewAntialiasing` is an optional editor preference for this native project's scene views: `OFF`, `X2`, `X4`
or `X8`. Missing means 4×; saving 4× removes the key. Invalid present values are reported and displayed as 4× without
rewriting the file. Select the project in Abyssus Properties to edit it through `editSceneJson` as one undoable command.
Text edits and Undo/Redo update its open views; other projects are independent. Unsupported native documents cannot be
edited. The runtime ignores this editor preference; `.scene` and asset metadata are unaffected.

## `.scene`

Top level, bound to `SceneDto` (`projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/dto/SceneDto.kt`):

| Key | Meaning |
|---|---|
| `id`, `name` | Shown as `name (id)`; `name` is changed by Rename Scene |
| `ambientLightEnabled` / `ambientLight` | `color` + `intensity` |
| `fogEnabled` / `fog` | `color`, `density`, `gradient` |
| `skyboxEnabled` / `skyboxName` | `skyboxName` is a `SKYBOX`, `SKYBOX_PROCEDURAL` or `SKYBOX_HDR` asset folder name, or `null` |
| `ecs` | Entities, kept as raw JSON (`JsonNode`) |
| `abyssus` | Abyssus-only extension data, kept as raw JSON; today it holds `waterSurfaces` (see "Water surfaces") |

**Toggles:** an `<x>Enabled` boolean gates `<x>` (or `<x>Name`). The tree folds it into an eye on the gated row. The
tree shows `skyboxName` as `skybox`, but the key in the file stays `skyboxName`.

An entity entry and a component entry of `ecs` may also carry an optional boolean `enabled`, written only as `false` (a
missing key means enabled). On an entity it sits beside `components`; on a component it is the last key of the component
object and is reserved: `EcsLoader` strips it before binding, so a component class never sees it, and a registered class
that declares a field named `enabled` is kept as read-only raw JSON with a warning. A disabled entity or component loads as
data but does nothing (no rendering, light, camera, foliage, body or shape) and is neither drawn nor picked in the scene
view. A non-boolean `enabled` makes the scene unsupported for editing. `formatVersion` stays 1; a file that never uses the
key is unchanged by any edit.

### `ecs`

```
ecs: { "<id>": { components: { "<Name>Component": {...}, ... } }, ... }
  (native files can also wrap the map in an `entities` member beside `metadata` and `archetypes`)
```

The editor document layer supports both shapes. The current `EcsLoader` only enumerates the wrapped `entities`
map, and `lib-core-editor`'s `EcsWriter` produces that shape without carrying block metadata or archetypes. This differs
from the required round-trip behavior in `openspec/specs/scene-ecs-components/spec.md`.

The native component fields described by the specs and fixtures:

| Component | Used for |
|---|---|
| `NameComponent.name` | Entity label (the id when missing) |
| `TypeComponent.type` | `CAMERA`, `LIGHT_*` (kind of light), `HANDLE` (a light's direction handle) |
| `PositionComponent` | `localPosition`, `localRotation` (quaternion `x y z w`) and `localScale`; plus `lookAtId` for cameras and lights |
| `RenderComponent.renderable` | `kind: "asset"`, `asset.assetName` + `asset.type` (`MODEL` / `TERRAIN`) and optional `shaderKey`; unknown kinds stay raw without rendering |
| `CameraComponent.camera` | `position`, `viewPointPosition` (the view direction), `near`, `far`, `fieldOfView` |
| `LightComponent` | `color`, `intensity`, `range` (positive reach, default 100 omitted), either directly or under `light` |
| `Point2PointPositionComponent` | `entity1Id` / `entity2Id` |
| `ParentComponent` | Read by the ECS loader; the scene view ignores parents and uses `local*` as world values |

**Defaults are omitted:** under the native version 1 contract, a missing field is its default (position 0, identity rotation, scale 1). An
empty `PositionComponent: {}` is valid. Writers add fields when they change them (`SceneTransformWriter`,
`PositionCodec`).

**Light defaults** (`projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/ecs/component`). A light that leaves a value out has: `intensity` 1; the
whole `color` object missing means white, but a channel missing inside a `color` object is 0 (the alpha channel never
affects lighting); `range` 100; `coneAngle` 45 and `edgeSoftness` 0.2. The scene view, the Properties panel and edits
all read lights through the same codecs, so they show and use these values alike. The file is never rewritten to state
them. (Before this, the view drew a missing `intensity` as 0.3 and a missing channel as 1; the panel always showed
1 and 0.)

**`lookAtId` may be an integer or text** (`3`, `"3"`, `"-1"`, `"h"`). The decoded reference is used by the view, and an
edit of another field keeps the original node; an edit of `lookAtId` itself writes an integer.

**Cameras have two positions:** a camera's position is stored both in `PositionComponent.localPosition` and in
`CameraComponent.camera.position`. Gizmo moves write both.

**A light that looks at an entity takes its direction from it.** A `LIGHT_DIRECTIONAL` or `LIGHT_SPOT` light with a
`PositionComponent.lookAtId` pointing at an existing entity faces it: the view direction is the unit vector from the
light to that entity's `localPosition`. The light's own `localRotation` is still read and used when the target is
missing or at the light itself. A light whose target is a `HANDLE` entity (an entity whose `TypeComponent.type` is
`HANDLE`, a light's direction handle) is a **handle-aimed light**: a rotate gizmo drag on it turns the
direction and moves the handle, and the handle's `localPosition` is written instead of the light's rotation. A light
aimed at anything else (a model, a camera, a terrain) only moves; turning it would mean moving an unrelated object.
A point light has no direction and never gets a ring. The scene view's own light direction is the same resolved
direction (`SceneContent`).

### Component identifiers and extension data

Component map keys such as `PositionComponent` are stable schema identifiers independent of their implementation
packages. Optional `archetypes` lists use these short identifiers. `ecs.componentIdentifiers` and
`RenderComponent.renderable.class` are rejected even when the scene has native markers; they are not aliases.
These reserved paths are checked by the shared validator in editor and JVM readers. Raw ECS loads validate before
changing the engine; writers and direct render serialization also reject them. Both the short and fully qualified
built-in RenderComponent names are checked; unrelated extension components remain opaque.

```json
{ "renderable": { "kind": "asset", "shaderKey": "pbr",
    "asset": { "type": "MODEL", "assetName": "tree" } } }
```

Unknown components and native renderable kinds remain raw and round-trip unchanged. Their payloads are opaque:
a `class` string inside a custom component or a marker's nested payload does not activate class loading or cause
format rejection. Adding or editing a component never creates a Java-class identifier table.

### Game components

A game's own Jackson-bindable Ashley components are native extension data keyed by a registered name, usually
their short class name, next to built-in components: `ecs.entities.<id>.components.<Name>`. Register them with
`core.ecs.ComponentRegistry` before `EcsLoader` loads them. No annotation-based schema API is present in the
current source set. A scene containing `ecs.componentIdentifiers` is rejected before component loading.

The document validator treats extension payloads as opaque. `EcsLoader` carries unregistered or unbindable
components as raw JSON with a warning; `EcsWriter` writes that carried data back. The editor's built-in component
editing keeps custom entries unchanged. `EcsWriter` omits default properties and writes integral floats as integers.

### Retained component schema files (`abyssus/components.schema.json`)

Some fixtures and bundled projects retain schema JSON from the earlier schema workflow. These are supporting
files, not native `.abss`, `.scene` or asset documents. The current `ComponentSchemas` service does not read them
or contributions from the `componentSchemas` extension point. They do not enable editing game or physics
components, and the source set has no `SchemaExportMain` or schema export task.

Required schema behavior remains in the `component-schemas` and `custom-scene-components` specs; the current
implementation does not yet provide it.

## Asset `meta.json`

```json
{ "format": "abyssus", "formatVersion": 1, "version": 1, "lastModified": 1663444124794, "uuid": "...", "type": "MODEL", "additional": { ... } }
```

| `type` | `additional` |
|---|---|
| `MODEL` | `file`, `format`, `binary`, `materials` (material asset `uuid`s) |
| `TERRAIN` | `terrainFile`, `size`, `uv`, `splatMap`, `splatBase`, `splatR`, `splatG`, `splatB`, `splatA` (texture asset `uuid`s) |
| `SKYBOX` | `top`, `bottom`, `left`, `right`, `front`, `back` (image files in the folder) |
| `SKYBOX_PROCEDURAL` | `vertex`, `fragment` (GLSL files in the folder); optional atmosphere parameters `planetRadius`, `atmosphereRadius`, `betaRayleigh` (3 numbers), `betaMie`, `heightRayleigh`, `heightMie`, `mieG`, `sunIntensity` (Earth-like defaults); optional `clouds`: the `uuid` of a `CLOUDS` asset (see *Clouds* below); optional `lightsScene`: the boolean `true` to light the scene from the sky (see *Sky lighting* below) |
| `CLOUDS` | `technique` and up to three cloud bands `low`, `mid`, `high` (see *Clouds* below) |
| `SKYBOX_HDR` | `file`: the OpenEXR image file inside the asset folder |
| `FOLIAGE` | `terrain`, `dataFile`, `maskResolution`, `layers` (see *Foliage* below) |
| `TEXTURE`, `PIXMAP_TEXTURE` | `file`; loaded as textures, including terrain splat dependencies |
| `MATERIAL`, `SHADER` | Recognized metadata types; no standalone scene drawable |
| `UI` | `ui`: a collection of screens and overlays (see *UI assets* below) |

`uuid` can be missing (the fixture's `skybox_default` and `tree` have none).

### Terrain data and the generation recipe

A terrain folder holds `meta.json` and the height file named by `additional.terrainFile` (`terrain.data`): no header,
each height a big-endian 32-bit float, a square grid row after row (z-major), so the resolution is the square root of
the float count (the fixture's is 180). Generating a terrain changes none of this: it writes the same two files.

New terrain metadata is one compact line with native markers first (`format`, `formatVersion`), then `version` 1, `lastModified`, `uuid`, `type` `TERRAIN`,
`additional` with `terrainFile`, `size`, `uv` 1.0 and the six splat fields null; see `TerrainAssetWriter` in
`projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/terrain`.

How generated heights were made is kept in a recipe file beside the
heights (`TERRAIN_RECIPE_FILE`; a terrain loads without it):

```json
// abyssus-terrain.recipe.json
{ "schemaVersion": 1,
  "generator": { "id": "opensimplex2-fbm-v1", "sourceRevision": "<FastNoiseLite commit>" },
  "settings": { "seed": 12345, "featureSize": 200.0, "minHeight": 0.0, "maxHeight": 120.0, "octaves": 5, "persistence": 0.5, "lacunarity": 2.0 },
  "size": 1600, "resolution": 180, "heightsSha256": "<SHA-256 of terrain.data>" }
```

The recipe is a fingerprint, not a source of truth: if the size, the resolution or the height bytes no longer match,
or the schema or generator identifier is unknown, or the file is malformed, the terrain stays usable, the panel shows
why, and a draft starts from the defaults above. An identifier is never reinterpreted: new noise gets a new
identifier. Heights come from world-local OpenSimplex2 fractal noise (`x / (resolution - 1) * size`), mapped onto
`minHeight..maxHeight`, so a height means the same at any resolution.

### Foliage

A foliage folder covers one terrain asset with many copies of model assets. It holds `meta.json`, an optional density
mask per layer (`layer-<id>.mask`), and the bake named by `additional.dataFile` (`foliage.data` by default).

```json
{ "format": "abyssus", "formatVersion": 1, "version": 1, "lastModified": 0, "uuid": "...", "type": "FOLIAGE",
  "additional": { "terrain": "<terrain asset folder>", "dataFile": "foliage.data", "maskResolution": 512, "layers": [
    { "id": 1, "kind": "OBJECT", "models": [{ "asset": "tree", "weight": 1.0 }],
      "density": 0.05, "scale": { "min": 0.8, "max": 1.2 }, "alignToNormal": 0.5,
      "minHeight": null, "maxHeight": null, "maxSlope": null, "drawDistance": 80.0, "seed": 0 } ] } }
```

- `terrain` names a TERRAIN asset folder; `maskResolution` is the mask's square side, 16 through 2048 (default in the
  dialog 512; an omitted member reads as 256).
- A layer has a unique integer `id`, a `kind` (`OBJECT`: few copies, cast shadows; `DETAIL`: many copies, drawn near
  the camera), one or more MODEL asset folders with positive `weight`s, a `density` in copies per square unit at full
  mask, a `scale` range, an `alignToNormal` fraction from 0 through 1, optional `minHeight`, `maxHeight` and
  `maxSlope` (degrees) rules, a `drawDistance` for `DETAIL` and a `seed`. Omitted members read as the defaults above:
  `density` 0.05, `scale` 0.8 to 1.2, `alignToNormal` 0.5, `drawDistance` 80, `seed` 0, no height or slope limits.
- `layer-<id>.mask` is `maskResolution²` bytes from 0 through 255, row after row (z-major) over the terrain's square,
  like `terrain.data`. A missing mask means full density; a file of any other length is an error naming the file.
- `foliage.data` is a binary cache of the generated copies, not a native JSON document: the native `formatVersion`
  does not change with it. Big-endian, `"ABFO"`, `u16` version 1, 32 fingerprint bytes, `f32` chunk size, `u16` layer
  count; per layer `i32` id, `u16` model count, `u16` chunks X, `u16` chunks Z; per chunk (z-major) `i32` count, then
  that many copies of `u16` model, `f32` x, `f32` z, `f32` yaw, `f32` scale (terrain-local; height and tilt are taken
  from the terrain when drawn). The chunk size is `max(32, size / 64)` world units. The fingerprint is SHA-256 over
  the generator id, the terrain's size and heights, `maskResolution`, each layer's generation fields and each mask's
  bytes; a bake of another magic or version, a truncated one, or one whose fingerprint no longer matches is treated
  as stale and regenerated, never as an error.
- An `OBJECT` layer may carry an optional `collider` for physics worlds: `shape` (`BOX`, `SPHERE` or `CAPSULE`, default
  `BOX`), `halfExtents` `{x, y, z}` (default 0.5 each), `radius` (0.5), `halfHeight` (0.5, along the copy's up axis)
  and `offset` `{x, y, z}` (0), in model units from each copy's origin. The defaults match the physics
  `ColliderComponent`. Only the active shape's sizes are used: they must be finite and positive, and the offset
  finite. An omitted (or null) `collider` means no collision; an unknown shape, a malformed payload or an invalid
  active size disables only that layer's collision and never its drawn copies, and nested members this program does
  not model are kept when it edits the layer. `DETAIL` layers never collide, so a collider on one is ignored with a
  warning. No collider member enters the bake's fingerprint.
- Games and physics worlds read a bake as written and never regenerate it. A matching bake gives the editor's copies.
  A stale one keeps its saved terrain-local x/z, yaw, scale and model indices, with height and tilt taken from the
  current terrain and `alignToNormal`, and each saved model index mapped onto the layer's current model list: ABFO v1
  holds no model folder names, so it cannot recover the models it was made from or the editor's regenerated
  distribution. Copies of a removed layer, of an index the layer no longer has or of a missing model are dropped, a
  mask that cannot be read leaves the bake used but unverified, and each problem is reported once per read.
- A scene shows a foliage asset on a terrain entity through
  `"FoliageComponent": { "assetName": "<foliage folder>" }`. The component keeps its other members, their order and
  number text when a scene is written back; a missing or null name shows nothing, and a name that is not a string
  leaves the component unchanged in the file with one warning.
- A path arrangement is an entity of type `GROUP` with a `PathArrangementComponent`: `points` (`x`, `y`, `z`, world
  coordinates), `closed`, `asset` (`type` `MODEL`, `assetName`), `spacing` (default 10), `offset` (default 0), `sides`
  (`CENTER` default, `LEFT`, `RIGHT`, `BOTH`), `alignToPath` (default true), `yawOffset` (degrees, default 0) and
  `slots`, the entity id of the copy in each place along the line (`-1` for none). Defaults are omitted. The copies
  are ordinary `OBJECT` entities in world coordinates with a `ParentComponent` pointing at the arrangement; the
  runtime reads the component as data and runs no system for it. Moving the arrangement entity does not move them.

### Imported FlightGear models

Import FlightGear Aircraft writes an ordinary `MODEL` folder: `meta.json` (`additional.file` `model.glb`, `format`
`GLTF`, `binary` true, a fresh `uuid`), `model.glb` with its textures as external files in `textures/` (SGI images
converted to PNG), the archive's licence files (`COPYING`, `LICENSE*`), and a `source.json` the loader ignores:

```json
{ "importer": "flightgear", "archive": "c172r.zip", "archiveSha256": "...", "aircraft": "c172r",
  "description": "Cessna 172R", "authors": "...", "model": "Models/c172-dpm.xml",
  "license": "unknown", "licenseFiles": [], "size": { "span": 1.0 },
  "excludedParts": ["Propeller.2"], "skipped": [{ "item": "...", "reason": "..." }],
  "frame": "nose +Z, up +Y, left wing +X; centred on span and length; lowest point at y = 0" }
```

`license` is the set file's `license` entry, `see <file>` for licence files in the archive, or `unknown`. Each glTF node
is one named AC3D part. See `projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/flightgear/FlightGearImport.kt`.

### Imported models

Import Model writes an ordinary `MODEL` folder: `meta.json` (`additional.file` `model.glb`, `format` `GLTF`, `binary`
true, `materials` empty, a fresh `uuid`), `model.glb` (the converted model: metres, +Y up, standing on y = 0 and
centred on X and Z, with its node hierarchy, skins and every animation), its textures as PNG files in `textures/`, and
a `source.json` the loader ignores:

```json
{ "importer": "model", "source": "hero.fbx", "sourcePath": "sources/hero.fbx", "sourceSha256": "...",
  "sourceFormat": "FBX",
  "stated": { "unit": "cm", "upAxis": "Z" }, "chosen": { "unit": "cm", "upAxis": "Z" },
  "size": "original",
  "animations": ["Idle", "Run"],
  "skipped": [{ "item": "Camera001", "reason": "camera" }, { "item": "wood.png", "reason": "missing" }],
  "approximated": [{ "item": "Body", "reason": "specular colour dropped" }] }
```

- `sourcePath` is relative to the folder of the `.abss` file, with forward slashes, when the source is inside it, and
  absolute otherwise; a later re-import finds the source through it.
- `sourceFormat` is one of `OBJ`, `FBX`, `3DS`, `DAE`, `GLTF` and `GLB`.
- `stated` is what the file states (FBX header, DAE `<asset>`) or its format defines (glTF: `m`, `Y`; 3DS: `m`,
  `Z`); its values are `null` when neither says anything (OBJ). A DAE `X_UP` is recorded as `"X"`. `chosen` is what the
  import applied.
- `size` is `"original"`, `{ "largestExtent": <metres> }` or `{ "height": <metres> }`.
- `skipped` reasons: `camera`, `light`, `points or lines`, `morph targets`, `glTF extension`, `missing`,
  `unsupported texture format`.

See `projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/modelimport/ModelImport.kt`.

**`SKYBOX_PROCEDURAL` is a native asset type.** The scene view
draws it as a fullscreen triangle with the folder's own shaders (single-scattering Rayleigh + Mie, ray-marched per
pixel; the fixture is `assets/skybox_physical`). The plugin supplies these uniforms: `u_invViewProj` (vertex),
`u_sunDir` (unit vector toward the brightest directional light's opposite; a default 45 degree sun without one),
`u_cameraHeight`, `u_planetRadius`, `u_atmosphereRadius`, `u_betaRayleigh`, `u_betaMie`, `u_heightRayleigh`,
`u_heightMie`, `u_mieG`, `u_sunIntensity`. The vertex shader takes `attribute vec2 a_position` (the three corners of
the triangle). A missing file or a compile error skips that sky and logs it.

**Clouds** are their own asset, type `CLOUDS`, which a `SKYBOX_PROCEDURAL` names by `uuid` in `additional.clouds`, the
way a terrain names its splat textures. Several skies can share one. The view draws them over the sky asset's own
atmosphere with plugin shaders (`projects/lib-core/src/main/resources/shader/sky/clouds_*`), so they work with any
asset `sky.frag`. Nothing is written back.

```json
{ "format": "abyssus", "formatVersion": 1, "uuid": "...", "type": "CLOUDS", "additional": {
  "technique": "shells",
  "low":  { "type": "cumulus", "base": 800, "top": 2000, "coverage": 0.4, "density": 0.8, "wind": [4, 1] },
  "high": { "type": "cirrus" }
} }
```

- A sky without `clouds`, with a `uuid` no asset declares (logged), or naming an asset with no valid band draws as
  without clouds. A `clouds` value that is not text is ignored.
- `technique`: `layered`, `shells` (the default) or `volumetric`. The scene view's Clouds toolbar choice can override
  it per view without writing it.
- `low`, `mid`, `high`: bands. `type` is required; every other field takes the type's default when omitted.
  `base` / `top` are altitudes in metres, `base` below `top`, both inside the level's limits; `coverage` is 0 to 1;
  `density` is 0 or more; `wind` is two numbers, metres per second along x and z. A band that breaks a rule is
  skipped and logged, and the other bands and the sky still draw.
- Loading: the sky lists the cloud asset as a dependency, so the asset storage loads it first and the sky reads the
  built asset on every draw; an edited cloud asset shows once it is loaded again. The cloud asset also owns the
  volumetric technique's tileable 3D noise (FastNoiseLite Perlin-Worley, 64³ base and 32³ detail), made when it is
  prepared and uploaded when it is built.
- **Sky lighting.** `additional.lightsScene` opts a `SKYBOX_PROCEDURAL` into lighting models and terrain, as an HDR sky
  does: while the scene shows that sky it replaces the ambient color (also when the scene's ambient light is disabled),
  and PBR models reflect it. Only the literal boolean `true` opts in; absent, `false` or any other value (including the
  string `"true"`) keeps the ambient color, and the fixture's `skybox_physical` has none. The light is rendered from the
  asset's atmosphere parameters and cloud asset by an Abyssus-owned linear model (the same single-scattering model
  as the fixture's `sky.frag`) into a 64²-per-face cube; the visible background still comes from the asset's own
  `sky.frag`, so a custom shader that departs from the physical parameters can look different from the light it gives.
  The light is rebuilt when the sun direction changes by 1° or more, every 2 s of cloud time while a band has wind,
  and on cloud asset reload or visibility change, then fades in over about one second. Until the first build
  completes, or if it fails, the ambient color lights the scene; a failed refresh keeps the last light. Nothing is written back.
- **Cloud shadows.** While a procedural sky draws clouds, they also shade the sun on terrain and models: dark patches
  that drift with each band's wind and sit where the sky shows clouds. The view renders a 512²-texel map (`R16F`) of
  the share of sun light the clouds let through, using the same coverage, wind, band spheres, extinction and floor
  as `SunOcclusion`. The map covers a 4000-unit square centered on the orbit target (the scene's units are taken as
  metres, so a scene in other units gets shadows of a different apparent size) and attenuates only the sun, the
  brightest usable directional light, together with that light's own scene shadow. Inside the square it replaces the
  uniform sun dimming of the clouds (a surface is never dimmed twice); over the outer 10% of the square it fades into,
  and beyond it uses, that uniform dimming. Without clouds, with a sun outside the rendered light budget or no usable
  directional light, or when the map cannot be made (logged once), only the uniform dimming applies. There is no
  setting for any of this and nothing is written back.
- `projects/lib-core/src/main/resources/clouds/templates/` holds fair, overcast and storm examples of `CLOUDS` metas
  (not loaded as built-in references; the creation action snapshots a sky's existing cloud asset).

**Sky lighting.** `additional.lightsScene: true` on a `SKYBOX_PROCEDURAL` asset lights the scene from that sky, as an HDR
sky does: while the scene's sky is enabled and its lighting is built, it replaces the scene's ambient color for every model
and terrain (whether or not the ambient light is enabled), PBR models reflect it, default-shader models take its six axis
colors and terrain its irradiance. Only the literal boolean `true` opts in; absent, `false`, text, numbers and other kinds
of value keep the ambient color, and `skybox_physical` has none. Nothing is written back; there is no UI to toggle it.

- The light comes from Abyssus's own linear single-scattering atmosphere (`atmosphere_linear.frag`, the model of the fixture
  `sky.frag` and of `SkyAmbientEstimate`) driven by the asset's atmosphere parameters, with the shell clouds of its `CLOUDS`
  asset over it, rendered to a 64²-per-face `RGBA16F` cube without tone mapping or a sun disc. It is **not** read back from
  the asset's own shader: a custom `sky.frag` that departs from the physical parameters (another model, tint or
  exposure) is drawn as written but lights the scene as the parameters say.
- The lighting follows the sun and the clouds. It is rebuilt when the sun moves more than 1 degree, every 2 seconds of
  cloud time while a band has wind, and whenever the cloud asset is reloaded, replaced or removed or its visibility changes
  (stationary clouds are not rebuilt for time). A rebuild takes one build step per frame and the new lighting fades in
  over about a second. Until the first build completes, or if it fails, the scene keeps its ambient color; a later failed
  rebuild keeps the last lighting.

**Cloud shadows.** While a procedural sky draws its clouds, they shade terrain and models from the sun, the brightest usable
directional light: dark patches that drift with each band's wind and sit where the sky shows clouds. Nothing is stored for
them and no field turns them off (disable the clouds instead).

- The shadow map covers a square of 4000 world units around the orbit target, 512 texels across (about 8 units a texel).
  **The scene's units are taken as metres**, like the cloud altitudes; a scene in other units gets shadows of a different
  apparent size. Beyond the square (the outer 10% of it fades), the uniform sun dimming of the clouds over the target applies.
- Inside the square the clouds dim the sun through the map only; the uniform dimming is not applied as well. Only the sun is
  shaded; spot and point lights and other directional lights are not. The sun's own scene shadow still applies and the two
  multiply, so a surface in both loses the sun once.
- If the sun is not among the rendered directional lights (it has no usable direction, or brighter lights took the budget),
  no light is shaded by clouds. If the map cannot be built the view falls back to the uniform dimming everywhere and logs the
  reason once.

| Level | Altitudes (m) | Types |
|---|---|---|
| `low` | 300–2500 | `cumulus`, `stratus`, `stratocumulus` |
| `mid` | 2000–7000 | `altocumulus`, `altostratus` |
| `high` | 6000–13000 | `cirrus`, `cirrostratus` |

The runtime and weather snapshot writer share `CloudSettingsReader` through `AssetMetaBinder`. Canonical
metadata uses the lowercase keys and `wind` array above; existing enum-name values, explicit `level` and
`windX`/`windZ` are also read without rewriting source files. Invalid bands are skipped independently.
A newly created weather snapshot is an ordinary `CLOUDS` asset with a fresh UUID, creation timestamp and every
known band default explicit. Unknown native extension members and unchanged numeric text are retained in it;
reading or copying a source does not materialize its omitted defaults in the source document.

Type defaults (`CloudType` in `lib-core`):

| Type | `base` | `top` | `coverage` | `density` | `wind` |
|---|---|---|---|---|---|
| `cumulus` | 800 | 2000 | 0.4 | 0.8 | [4, 1] |
| `stratus` | 400 | 900 | 0.85 | 0.5 | [3, 0.5] |
| `stratocumulus` | 600 | 1600 | 0.6 | 0.7 | [5, 1.5] |
| `altocumulus` | 3000 | 4200 | 0.45 | 0.5 | [10, 2] |
| `altostratus` | 3500 | 5500 | 0.75 | 0.4 | [12, 3] |
| `cirrus` | 8000 | 9500 | 0.35 | 0.15 | [25, 5] |
| `cirrostratus` | 8500 | 10500 | 0.6 | 0.12 | [22, 4] |

**`CLOUDS` is a native asset type.** The Abyssus view lists it with its own icon.

**`SKYBOX_HDR` is a native asset type.** `additional.file` names a single-part OpenEXR image inside the asset
folder. `ExrLoader` in `lib-core` decodes it through TinyEXR off the GL thread. The image must be equirectangular
(width = 2 × height, height at most 4096); it is reduced by block averaging to at most 4096 pixels wide for raster
loading. Scanline and tiled files, including the base level of mipmapped files, are supported; multipart,
ripmapped and subsampled color channels are rejected. RGB channels are found by name (including layer prefixes);
Y or a lone channel can provide grayscale. Alpha is not used. Pixels are kept as RGB half floats: negative/NaN
radiance becomes zero and positive values saturate at 65504. The horizontal centre faces `-Z`, the top row `+Y`.
The fixture is `projects/plugin-abyssus/src/test/testData/project/Untitled/assets/skybox_hdr/`, whose metadata names `sky.exr`.
Radiance `.hdr` decoding and extension-based file discovery are not implemented by the current loader.

### UI assets

A `UI` asset holds named, standalone screens and overlays under `additional.ui`. It is decoded by
`projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/ui/validation/UiCollectionDecoder.kt`, which first requires the
native markers (`AbyssusDocumentFormat`) and then an integral `additional.ui.schemaVersion` of `1`. The UI schema
version is separate from `formatVersion`; additive UI fields do not bump either.

| Field | Meaning |
|---|---|
| `entryScreen` | optional id of the screen a host opens first; the first screen when missing, an error when it names no screen |
| `screens[]` | `id`, `name`, `kind` (`screen` or `overlay`, default `screen`), `referenceSize` (`width`, `height`, default 1280 x 720), `modal` (default `false`), `navigation` (`tab`, the default, or `arrows`: Up / Down move focus) and `root`, a widget |
| widget | `id`, `type`, `layout`, `style`, `properties`, `bindings`, `actions` and, for containers only, `children` |
| widget `type` | `panel`, `row`, `column` (containers), `label`, `image`, `button`, `progressBar`, `slider`, `checkbox`, `textField`, `list` (see *Lists*); any other name is kept and shown as an inert placeholder |
| `layout` | `anchor`, `pivot` (`topLeft`, `top`, `topRight`, `left`, `center`, `right`, `bottomLeft`, `bottom`, `bottomRight`), `x`, `y`, `width`, `height`, `grow`, `padding`, `spacing`, `align` (`start`, `center`, `end`, `stretch`); sizes, `grow`, `padding` and `spacing` are not negative |
| widget `bindings` | property name (`text`, `value`, `maximum`, `checked`, `visible`, `enabled`, `style`; `items` on a list) to a binding name, or inside a list row to `item.<field>` |
| widget `actions` | event (`click`) to `{ "type": ..., "screenId", "assetName", "name" }`; `type` is `openScreen`, `back`, `showOverlay`, `closeOverlay` or `emitAction`; `assetName` is omitted for a screen of the same asset |
| `bindings[]` | `name`, `type` (`number`, `boolean`, `string`, `list`), `access` (`readOnly` default, or `write`), `sample` of that type, optional `min`, `max`, `step` (positive) or `maxLength` |
| `resources[]` | `id`, `kind` (`image`, `bitmapFont`) and a `path` inside the asset folder |
| `styles[]` | `name` and the style values the renderer reads |

Limits, checked before anything is built: depth 64, 10,000 widgets for the whole collection, 8192 pixels per image
side. Ids of screens, widgets, bindings and resources are unique. Errors (version, duplicate id, bad geometry, a
binding of the wrong type, a limit) refuse the whole collection; an unknown widget, a widget binding no `bindings[]`
entry declares and a local screen target that does not exist are warnings. Unknown members stay in the tree and edits
keep key order and number text.

**Lists.** A `list` widget repeats its `template` widget once for every item of the list binding named by `bindings.items`
(at most `properties.maxRows`), laid out like a column (`layout.spacing`, `padding`, `align`), and shows its optional
`empty` widget instead when there are no items. A binding of type `list` declares `fields` (`name`, `type` of `number`,
`boolean` or `string`) and a `sample` array of rows; it is read-only. Widgets inside the template bind to the current
row's fields with `item.<field>` (for example `"text": "item.name"`), and a bound `style` (`"style": "item.look"`) picks a named
style per row. Template and empty widgets have document-unique ids like any widget (ids may not contain `#`, which
marks the runtime copy of a row, `<id>#<row>`). A host publishes the list as rows of typed fields; a field it leaves
out or mistypes shows its type's zero value with a diagnostic, and a list it does not publish shows the sample. The widget
limits count the expanded rows.

A panel, row or column may carry a `click` action like a button, so a whole list row is one click target (it is not in the
focus order, and shows its `hover` / `pressed` styles). An `emitAction` from inside a list row reaches the host as
`<name>#<row>` with the zero-based row index. A text field with `properties.selectOnFocus: true` is replaced by the
first character (or cleared by Backspace) typed after it gains focus.

<!-- ui-minimal-example -->
```json
{
  "format": "abyssus", "formatVersion": 1, "type": "UI", "uuid": "6f1c6f0e-3a54-4c53-9a42-0d7d3f5b7a10",
  "additional": { "ui": {
    "schemaVersion": 1,
    "entryScreen": "main",
    "bindings": [
      { "name": "volume", "type": "number", "access": "write", "sample": 0.5, "min": 0, "max": 1, "step": 0.05 },
      { "name": "health", "type": "number", "sample": 75 },
      { "name": "maxHealth", "type": "number", "sample": 100 }
    ],
    "resources": [ { "id": "logo", "kind": "image", "path": "logo.png" } ],
    "styles": [ { "name": "title", "fontSize": 32 } ],
    "screens": [
      { "id": "main", "name": "Main menu", "root": {
          "id": "root", "type": "column", "layout": { "anchor": "center", "spacing": 12, "align": "center" },
          "children": [
            { "id": "title", "type": "label", "style": "title", "properties": { "text": "Control Line" } },
            { "id": "volume", "type": "slider", "bindings": { "value": "volume" } },
            { "id": "start", "type": "button", "properties": { "text": "Start" }, "actions": { "click": { "type": "emitAction", "name": "startGame" } } },
            { "id": "settings", "type": "button", "properties": { "text": "Settings" }, "actions": { "click": { "type": "showOverlay", "screenId": "hud" } } }
          ] } },
      { "id": "hud", "name": "HUD", "kind": "overlay", "referenceSize": { "width": 1280, "height": 720 }, "root": {
          "id": "hud-root", "type": "panel", "children": [
            { "id": "health-bar", "type": "progressBar", "layout": { "anchor": "topRight", "x": -16, "y": 16, "width": 200, "height": 16 },
              "bindings": { "value": "health", "maximum": "maxHealth" } }
          ] } }
    ]
  } }
}
```

**Registry and reachability.** Creating a UI asset adds its folder name to `uiAssetNames`. The registered names are
roots for *Reachability* below, so a menu no scene mentions is still used. Games open a registered collection by name;
UI is not a renderable entity and no scene field changes. `UiProjectRegistry`
(`projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/project/UiProjectRegistry.kt`) reports a duplicate, missing,
unreadable or wrong-type entry as a problem and returns the admission of the others.

### Reachability ("unused")

`ProjectReader` (`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/dto/ProjectReader.kt`) decides which assets are used:

- **Roots:** a scene reaches asset folders by name through every `assetName` and `shaderKey` in its `ecs`, and its
  `skyboxName`.
- **References:** a reached asset reaches others by `uuid` through terrain `splat*` fields, model `materials`,
  and a reached procedural sky reaches the `CLOUDS` asset its `clouds` names.
  A reached foliage asset reaches its terrain through `additional.terrain` and its models through
  `additional.layers[].models[].asset`, both by folder name; their own references are followed transitively.
  Files named inside `meta.json` are not followed.
- **UI roots and links:** a `uiAssetNames` entry naming a supported `UI` asset is also a root. A reached UI asset reaches
  the UI assets its `openScreen` / `showOverlay` actions name through `assetName`, transitively. Its resource files are
  owned by it and are not assets. A missing, wrong-type, unsupported (refused) or cyclic UI reference is a
  diagnostic (`uiUsageIssues`) and never blocks the other results; an unsupported UI asset is reached but not followed.
- **Unused:** an asset reached by no root of the project is unused.
- **Bundled shaders:** a `shaderKey` naming no folder is a bundled shader and is ignored.

## Plugin-created light entities

New lights use Name, Type, Position and Light components. The type is `LIGHT_DIRECTIONAL` (Directional and Sun)
or `LIGHT_SPOT` (Spot); Sun differs only in its initial color, intensity and rotation. Light values are nested under
`LightComponent.light`: color and intensity are written, while positive `range` is written only when it differs from
100. No editor icon or direction-handle entities are created.
`ecs.archetypes` reuses or appends the exact four-component set; no class identifier table is created.

Spotlight beam settings are native Abyssus fields: `coneAngle` is the full cone width in degrees (finite, greater than
0 and less than 180; default 45), and `edgeSoftness` is the fraction of the angular radius used for the inward fade
(finite, 0 through 1; default 0.2). The properties panel expresses softness as percent. Both keys sit under
`LightComponent.light` for nested components, or directly in an existing flat LightComponent. Missing fields use the
defaults without writing the scene; resetting a default removes its key. Unknown fields and unrelated number text
are preserved. The native format makes no promise of support in another editor and provides no legacy importer.

## Saved ray tracing preferences and instance optics

Native version 1 scenes may contain a root `rayTracing` object. Omitted fields use these defaults:

| Field | Default | Accepted integers |
|---|---:|---:|
| `targetSamplesPerPixel` | 256 | 1–4096 |
| `maxRaysPerFrame` | 33554432 | 1–268435456 |
| `maxReflectionBounces` | 1 | 0–16 |
| `maxRefractionBounces` | 0 | 0–16 |
| `maxInstances` | 4096 | 1–4096 |

A present null, fraction, string or out-of-range value is malformed. Ordinary scene editing remains available;
the raw Properties reader reports the error. Explicit null limits survive typed DTO binding and are rejected by
the render settings codec. Other malformed types may still be coerced during binding or reject the whole scene;
see the documentation audit for this remaining gap.
Editor writes never repair unrelated fields or insert their defaults. Runtime Ray Tracing enable state
is separate and is not saved. Resetting one preference removes only that field, then an empty known container.
Unknown members and unrelated number text remain intact.

Each model entity's `RenderComponent.rayTracingMaterials` is an optional map keyed by unique nonempty model material
IDs. A PBR material entry accepts finite `transmission` from 0 through 1 (default 0) and `ior` from 1 through 3
(default 1.5). Default fields are omitted on reset. These overrides belong to the scene instance and never change
model sources or asset metadata. Unresolved or ambiguous material IDs are preserved without retargeting; ray
conversion refuses unsupported overrides. Geometry and textures stay shared while affected materials are copied.

## Water surfaces

A native scene may carry procedural water under the root `abyssus.waterSurfaces` object, keyed by a stable surface id
(a UUID string). Water is not an ECS entity and not an asset. It is Abyssus-only extension data inside a version 1
document: the loader keeps it, no other tool is promised to. The codec is `WaterCodec`
(`projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/water/WaterCodec.kt`); the edits are
`WaterEdits` in the same package; the scalars and rules are `WaterSettings` / `WaterValidation`
(`projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/water/WaterSettings.kt`).

```json
"abyssus": { "waterSurfaces": { "<id>": {
  "name": "Lake", "preset": "LAKE", "enabled": true,
  "position": { "x": 0, "y": 0, "z": 0 },
  "width": 100, "length": 100,
  "tint": { "r": 0.03, "g": 0.22, "b": 0.25 },
  "clarity": 8,
  "waveAmplitude": 0.08, "waveLength": 4, "waveSpeed": 0.5,
  "foamAmount": 0.5, "foamWidth": 0.6 } } }
```

`position` is the surface centre; `y` is the mean water level. `width` runs along X and `length` along Z (world
units); there is no rotation or scale. `clarity` is the distance over which the tint hides a submerged bottom.
`preset` (`LAKE` or `SEA`) records the starting choice; it is not a live link, and editing a value never changes it.

| Field | Lake | Sea | Rule |
|---|---:|---:|---|
| `width` / `length` | 100 / 100 | 2000 / 2000 | finite, > 0 |
| `tint` r, g, b | 0.03, 0.22, 0.25 | 0.02, 0.12, 0.20 | finite, 0 to 1 |
| `clarity` | 8 | 15 | finite, > 0 |
| `waveAmplitude` | 0.08 | 0.6 | finite, >= 0 |
| `waveLength` | 4 | 24 | finite, > 0 |
| `waveSpeed` | 0.5 | 2 | finite, >= 0 |
| `foamAmount` | 0.5 | 0.8 | finite, 0 to 1 |
| `foamWidth` | 0.6 | 1.5 | finite, > 0 |

Creating a surface writes every value explicitly, so retuned defaults never change a saved surface. When reading, a
missing scalar takes its preset's default without any write. A surface with an unknown or missing `preset`, a value of
the wrong type, or an out-of-range value is invalid: it stays a removable row, is not drawn, and is logged; nothing is
repaired. If `abyssus` or `waterSurfaces` is not an object, no water is read and an add is refused; an incompatible
container is never replaced.

Edits go through `editSceneJson` as one named undoable command and touch only the targeted `abyssus.waterSurfaces.<id>`
members. Unknown members, key order and number text elsewhere survive, and removing the last surface leaves an empty
`waterSurfaces` object so unrelated `abyssus` members are undisturbed. Opening or reading a scene writes nothing, and a
scene without the extension has no water. There is no migration: nothing adds, converts or rewrites the extension.
