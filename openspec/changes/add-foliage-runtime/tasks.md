# Tasks

Commands use the real Gradle paths (`:lib-runtime`, `:lib-physics`, `:lib-core`, `:lib-core-editor`, `:plugin-abyssus`,
`:app-game-control-line`). GL tests need `-Dabyssus.glTests=true` on a machine with a display.

## 1. Preconditions

- [ ] 1.1 Confirm that `add-terrain-foliage` is applied and archived, so `openspec/specs/terrain-foliage-authoring`
  and `scene-foliage-rendering` exist and `FoliageLoader` / `FoliageDrawable` are on `main`. Verify:
  `npx @fission-ai/openspec list --specs` lists both capabilities, and `./gradlew :lib-core:test` passes.

## 2. Shared copy transform in `lib-core`

- [ ] 2.1 Extract the per-copy math from `FoliageDrawable` into the pure `FoliageCopyTransforms` (design decision 2),
  with no behavior change. Verify:
  - the existing `FoliageChunkMatricesTest` passes unchanged;
  - a new `FoliageCopyTransformsTest` covers a copy at terrain-local (10, 20) on the `Untitled` terrain under entity
    `1`, with alignment 0 and 1, and a non-uniform entity scale.
- [ ] 2.2 Bind the layer `collider` in `FoliageMeta` (BOX, SPHERE, CAPSULE, with `ColliderComponent`'s defaults).
  Verify with `AssetMetaLoaderTest` cases: a capsule binds with its radius and half height; an omitted `collider` is
  null; an unknown shape fails only that layer's collider and keeps the layer.

## 3. Collider authoring (`lib-core-editor`, `plugin-abyssus`)

- [ ] 3.1 Add collider validation to `FoliageSettings` (finite, sizes > 0, OBJECT only) and `collider` writes to
  `FoliageMetaEdits`, including adding, changing and removing the member. Verify, in `./gradlew :lib-core-editor:test`:
  - `FoliageSettingsTest`: radius 0 and a NaN half extent are rejected, and a collider on DETAIL is rejected;
  - `FoliageMetaEditsTest`: setting a capsule writes `{"shape": "CAPSULE", "radius": 0.4, "halfHeight": 3}`, setting
    None removes the member, and other text is unchanged;
  - `FoliageFingerprintTest`: a collider change keeps the fingerprint.
- [ ] 3.2 Add the collider fields to the foliage panel: a shape combo (None, Box, Sphere, Capsule) and the matching
  fields, on OBJECT layers only, with every string in `AbyssusBundle`. Verify with `FoliagePanelTest` cases covering
  each scenario of the "Layer colliders" requirement, including that Apply leaves `foliage.data` byte-for-byte
  unchanged.

## 4. Runtime component and listing in `lib-runtime`

- [ ] 4.1 Add `FoliageComponent(assetName)` and add it to `BUILT_IN_COMPONENTS`. Its deserializer checks folders and
  its serializer writes back the carried node (design decision 1). Verify with `FoliageComponentTest` in
  `./gradlew :lib-runtime:test`: the round trip keeps `"note": "x"` and the number text; an unknown folder gives one
  warning and writes back unchanged; a missing `assetName` loads as null.
- [ ] 4.2 Add `SceneFoliage.of(context)`, which returns `FoliageEntry(entity, foliageName, terrainName)`. Verify with
  `SceneFoliageTest`: a test-scene copy of `Main Scene` with the component on entity `1` lists one entry; a model
  entity with the component gives one warning and no entry.
- [ ] 4.3 Add a headless load test, `FoliageRuntimeLoadTest`, using `RuntimeSceneLoader` plus the `lib-core` asset
  loaders with no IntelliJ classes on the classpath. Verify:
  - the Foliage fixture from `add-terrain-foliage` loads with the copy count of its `foliage.data`;
  - a stale fingerprint logs one warning and keeps the copies;
  - a deleted bake loads empty with one warning;
  - `formatVersion` 2 is skipped with one warning.
- [ ] 4.4 Update `projects/lib-runtime/README.md` (built-in components, `SceneFoliage`, bake handling). Verify that
  `scripts/check-docs.sh` passes.

## 5. Physics in `lib-physics`

- [ ] 5.1 Add `PhysicsAssets.foliage(name)`, which reads the meta, the bake and the terrain heights without GL, cached.
  Verify with `PhysicsAssetsTest` cases: the fixture foliage reads with its copies; a missing bake returns empty with a
  warning.
- [ ] 5.2 Add `ShapeFactory.foliageChunk`, a static compound shape from copies placed by `FoliageCopyTransforms` plus
  the collider offset. Verify, in `./gradlew :lib-physics:test` (Jolt natives), with `ShapeFactoryTest` cases:
  - a 3-copy chunk has 3 sub-shapes at the expected positions within 1e-4;
  - a capsule under a non-uniform scale takes the largest axis, with one warning.
- [ ] 5.3 Build foliage bodies in `PhysicsWorld`:
  - one static body per (layer, chunk);
  - validation before any shape, with warnings naming the foliage and layer;
  - DETAIL colliders ignored, with a warning;
  - the 200,000 cap in entity-then-layer order;
  - removal on `removeEntity` and the engine listener, and release in `close()`.

  Verify with a test project `src/test/testData/project/PhysicsFoliage` (a copy of `Physics` with one foliage copy
  under the dropped box) and `PhysicsFoliageTest`, with one case per scenario of "Foliage colliders are static bodies",
  "Foliage collision is bounded and validated" and "Foliage bodies are released". Use a synthetic bake of 300,000
  copies for the cap case.
- [ ] 5.4 Confirm that Play picks up foliage. Verify with a `PlayHostTest` case: the play host built from
  `PhysicsFoliage`'s scene text sends a box pose that rests on the capsule after 5 s of simulated time. Then do manual
  check 8.3.
- [ ] 5.5 Update `projects/lib-physics/README.md` (foliage colliders, cap, release). Verify that
  `scripts/check-docs.sh` passes, and that `./gradlew :lib-physics:checkNoSingletons` and
  `./gradlew :plugin-abyssus-physics:checkNoJolt` still pass.

## 6. Control Line migration

- [ ] 6.1 Check whether `texture_airfield_site_splat` has a channel that separates dirt and paths from grass. Record the
  answer in the tool's KDoc and in `project/environment/README.md`. Verify by reading the splat channels in a scratch
  test, then deleting it.
- [ ] 6.2 Write `tools/FieldFoliage.kt` and the `generateFieldFoliage` `JavaExec` task (tools classpath plus
  `lib-core-editor`). It produces the grass tuft model and texture with `source.json`, both foliage assets with their
  per-species layers, stamped masks, the clear-area and site-square zeroing, the grass mask, and the bakes. It then
  rewrites `Field.scene` through `HeadlessEditing` (design decision 7). Verify that running it twice gives byte-equal
  output (`git status` is clean after the second run), and that `./gradlew :app-game-control-line:test` compiles.
- [ ] 6.3 Run the tool and commit its output. Verify with new and updated `AirfieldEnvironmentTest` cases:
  - "Vegetation is foliage": no tree or bush entities remain, entities `0` and `300` carry the component, and each
    species' count is within 25% of 52, 40, 33, 48 and 42;
  - "Grass stays off the asphalt": no grass copy lies within 25 m of the pilot;
  - "Foliage keeps the flying area clear": every OBJECT copy's footprint and crown is at least 28 m from the pilot
    (model bounds × copy transform), and no field layer has a collider;
  - every new asset is native and records its source;
  - `linesAndPlanesAreUnchanged` still passes.
- [ ] 6.4 Add `FieldScene.foliage`, `FoliageLoader` in `fieldAssets`, `FoliageDrawable` drawing in `FieldRenderer`,
  and OBJECT casters in `FieldShadows` (design decision 6). Verify:
  - a `FieldSceneTest` case lists the two entries;
  - `FieldRendererGlTest`, under `-Dabyssus.glTests=true`, draws a frame from the pilot camera with grass and trees,
    with no GL errors and the frame time logged;
  - `./gradlew :app-game-control-line:test` passes headless.
- [ ] 6.5 Update `project/environment/README.md`: the migration as an estimate of the previous layout, the grass
  asset's generation instructions, and the path-mask answer from 6.1. Update the game README's code table and the
  `generateFieldFoliage` command. Verify that `scripts/check-docs.sh` passes.

## 7. Specs and docs

- [ ] 7.1 Update `docs/ai/architecture.md` (the foliage data flow into the runtime, physics and games) and
  `docs/ai/file-formats.md` (the layer `collider`, and `FoliageComponent` read by the runtime). Verify that
  `scripts/check-docs.sh` passes.

## 8. Manual checks

- [ ] 8.1 `./gradlew :app-game-control-line:run`: fly the Trainer. Check that the trees stand where the old forest did
  (denser to the northeast and east), that grass covers the field outside the pad, that the trees cast shadows, and
  that the frame rate stays smooth from the pilot camera.
- [ ] 8.2 Open a copy of `project/ControlLine` in `./gradlew :plugin-abyssus-physics:runIde`. Check that both foliage
  assets show in the scene view and that the bakes are not reported as out of date.
- [ ] 8.3 In the same sandbox, give a copy of the site's broadleaf layer a capsule collider, Apply, then Play with a
  dynamic box dropped onto a tree. Check that the box rests on the tree, then undo the collider.

## 9. Integration

- [ ] 9.1 Run `./gradlew check -Pabyssus.requireShaders=true` and `scripts/check-docs.sh`. Verify that both pass,
  including `checkNoSingletons` for `lib-core`, `lib-runtime`, `lib-physics` and `lib-core-editor`, `checkNoJolt`,
  `checkNoRunCatching` and `checkPackageCycles`. Report failures outside the change instead of fixing them silently.

## Workflow follow-up

- Archive with `/openspec-archive-change` after review. This syncs `foliage-runtime` and the deltas to
  `terrain-foliage-authoring`, `physics-simulation`, `scene-ecs-components` and `control-line-field-environment`.
