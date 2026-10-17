# Proposal

## Why

The Scene view shades models, terrain, foliage, skybox and water as solid Lit or Unlit surfaces. There is no way to inspect the wireframe topology of what is drawn, which is useful when placing models, checking terrain tessellation or debugging silhouettes next to the rendered look.

## What Changes

- Scene views gain a third shading mode, **Wireframe**, alongside Lit and Unlit in the toolbar's shading dropdown. It is per view, never saved to `.abss`, `.scene` or any asset `meta.json`.
- In Wireframe mode every drawable the renderer issues — models, terrain meshes, foliage OBJECT layers, the skybox cube and water — is drawn as edges only (`glPolygonMode(GL_LINE)` around the content pass).
- Lights, shadows, fog, the grid, markers, the selection box and gizmos are unchanged (they are already line or marker geometry).
- No document fields are read or written; the Abyssus native format does not change. Out of scope: persisting wireframe per scene, combining Wireframe with Lit, and wireframe inside Ray Tracing (raster only).

## Capabilities

### New Capabilities

- `scene-view-wireframe`: The Scene view shading dropdown gains Wireframe; all scene geometry draws as edges in that mode; the choice is per view and never saved.

### Modified Capabilities

None.

## Impact

- Plugin `sceneview` package: `SceneViewToolsModel.Shading`, `ViewDisplay`, `SceneRenderer` content pass, `SceneToolsToolbar` shading combo, `SceneViewIcon.WIREFRAME` icon + SVGs, `AbyssusBundle.properties` labels.
- No changes to `lib-core`, `lib-core-editor`, `lib-gdx`, `lib-render`, `lib-physics`, `lib-raytracing` or the game.
- Docs: `sceneview/README.md`, README feature list, CHANGELOG Unreleased.
