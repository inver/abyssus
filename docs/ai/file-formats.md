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
| `skyboxEnabled` / `skyboxName` | `skyboxName` is a `SKYBOX` asset folder name, or `null` |
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
| `LightComponent` | `color`, `intensity`, either directly or under `light` |
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
| `SKYBOX_HDR`, `TEXTURE`, `PIXMAP_TEXTURE`, `MATERIAL`, `SHADER` | Recognized for icons; not drawn by the scene view |

`uuid` can be missing (the fixture's `skybox_default` and `tree` have none).

### Reachability ("unused")

`ProjectAssets` (`src/main/kotlin/net/nevinsky/abyssus/dto/ProjectAssets.kt`) decides which assets are used:

- **Roots:** a scene reaches asset folders by name through every `assetName` and `shaderKey` in its `ecs`, and its
  `skyboxName`.
- **References:** a reached asset reaches others by `uuid` through terrain `splat*` fields and model `materials`.
  Files named inside `meta.json` are not followed.
- **Unused:** an asset reached by no scene of the project is unused.
- **Bundled shaders:** a `shaderKey` naming no folder is a bundled shader and is ignored.
