# Tasks

## 1. Shading enum and tools model

- [x] 1.1 Add `WIREFRAME` to `Shading` in `SceneViewToolsModel.kt` (`LIT`, `UNLIT`, `WIREFRAME`). `ViewDisplay` needs no field change — `shading` already holds the enum.
- [x] 1.2 Add a headless test covering `Shading.entries` includes `WIREFRAME` and `ViewDisplay.drawsShadows` stays `shadows && shading == LIT`.
- Verify with `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewToolsModelTest'`.

## 2. Toolbar shading combo

- [x] 2.1 Extend `SceneToolsToolbar.ShadingAction` so the dropdown lists `WIREFRAME` with a `sceneViewShadingWireframe` label; `shadingLabel` maps it.
- [x] 2.2 Add `SceneViewIcon.WIREFRAME` plus `scene_view_wireframe_icon.svg` and `_dark` twin (24 px grid, 1.8 stroke, 16 px drawn). `IconInventoryTest` fails on a missing or unlisted SVG.
- [x] 2.3 Add `AbyssusBundle.properties` keys: `sceneViewShadingWireframe=Wireframe` and `sceneViewShadingTooltip` (keep, now covers three modes).

## 3. Renderer wireframe pass

- [x] 3.1 In `SceneRenderer.renderContent()` (and the water/reflection path `drawReflected`), switch `GL20.glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)` around the content batch when `display.shading == Shading.WIREFRAME`, restoring `GL_FILL` in a `finally`. Apply to the skybox draw through the same content batch so the cube draws wireframe too.
- [x] 3.2 Leave grid, `LineBatch` markers, selection box and gizmos unchanged (already line geometry).
- Verify through a GL test reading `GL_POLYGON_MODE` with `-Dabyssus.glTests=true`.

## 4. Panel wiring

- [x] 4.1 Confirm `SceneViewPanel.syncControls()` already pushes `tools.display → renderer.display` (no code change; add a test assertion that `renderer.display.shading` follows `tools.display.shading`).
- [x] 4.2 Add `SceneViewToolsModelTest` / panel-level cases: selecting Wireframe in the model reaches `renderer.display.shading`.

## 5. Docs

- [x] 5.1 Update `sceneview/README.md` (ViewDisplay / Shading row), README user-facing feature list (keeping the `<!-- Plugin description -->` markers), CHANGELOG Unreleased. Verify with `scripts/check-docs.sh`.

## 6. Verification

- [x] 6.1 Run `./gradlew :plugin-abyssus:check` and `scripts/check-docs.sh`; record any unrelated failures without changing their scope.
- [ ] 6.2 Manual runIde check on a fixture copy: open a scene, switch shading to Wireframe, verify models/terrain/water show edges, switch back to Lit, verify filled; reopen view, verify back at Lit (not saved).
