# Proposal

## Why

The Scene view shades models, terrain, foliage, skybox and water as solid Lit or Unlit surfaces. There is no way to inspect the wireframe topology of what is drawn, which is useful when placing models, checking terrain tessellation or debugging silhouettes next to the rendered look.

## What Changes

- Scene views gain a third shading mode, **Wireframe**, alongside Lit and Unlit in the toolbar's shading dropdown. It is per view, never saved to `.abss`, `.scene` or any asset `meta.json`.
- In Wireframe mode every scene-geometry draw the renderer issues — models, terrain meshes, foliage OBJECT layers, the skybox cube and water surfaces — is drawn as edges only (`GL20.glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)` around each geometry pass: skybox, terrain, the content batch including foliage, and water surfaces, restored to `GL_FILL` in a `finally`). While Ray Tracing presents a frame, the ray image is shown as usual and wireframe does not apply.
- Lights, shadows, fog, the grid, markers, the selection box and gizmos are unchanged (they are already line or marker geometry). Shadows stay off while shading is not Lit: the shadow pass runs only with Lit.
- No document fields are read or written; the Abyssus native format does not change. Out of scope: persisting wireframe per scene, combining Wireframe with Lit, and wireframe inside Ray Tracing (raster only).

## Capabilities

### New Capabilities

- `scene-view-wireframe`: The Scene view shading dropdown gains Wireframe; all scene geometry draws as edges in that mode; the choice is per view and never saved.

### Modified Capabilities

None.

## Impact

- Plugin `sceneview` package: `SceneViewToolsModel.Shading` (gains `WIREFRAME`), `ViewDisplay`, `SceneRenderer.render()`/`renderContent()` geometry passes (skybox draw, terrain pass, content batch incl. foliage, water surfaces), `SceneToolsToolbar.ShadingAction` shading combo (+`shadingLabel`), `SceneViewIcon.WIREFRAME` icon + `scene_view_wireframe_icon.svg`/`_dark`, `AbyssusBundle.properties` labels (`sceneViewShadingWireframe`).
- No changes to `lib-core`, `lib-core-editor`, `lib-gdx`, `lib-render`, `lib-physics`, `lib-raytracing` or the game.
- Docs: `sceneview/README.md`, README feature list, CHANGELOG Unreleased.
