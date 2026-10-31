# Design

## Context

`SceneLoader.parse` parses the scene with `NativeJsonTree`, binds `SceneDto`, then re-attaches the raw `ecs` node.
`EcsLoader` walks `ecs.entities.<id>.components.<Name>`, strips `enabled`, binds each value with a reader, and carries
what it cannot bind as raw JSON (`EcsLoadingWarns.carried`). `lib-core-editor` and the plugin read the same raw node
(`sceneDocumentFromEcs`, `PanelBuilders`, `ProjectReader`, `PhysicsOverlayGeometry`) and `EcsWriter` writes it back.
The working tree already has `SceneDto.ecs: Map<Long, EntityDto>` and `EntityDto(components: List<Component>)`, which
does not compile against `SceneLoader`/`RuntimeSceneLoader`.

## Goals / Non-Goals

**Goals:** one typed read path for entities; unknown components still round-trip; no hand-written JSON <-> DTO code.

**Non-Goals:** typing the `abyssus` extension block; changing `.abss` or asset meta; new editor features.

## Decisions

1. **File shape.** `ecs: { "<id>": { "components": { "<Name>": {...} }, "flags": <int> } }`. Component entries keep the
   existing name-keyed object, so component data and component-level handling do not change.
   `flags` is an integer bit set. Assumption (confirm): bit 0 (`1`) = entity disabled, replacing the entity-level
   `enabled: false`; other bits are reserved and preserved as read. Missing `flags` means `0` and is never inserted.
   A per-component `enabled` stays as today (last key inside the component object, stripped before binding).
2. **Binding.** `EntityDto.components` gets a `@JsonDeserialize` `ComponentMapDeserializer` (a `StdDeserializer` in
   `lib-core`) that turns the name-keyed object into `List<Component>`: it resolves each name through `ComponentRegistry`,
   binds the value into the class, and falls back to `RawComponent`. Jackson annotation binding, not a manual tree reader.
   The registry reaches it through one new `JsonProcessor` injectable; the ask-first rule applies, confirm before coding.
3. **`RawComponent`.** A `Component` holding `name`, the raw `data` node (via `NativeJsonTree`, exact numbers) and the
   reason; `EcsLoader` skips it and records the warning, so `EcsLoadingWarns.carried` is replaced by the entries themselves.
4. **Enabled.** `HeaderComponent.entityEnabled` comes from `EntityDto.flags`; component-level `enabled` is unchanged
   (`stripEnabled` and the reserved-field warning stay).
5. **Writes keep the tree.** Token-exact number text and key order cannot come from re-serializing typed objects, so
   `editSceneJson`, `DocumentTextEditor`, `SceneDocument` and `ComponentEditor` keep editing the `SceneJson` tree, adapted
   to the new entity shape (entity enable toggling writes `flags` instead of `enabled`). `EcsWriter` is retired: new entries are written
   as name-keyed members built by the component codecs. Only reads move to the typed map; `SceneDto.ecs` is never the write source.
6. **Reads.** `SceneContent`/`SceneRenderParams` take `Map<Long, EntityDto>`; the plugin overlay and `ProjectReader`
   use component classes, not key lookups. Ray `rayTracingMaterials` stay editor-only: read from the document tree, not the DTO.
7. **Fixtures.** A one-off Kotlin script under `scripts/` converts every `.scene`; tests that build `{"entities":...}` strings
   are rewritten to the new shape. The converter turns entity `enabled: false` into `flags: 1`, unwraps `entities`, drops `archetypes`/`metadata`. Old files are not imported (project rule).
8. **No `SourceBackedComponent`.** Typed components no longer write themselves back from a stored source node: the
   interface, `adoptSource`/`sourceTree`, and the `is SourceBackedComponent` branches in `EcsLoader` and `JsonProcessor`
   (`bindComponent`, `componentTree`) are deleted. `FoliageComponent` becomes a plain component with `assetName` plus a
   `Map<String, JsonNode>` of unmodeled members (`@JsonAnySetter`/`@JsonAnyGetter`), so they survive a typed round trip;
   it writes `assetName` only when non-null. In-place document edits are unaffected because they patch the tree (decision 5).
   Trade-off: member order from a typed write is `assetName` first, then extras; number text of extras follows the
   component reader's exact-decimal setting.

## Risks / Trade-offs

- **Number text.** Typed components lose `2.50`-style text if they were ever re-serialized; point 5 avoids this, but any
  new code that writes from the DTO would break the rule. Mitigation: a test that edits a fixture and diffs text.
- **Format version.** The `ecs` wrapper and `flags` are incompatible with existing version 1 files. Open question for the owner: keep `formatVersion: 1`
  (no installed base, per the project rules) or bump to 2 and teach `AbyssusDocumentFormat`. This plan keeps `1`.
- **Blast radius.** About 40 test files build ecs JSON strings; the change is mechanical but large.
- **Order.** `lib-core` must compile first; other modules stay red until their read paths move.
- **Threading.** All pure JVM, runs wherever scenes load today (read action / background); no libGDX calls added.
- **Other open changes** (`add-control-line-pbr-rendering`, `add-scene-model-mesh-selection`) whose deltas show the old
  entity shape must be amended in task 5.2.

## Addendum: entity header

`TypeComponent` is removed. The `ecs` entry carries `type`, `flags` and `disabledComponents` (component keys) beside
`components`; the engine holds them with the entity id in one `HeaderComponent` (replacing `IdComponent` and `EnabledState`).
A component's own `enabled: false` is gone, so no class field name is reserved. `RawComponent` stores its payload as JSON
text (`json`) and whether its entity disables it. `EntityType` lives in `dto` so `format` can validate `type` without a
package cycle.
