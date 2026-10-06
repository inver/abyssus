# File formats

Abyssus owns the JSON format of project `.abss`, scene `.scene` and asset `meta.json` documents.
The plugin reads them with `SceneJson` (`src/main/kotlin/net/nevinsky/abyssus/filetype/SceneJson.kt`),
which keeps key order, `null` members and the exact text of numbers. Writes must keep those, too. The user-facing
description of what the tree shows is in `README.md` ("Abyssus view"); this page is the format reference.

Examples: `src/test/testData/project/Untitled/` (models, terrain, skybox, a camera) and
`src/test/testData/project/Animated/` (an animated model).

## Native document identity

Version 1 requires these root members in each project, scene and asset metadata document:

```json
{ "format": "abyssus", "formatVersion": 1 }
```

`AbyssusDocumentFormat` (`src/main/kotlin/net/nevinsky/abyssus/format/AbyssusDocumentFormat.kt`)
validates document identity and reserved scene fields on the caller's thread, without GL or platform services.
Only the exact string `abyssus` and integral JSON version `1` are supported. Missing/null/foreign markers, string
or fractional versions (including `1.0`), negative versions and future versions are unsupported. Asset metadata's
`version` has its own meaning; it is not the document `formatVersion`.

Unsupported documents are not imported or converted. They remain openable as text, with an explanation from the
plugin; automatic formatting and plugin edits must leave their document text and disk bytes unchanged. Supported
siblings remain available. New documents put the markers first; edits preserve their existing positions, unrelated
keys and exact number text. External model/image formats and the terrain recipe do not receive these markers.

## Project layout

`ProjectLayout` (`src/main/kotlin/net/nevinsky/abyssus/dto/ProjectLayout.kt`) defines it:

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

The plugin reads only:
- `name`: the project label; the file name is used when it is missing.
- `mainCamera`: `position`, `viewPointPosition` (the view direction), `near`, `far` and `fieldOfView`. It is the
  scene view's starting camera (`MainCamera` in `sceneview/SceneRenderParams.kt`).

