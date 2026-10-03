# Tasks

## 1. Serialization and change coordination

- [x] 1.1 Inspect the supported Mundus upstream light serialization or a Mundus-produced spotlight scene; record the evidence and mapping in design Decision 1. If native fields are absent or cannot be verified, use the authorized coneAngle/edgeSoftness extension. Verify the decision names keys, units, defaults, nested/flat layout and compatibility limits.
- [x] 1.2 Reconcile add-light-entities proposal/design exclusions with this change, retaining its range and creation scope. Verify both changes with openspec validate --strict and confirm no contradictory cone-control requirement remains.
- [x] 1.3 Extend LightData/LightCodec and SceneContent with angle and softness while preserving unknown fields and both representations. Add ComponentCodecsTest and SceneRenderParamsTest cases for missing defaults, saved values and unknown keys; verify ./gradlew :test --tests '*ComponentCodecsTest' and ./gradlew :test --tests '*SceneRenderParamsTest'.
- [x] 1.4 Document the exact saved fields, defaults and extension limitations in docs/ai/file-formats.md; verify scripts/check-docs.sh.

## 2. Beam edits and properties

- [x] 2.1 Add finite-value validation and targeted spotlight field edits through editSceneJson; test boundary rejection, default omission, no-op edits, preservation of number text/key order and Undo in ComponentEditorTest and SceneComponentEditsTest. Verify ./gradlew :test --tests '*ComponentEditorTest' and ./gradlew :test --tests '*SceneComponentEditsTest'.
- [x] 2.2 Add spotlight-only angle/softness controls, percent conversion and localized labels. Add EntityPropertiesPanelTest cases for visibility by light kind, values, invalid input and external refresh; verify ./gradlew :test --tests '*EntityPropertiesPanelTest'.
- [ ] 2.3 Document the controls and persistence in README.md while retaining Plugin description markers; verify scripts/check-docs.sh and runIde checks 3 and 4 below.

## 3. Correct spotlight illumination

- [x] 3.1 Implement plain SpotCone math and separate spot sources with entity identity and deterministic selection under the shared five-local-light limit. Add SpotConeTest and SceneLightingTest cases for cone boundaries, zero/100 softness, rotation, range, invalid values and stable ties; verify ./gradlew :test --tests '*SpotConeTest' and ./gradlew :test --tests '*SceneLightingTest'.
- [x] 3.2 Extend default/PBR and terrain lighting with cone attenuation and consistent range cutoff. Add SceneRenderGlTest pixel checks for inside/outside cone and smooth edge on both models and terrain; verify ./gradlew :test --tests '*SceneRenderGlTest' -Dabyssus.glTests=true.
- [x] 3.3 Update sceneview/README.md with spot-light behavior and supported limits; verify scripts/check-docs.sh.

## 4. Shadow math and model depth pass

- [x] 4.1 Implement pure deterministic tile allocation, point-face mapping/radial depth and projection fitting with texel snapping. Add ShadowLayoutTest and ShadowProjectionTest covering budgets, reorder/removal, six axes and face seams, off-screen casters and degenerate bounds; verify ./gradlew :test --tests '*ShadowLayoutTest' and ./gradlew :test --tests '*ShadowProjectionTest'.
- [x] 4.2 Add reusable gdx-model depth shader/provider for custom indices, posed skinning and alpha-test cutouts, plus atlas shadow attributes. Add headless ShadowAttributeTest and GL ModelShadowGlTest cases including a mesh beyond 65535 vertices; verify ./gradlew :gdx-model:test and ./gradlew :gdx-model:test -Dabyssus.glTests=true.
- [x] 4.3 Add independent per-light atlas sampling to default and PBR shaders while preserving legacy library shadowMap usage and keeping ambient/IBL/emissive terms independent. Verify ModelShadowGlTest with two lights, zero atlas shadows and HDR environment lighting using ./gradlew :gdx-model:test -Dabyssus.glTests=true.
- [x] 4.4 Document reusable shadow attributes, depth rendering and material limits in gdx-model/README.md; verify documented paths exist with rg --files gdx-model/src/main.

## 5. Scene and terrain shadow integration

- [x] 5.1 Build per-context atlas resources and capability/failure fallback; inspect sampler counts against actual GL limits and reserve a nonconflicting unit. Verify ShadowResourcesGlTest allocation, failed framebuffer fallback and state restoration with ./gradlew :test --tests '*ShadowResourcesGlTest' -Dabyssus.glTests=true.
- [x] 5.2 Reorder SceneRenderer to update geometry once, render bounded depth tiles, restore main rendering state and draw color from the same geometry snapshot. Include terrain depth rendering and atlas receiving; verify SceneRenderGlTest model-to-terrain, terrain-to-model and per-light isolation cases using ./gradlew :test --tests '*SceneRenderGlTest' -Dabyssus.glTests=true.
- [ ] 5.3 Wire safe disposal/context abandonment, removal and shadow fallback without stale resources; verify ShadowResourcesGlTest rebuild/removal cases with ./gradlew :test --tests '*ShadowResourcesGlTest' -Dabyssus.glTests=true and runIde checks 2 and 5 below.
- [x] 5.4 Document frame order, threading, resource ownership, budgets and material limits in sceneview/README.md and affected docs/ai/architecture.md sections; verify scripts/check-docs.sh.

## 6. Integration verification

- [ ] 6.1 runIde check 1 on a copy of Untitled: add test point and spot lights and an occluder; verify model/terrain casting and receiving, all six point directions, spot cone coverage, independent second-light filling and retained HDR/ambient lighting. Inspect seams, bias and atlas borders. Record results; leave unchecked if unavailable.
- [ ] 6.2 runIde check 2: move/rotate light and model with gizmos, Undo and animate a model; confirm shadows match previews and poses with no one-frame lag. Record results.
- [ ] 6.3 runIde check 3: set spotlight angle to 60 degrees and softness to 25 percent; save, close and reopen; verify controls and beam match, text preserves unrelated numbers and each Undo restores the prior scene text. Record results.
- [ ] 6.4 runIde check 4: reject invalid angles/softness, test zero and 100-percent softness, reset defaults and inspect omitted fields. If Mundus is available, open/re-save a copy and document whether it understands or retains extension fields; report unavailable compatibility verification explicitly.
- [ ] 6.5 runIde check 5: exceed each shadow budget, delete lights, switch scenes, resize, minimize and hide/show the view; verify continued lighting, no stale shadows or invalid-context failures, and responsive input. Record frame timings on a representative scene and reduce proposed budget/resolution if needed without weakening the behavior contract.
- [ ] 6.6 Validate the completed change with openspec validate add-scene-shadows --strict, then run ./gradlew check and scripts/check-docs.sh; report unrelated failures without silently fixing them.
