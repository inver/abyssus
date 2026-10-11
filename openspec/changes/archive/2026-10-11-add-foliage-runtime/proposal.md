# Proposal

## Why

Terrain foliage is authored and rendered in the editor, but its scene component is still raw data in the game engine.
Control Line still uses 215 separate tree and bush entities. This change completes the authored-content path into games
and the separate physics play host, using the existing instanced renderer and committed bakes.

## What Changes

- Add `FoliageComponent(assetName)` to `lib-core`'s built-in component registry. Keep native unknown component members,
  key order and number text on round trip, and retain unresolved names with a warning.
- List terrain-bound foliage from a loaded engine without file access or GL. Load through constructor-wired core
  asset readers and draw committed bake copies using `FoliageDrawable` and the existing `FoliageMatrices`.
- Games and physics never scatter or write assets. A readable stale bake keeps its baked positions, yaw and scale;
  current terrain heights and layer/model metadata supply what ABFO v1 does not store. Warn once per loaded asset
  revision. Missing/corrupt bakes or unsupported documents skip the affected foliage; a missing model skips its copies.
- Add optional BOX, SPHERE and CAPSULE colliders to OBJECT layer metadata. DETAIL layers remain non-colliding.
  Collider-only Apply changes only metadata, preserves unknown nested data, and is one undoable document edit even
  when the existing bake is stale or missing. Collider fields do not enter the fingerprint.
- Build static compound collision per layer/chunk in `lib-physics`, on the world-owning game/play-host thread.
  Validate declarations and effective copy transforms before native calls, isolate failed layers, enforce a 200,000
  colliding-copy cap plus the world's available body capacity, report contacts against the owning terrain entity,
  and release bodies and native shapes on removal, failed construction and close.
- Migrate Control Line's 215 vegetation entities to `foliage_airfield_site` and `foliage_airfield_outer`, attached to
  terrain entities `0` and `300`. Preserve each species' count within 25%, northeast/east distribution, the 28 m clear
  flying area, flight settings and the rule that bundled scenery adds no colliders.
- Add generated, redistributable grass outside the asphalt and hard/worn ground, with provenance. A rerunnable tool
  uses a recorded source-placement manifest rather than reading vegetation entities that disappear after migration.
  It has bounded density search, explicit output paths and byte-equal reruns. Rendering lands before content migration.
- Integrate foliage into Control Line's color and sun-depth passes, with OBJECT-only shadow casters. Each terrain
  placement owns independent instance/culling state; the asset storage owns shared asset drawables and model data.
  Measure the existing field before choosing grass density and a documented performance budget.

### Native documents and compatibility

Read supported `.abss`, `.scene` and asset `meta.json` only after `AbyssusDocumentFormat` admission.
Read/write `ecs.entities.<id>.components.FoliageComponent.assetName` (using the scene's existing ECS shape) and
`additional.layers[].collider` in foliage metadata. Collider members are `shape`, `halfExtents.{x,y,z}`, `radius`,
`halfHeight` and `offset.{x,y,z}`. Defaults match `ColliderComponent`; an omitted collider means no collision.
Unknown shapes and malformed colliders do not make a renderable layer disappear. No format version, mask or ABFO v1
layout change is proposed. Source lexical preservation requires a shared plain-JVM JSON reader, not a reverse
`lib-core` dependency on `lib-core-editor`.

Out of scope: a generic Ashley foliage render system; runtime rebaking; dynamic/kinematic foliage owners;
DETAIL/mesh/hull colliders; wind, LOD and impostors; ray-traced foliage; backend migration; new Control Line scenery
collision; IDE/build-baseline upgrades. Unsupported shear, reflection or singular copy transforms are refused for
collision rather than approximated silently; spheres/capsules retain the documented largest-axis scale approximation.

## Capabilities

### New Capabilities

- `foliage-runtime`: engine listing, committed-bake rendering, runtime failure isolation and diagnostics without the IDE.

### Modified Capabilities

- `terrain-foliage-authoring`: optional OBJECT colliders and metadata-only Apply/Undo independent of bake availability.
- `physics-simulation`: static foliage collision, transform validation, bounded construction, contacts and resource release.
- `scene-ecs-components`: built-in foliage component with native source preservation and unresolved-reference retention.
- `control-line-field-environment`: foliage vegetation, grass, deterministic migration and preserved clear-area/flight rules.

## Impact

- `projects/lib-core`: component registry/binding, shared lossless JSON reading, pure runtime foliage listing/read policy,
  neutral collider metadata and existing `FoliageMatrices` reuse; no IntelliJ, Jolt or editor-core dependencies.
- `projects/lib-core-editor`: retain `EcsWriter` ownership, source-preserving component serialization integration,
  collider validation/diffs and a collider-only edit path; retain `SceneJson`'s public behavior when sharing its reader.
- `projects/lib-physics`: CPU-only foliage assets, compound shape construction and owned body/contact records.
- `projects/plugin-abyssus`: collider controls, live-buffer revalidation and document-aware metadata Undo. Physics/Jolt
  remain in the bundled external play host; existing `checkNoJolt` and zip checks remain mandatory.
- `projects/app-game-control-line`: independent per-placement foliage rendering, sun casters, migration tool and source
  manifest, generated grass assets, field content/tests and environment documentation. Tools-only editor-core dependency.

`add-terrain-foliage` is archived at `openspec/changes/archive/2026-10-10-add-terrain-foliage`; its seven manual checks
remain unverified. Its existing classes/specs are the prerequisite; archive status alone is not runtime or UI evidence.