Other members (`settings`, `activeSceneName`, `selectedCamera`, ...) are ignored. The plugin never writes an `.abss`
(beyond `SceneFormatListener`'s formatting).

## `.scene`

Top level, bound to `Scene` (`core/src/main/kotlin/net/nevinsky/abyssus/core/scene/Scene.kt`):

| Key | Meaning |
|---|---|
| `id`, `name` | Shown as `name (id)`; `name` is changed by Rename Scene |
| `ambientLightEnabled` / `ambientLight` | `color` + `intensity` |
| `fogEnabled` / `fog` | `color`, `density`, `gradient` |
| `skyboxEnabled` / `skyboxName` | `skyboxName` is a `SKYBOX`, `SKYBOX_PROCEDURAL` or `SKYBOX_HDR` asset folder name, or `null` |
| `ecs` | Entities, kept as raw JSON (`JsonNode`) |

**Toggles:** an `<x>Enabled` boolean gates `<x>` (or `<x>Name`). The tree folds it into an eye on the gated row. The
tree shows `skyboxName` as `skybox`, but the key in the file stays `skyboxName`.

### `ecs`

```
ecs: { "<id>": { components: { "<Name>Component": {...}, ... } }, ... }
  (older scenes wrap this map in an `entities` member beside optional `metadata`, which the runtime keeps and writes
  back; they also have an `archetype` per entity and an `archetypes` table, which it neither reads nor carries)
```

The components the plugin reads:

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

**Light defaults** (`runtime/src/main/kotlin/net/nevinsky/abyssus/runtime/ecs/component/LightComponent.kt`). A light that leaves a value out has: `intensity` 1; the
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
These reserved paths are checked by the editor validator. The plain JVM ECS helpers currently lack that guard;
see `docs/reviews/documentation-audit-2026-10-06.md` for the implementation gap.

```json
{ "renderable": { "kind": "asset", "shaderKey": "pbr",
    "asset": { "type": "MODEL", "assetName": "tree" } } }
```

Unknown components and native renderable kinds remain raw and round-trip unchanged. Their payloads are opaque:
a `class` string inside a custom component or a marker's nested payload does not activate class loading or cause
format rejection. Adding or editing a component never creates a Java-class identifier table.

### Game components

A game's own components (declared with `@SceneComponent` in its code) are native extension data keyed by their short
name, next to the built-in ones: `ecs.entities.<id>.components.<ShortName>`. They are written without a class name
and without an identifier table; a scene that holds `ecs.componentIdentifiers` is rejected before any component is
read. The document validator does not look inside them. A program that has not registered a game component keeps it
raw and writes it back unchanged.

A field equal to its declared default is left out, so a plane at its defaults is `"PlaneComponent": {}`. Values are
stored as JSON numbers (a whole decimal as `25`), booleans, strings (text, a choice's name, an asset folder name), an
entity id (`-1` for none), or whole `{x, y, z}` / `{r, g, b, a}` objects. If Jackson cannot bind a component (for example, an unknown enum),
the runtime keeps the whole component raw with a warning. Schema limits are editor validation, not runtime load-time constraints.

### The component schema (`abyssus/components.schema.json`)

The game exports what it registers (`SchemaExportMain`, see `runtime/README.md`) so Abyssus can edit those
components without loading game classes. It is not a native document (no `format` marker); `version` must be `1`.

```json
{ "version": 1,
  "components": [ { "name": "PlaneComponent", "class": "net.example.PlaneComponent", "label": "Plane",
      "fields": [ { "name": "lineLength", "label": "Line length", "type": "decimal", "default": 18,
                    "group": "Lines", "min": 5, "max": 30 },
                  { "name": "kind", "label": "Kind", "type": "choice", "choices": ["TRAINER", "STUNT"], "default": "TRAINER" },
                  { "name": "model", "label": "Model", "type": "asset", "assetType": "MODEL", "default": "" } ] } ] }
```

Field `type`: `decimal`, `whole`, `boolean`, `text`, `choice` (with `choices`), `vector`, `color`, `entity`, `asset`
(with `assetType`). `group`, `min`, `max` and `minExclusive: true` are optional; infinite limits are not written.
The export is stable (registration then declaration order, two-space indent, LF), so the same game gives the same
bytes. An unknown `version` rejects the file; an unknown field type rejects only that component. Other plugins can
contribute files in the same format (`docs/ai/architecture.md`, Extension points); the project's file wins per name.

## Asset `meta.json`

```json
{ "format": "abyssus", "formatVersion": 1, "version": 1, "lastModified": 1663444124794, "uuid": "...", "type": "MODEL", "additional": { ... } }
```

| `type` | `additional` |
|---|---|
| `MODEL` | `file`, `format`, `binary`, `materials` (material asset `uuid`s) |
| `TERRAIN` | `terrainFile`, `size`, `uv`, `splatMap`, `splatBase`, `splatR`, `splatG`, `splatB`, `splatA` (texture asset `uuid`s) |
| `SKYBOX` | `top`, `bottom`, `left`, `right`, `front`, `back` (image files in the folder) |
| `SKYBOX_PROCEDURAL` | `vertex`, `fragment` (GLSL files in the folder); optional atmosphere parameters `planetRadius`, `atmosphereRadius`, `betaRayleigh` (3 numbers), `betaMie`, `heightRayleigh`, `heightMie`, `mieG`, `sunIntensity` (Earth-like defaults) |
| `SKYBOX_HDR` | `file`: the OpenEXR image file inside the asset folder |
| `TEXTURE`, `PIXMAP_TEXTURE`, `MATERIAL`, `SHADER` | Recognized for icons; not drawn by the scene view |

`uuid` can be missing (the fixture's `skybox_default` and `tree` have none).

### Terrain data and the generation recipe

A terrain folder holds `meta.json` and the height file named by `additional.terrainFile` (`terrain.data`): no header,
each height a big-endian 32-bit float, a square grid row after row (z-major), so the resolution is the square root of
the float count (the fixture's is 180). Generating a terrain changes none of this: it writes the same two files.

New terrain metadata is one compact line with native markers first (`format`, `formatVersion`), then `version` 1, `lastModified`, `uuid`, `type` `TERRAIN`,
`additional` with `terrainFile`, `size`, `uv` 1.0 and the six splat fields null; see `TerrainAssetWriter` in
`src/main/kotlin/net/nevinsky/abyssus/terrain/generation`.

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
is one named AC3D part. See `core/src/main/kotlin/net/nevinsky/abyssus/core/flightgear/FlightGearImport.kt`.

**`SKYBOX_PROCEDURAL` is a native asset type.** The scene view
draws it as a fullscreen triangle with the folder's own shaders (single-scattering Rayleigh + Mie, ray-marched per
pixel; the fixture is `assets/skybox_physical`). The plugin supplies these uniforms: `u_invViewProj` (vertex),
`u_sunDir` (unit vector toward the brightest directional light's opposite; a default 45 degree sun without one),
`u_cameraHeight`, `u_planetRadius`, `u_atmosphereRadius`, `u_betaRayleigh`, `u_betaMie`, `u_heightRayleigh`,
`u_heightMie`, `u_mieG`, `u_sunIntensity`. The vertex shader takes `attribute vec2 a_position` (the three corners of
the triangle). A missing file or a compile error skips that sky and logs it.

**`SKYBOX_HDR` is a native asset type.** `additional.file` names a single-part OpenEXR image inside the asset
folder. `ExrLoader` in `core` decodes it through TinyEXR off the GL thread. The image must be equirectangular
(width = 2 × height, height at most 4096); it is reduced by block averaging to at most 4096 pixels wide for raster
loading. Scanline and tiled files, including the base level of mipmapped files, are supported; multipart,
ripmapped and subsampled color channels are rejected. RGB channels are found by name (including layer prefixes);
Y or a lone channel can provide grayscale. Alpha is not used. Pixels are kept as RGB half floats: negative/NaN
radiance becomes zero and positive values saturate at 65504. The horizontal centre faces `-Z`, the top row `+Y`.
The fixture is `src/test/testData/project/Untitled/assets/skybox_hdr/`, whose metadata names `sky.exr`.
Radiance `.hdr` decoding and extension-based file discovery are not implemented by the current loader.

### Reachability ("unused")

`ProjectReader` (`src/main/kotlin/net/nevinsky/abyssus/dto/ProjectReader.kt`) decides which assets are used:

- **Roots:** a scene reaches asset folders by name through every `assetName` and `shaderKey` in its `ecs`, and its
  `skyboxName`.
- **References:** a reached asset reaches others by `uuid` through terrain `splat*` fields and model `materials`.
  Files named inside `meta.json` are not followed.
- **Unused:** an asset reached by no scene of the project is unused.
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
| `maxRaysPerFrame` | 2097152 | 1–67108864 |
| `maxReflectionBounces` | 1 | 0–16 |
| `maxRefractionBounces` | 0 | 0–16 |

A present null, fraction, string or out-of-range value is malformed. Ordinary scene editing remains available;
the raw Properties reader reports the error. This is the required contract; the render path currently binds
through a typed DTO first and may coerce malformed values or reject the whole scene. See the documentation audit.
Editor writes never repair unrelated fields or insert their defaults. Runtime Ray Tracing enable state
is separate and is not saved. Resetting one preference removes only that field, then an empty known container.
Unknown members and unrelated number text remain intact.

Each model entity's `RenderComponent.rayTracingMaterials` is an optional map keyed by unique nonempty model material
IDs. A PBR material entry accepts finite `transmission` from 0 through 1 (default 0) and `ior` from 1 through 3
(default 1.5). Default fields are omitted on reset. These overrides belong to the scene instance and never change
model sources or asset metadata. Unresolved or ambiguous material IDs are preserved without retargeting; ray
conversion refuses unsupported overrides. Geometry and textures stay shared while affected materials are copied.
