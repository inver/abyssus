# Proposal

## Why

An Abyssus project keeps its models, terrains and skyboxes in an `assets` folder beside the
`.abss` file, but the Abyssus view only shows the project's scenes. Users cannot see which assets
a project owns, and cannot tell which of them no scene references any more and could be deleted.
Separately, JSON handling is done with Gson, which the plugin only gets as an incidental
transitive dependency of the IntelliJ Platform; Jackson is preferred for reading and writing the
asset files.

## What Changes

- Show a project's assets inside its `.abss` node as an `assets` property: one entry per
  sub-folder of the `assets` folder beside the `.abss` file, in name order, each with the asset's
  type (`MODEL`, `TERRAIN`, `SKYBOX`, ...) read from the folder's `meta.json`.
- Mark an asset as **unused** when it is not reachable from any scene of the project. A scene
  references an asset by folder name through its ECS data (`assetName` of a render component,
  `shaderKey` of a renderable) or, for skyboxes, through `skyboxName`. A `shaderKey` that matches
  no folder in the project's `assets` names a bundled editor shader and is ignored. Used assets then pull in the assets they reference through
  their own `meta.json` by `uuid` (terrain `splat*` textures, model `materials`), transitively.
  Used assets are shown normally; unused ones are labelled and grayed.
- Replace Gson with Jackson (`ObjectMapper` / `JsonNode`) for all JSON deserialization, and for
  the serialization used when toggling `<x>Enabled` or renaming a scene. **No behavior change**
  to what the view shows or what is written to scene files.

## Capabilities

### New Capabilities
- `abyssus-project-assets`: listing a project's assets in the Abyssus view and flagging the ones
  no scene uses.

### Modified Capabilities

None. The Jackson migration changes no observable behavior. (`abyssus-project-view` is still an
in-flight change and has no main spec yet; the assets requirements are kept in their own
capability so they do not depend on its archive order.)

## Impact

- Code: `dto/AssetReader.kt` (read `assets` incl. each asset's `uuid` and reference fields, compute usage transitively, Jackson parsing), `dto/DtoProperty.kt`
  (`JsonNode` instead of `JsonElement`, an `unused` flag on entries), `scene/SceneDto.kt`,
  `sceneview/SceneRenderParams.kt`, `projectView/EnabledToggle.kt`, `projectView/AbyssusNodes.kt`
  (unused rendering).
- Dependencies: `build.gradle.kts` adds `com.fasterxml.jackson.core:jackson-databind` and removes
  every Gson import. Both are checked for bundling conflicts with the platform's own copies.
- Messages: new keys in `AbyssusBundle.properties` for the `assets` label and the unused marker.
- Tests: reader/node tests over `src/test/testData/project/Untitled` (7 asset folders, 4 used by
  `Main Scene`), plus existing reader, toggle and render-param tests updated for Jackson.
