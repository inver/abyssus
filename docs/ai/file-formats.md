# File formats

All three are Mundus JSON. The plugin reads them with `SceneJson` (`src/main/kotlin/net/nevinsky/abyssus/filetype/SceneJson.kt`),
which keeps key order, `null` members and the exact text of numbers. Writes must keep those, too. The user-facing
description of what the tree shows is in `README.md` ("Abyssus view"); this page is the format reference.

Examples: `src/test/testData/project/Untitled/` (models, terrain, skybox, a camera) and
`src/test/testData/project/Animated/` (an animated model).

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

Top level, bound to `SceneDto` (`src/main/kotlin/net/nevinsky/abyssus/scene/SceneDto.kt`):

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
ecs:
  entities: { "<id>": { archetype, components: { "<Name>Component": {...}, ... } } }
  archetypes, componentIdentifiers, metadata    (Mundus bookkeeping, carried unchanged)
```

The components the plugin reads:

| Component | Used for |
|---|---|
| `NameComponent.name` | Entity label (the id when missing) |
| `TypeComponent.type` | `CAMERA`, `LIGHT_*` (kind of light), ... |
| `PositionComponent` | `localPosition`, `localRotation` (quaternion `x y z w`) and `localScale`; plus `lookAtId` for cameras |
| `RenderComponent.renderable` | `asset.assetName` + `asset.type` (`MODEL` / `TERRAIN`) and `shaderKey`. Editor-only renderables have a `class` but no `asset` |
| `CameraComponent.camera` | `position`, `viewPointPosition` (the view direction), `near`, `far`, `fieldOfView` |
| `LightComponent` | `color`, `intensity`, `range` (positive reach, default 100 omitted), either directly or under `light` |
| `Point2PointPositionComponent` | `entity1Id` / `entity2Id` |
| `ParentComponent` | Read by the ECS loader; the scene view ignores parents and uses `local*` as world values |

**Defaults are omitted:** like Mundus, a missing field is its default (position 0, identity rotation, scale 1). An
empty `PositionComponent: {}` is valid. Writers add fields when they change them (`SceneTransformWriter`,
`PositionCodec`).

**Cameras have two positions:** a camera's position is stored both in `PositionComponent.localPosition` and in
`CameraComponent.camera.position`. Gizmo moves write both.

## Asset `meta.json`

```json
{ "version": 1, "lastModified": 1663444124794, "uuid": "...", "type": "MODEL", "additional": { ... } }
```

| `type` | `additional` |
|---|---|
| `MODEL` | `file`, `format`, `binary`, `materials` (material asset `uuid`s) |
| `TERRAIN` | `terrainFile`, `size`, `uv`, `splatMap`, `splatBase`, `splatR`, `splatG`, `splatB`, `splatA` (texture asset `uuid`s) |
| `SKYBOX` | `top`, `bottom`, `left`, `right`, `front`, `back` (image files in the folder) |
| `SKYBOX_PROCEDURAL` | `vertex`, `fragment` (GLSL files in the folder); optional atmosphere parameters `planetRadius`, `atmosphereRadius`, `betaRayleigh` (3 numbers), `betaMie`, `heightRayleigh`, `heightMie`, `mieG`, `sunIntensity` (Earth-like defaults) |
| `SKYBOX_HDR` | Any; a text value naming a `.hdr` file in the folder is used, otherwise the folder's `.hdr` is found by extension |
| `TEXTURE`, `PIXMAP_TEXTURE`, `MATERIAL`, `SHADER` | Recognized for icons; not drawn by the scene view |

`uuid` can be missing (the fixture's `skybox_default` and `tree` have none).

**`SKYBOX_PROCEDURAL` is a plugin-only type.** Mundus does not define it and will not load such an asset. The scene view
draws it as a fullscreen triangle with the folder's own shaders (single-scattering Rayleigh + Mie, ray-marched per
pixel; the fixture is `assets/skybox_physical`). The plugin supplies these uniforms: `u_invViewProj` (vertex),
`u_sunDir` (unit vector toward the brightest directional light's opposite; a default 45 degree sun without one),
`u_cameraHeight`, `u_planetRadius`, `u_atmosphereRadius`, `u_betaRayleigh`, `u_betaMie`, `u_heightRayleigh`,
`u_heightMie`, `u_mieG`, `u_sunIntensity`. The vertex shader takes `attribute vec2 a_position` (the three corners of
the triangle). A missing file or a compile error skips that sky and logs it.

**`SKYBOX_HDR` is a plugin-only type** in the same sense: the plugin does not assume Mundus loads it. The folder holds a
Radiance `.hdr` image: a `.hdr` named by any `additional` text value, else the only `.hdr`, else the first by name
(logged). Supported: header `#?RADIANCE` or `#?RGBE`, `FORMAT=32-bit_rle_rgbe` or none, resolution line
`-Y <height> +X <width>` only, flat or new-style run-length scanlines; `EXPOSURE` and other header lines are ignored.
The image must be equirectangular (width = 2 x height, height at most 4096); one wider than 4096 is halved while
reading. The horizontal centre faces `-Z`, the top row `+Y`. The fixture is `assets/skybox_hdr` (64 x 32, written by
the test helper `HdrFixtures`).

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
100. No editor icon or direction-handle entities are created. Mundus compatibility is not required for this structure.
`ecs.archetypes` reuses or appends the exact four-component set, and missing `componentIdentifiers` entries are added.

Spotlight beam settings are Abyssus extensions: `coneAngle` is the full cone width in degrees (finite, greater than
0 and less than 180; default 45), and `edgeSoftness` is the fraction of the angular radius used for the inward fade
(finite, 0 through 1; default 0.2). The properties panel expresses softness as percent. Both keys sit under
`LightComponent.light` for nested components, or directly in an existing flat LightComponent. Missing fields use the
defaults without writing the scene; resetting a default removes its key. Unknown fields and unrelated number text
are preserved. The inspected Mundus light implementation is transient and its saved spotlight fixture contains an
empty LightComponent, so native equivalents were not established. Mundus rendering and retention of these extensions
are unverified; saving through another editor may lose them.
