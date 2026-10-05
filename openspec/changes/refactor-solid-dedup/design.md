# Design

## Context

See `proposal.md` for the findings F1–F10. This is a behavior-preserving refactor, so the existing tests are the
safety net; each phase must leave `./gradlew check` green before the next starts.

Constraints:
- **Writes:** every scene or `meta.json` edit still goes through `editSceneJson`. No new write path.
- **Modules:** `core`, `runtime` and `physics` stay plain JVM libraries wired by constructors, with no `object` or
  `companion object` (`checkNoSingletons`). `gdx-model` is untouched. `physics-plugin` may not name Jolt.
- **Threading:** libGDX only inside `GdxRuntime.withContext` on the canvas's AWT thread. Splitting `SceneViewPanel`
  moves code between classes on that same thread; no code moves threads.
- **Format:** documents keep `format: "abyssus"` / `formatVersion: 1`; validation by `AbyssusDocumentFormat` stays
  before binding.

## Goals / Non-Goals

**Goals:** remove each duplicated block in the findings table; make adding a `MetaType`, a `FieldType` or a play frame a
one-place change; bring classes above ~450 lines with several reasons to change under that size by responsibility.

**Non-Goals:** behavior changes, `gdx-model`, performance work, a new DI framework, patterns without a duplicate to
remove.

## Decisions

### D1. One meta binding in `core` (F1)

Add `AssetMetaBinder` (constructor takes `JsonProcessor`) in `core/.../assets/`. It owns the `MetaType` → `additional`
class table (a `Map<MetaType, Class<*>>` built in the constructor, not a `when`) and `bind(name, tree): AssetMeta<Any>`.
`AssetMetaLoader` and the plugin's unsaved-meta loader both take it. `AssetMetaReader` keeps format validation and the
`MetaDocument` for listing and the panel; it does not duplicate the table.
*Why not a `MetaType` field:* `MetaType` is a plain enum without settings classes; putting classes on it would make it
depend on every asset package. The binder is injected, so a test can register a fake kind.
*Pure code:* the binder has no IntelliJ or GL imports and is tested headless.

### D2. Small helpers instead of new rules (F2)

`parseUuidOrNull(String?)` in `core` (uses `runCatchingKeepingCancellation`, or a plain `try` on
`IllegalArgumentException`, which is not cancellation) replaces the four direct calls. `checkNoRunCatching` moves
into the shared Gradle script (D7) and covers all JVM modules.

### D3. Strategy per `FieldType` (F3)

`FieldTypeHandler` in `runtime/schema/` with `encode`, `decode`, `defaultFor`, `fromJava` and `parts` (how many editable
sub-values and their names). `FieldTypes` is a map built in a constructor and passed to `SchemaJson`,
`ComponentSchemaReader` and the plugin's editor. Where a handler is needed for the panel (`FieldKind`), the plugin
adds a display mapping beside it, not inside `runtime` (runtime has no UI notion).
*Why a strategy and not an enum method:* `FieldType` is part of the exported schema; adding behavior to it would put
codec dependencies on the schema model. The registry keeps the enum a pure tag.
*Risk:* the `when` blocks are exhaustive today, so the compiler catches a missing type; the registry must keep that
guarantee. Handler registration is checked by a test that every `FieldType.entries` has a handler.

### D4. Split by reason to change (F4, F5)

- `ComponentEditor.kt` → `ComponentCodec.kt` (contract and `RuntimeCodec`), `BuiltInComponentKinds.kt` (field tables),
  `SchemaCodec.kt`, `ComponentEditor.kt` (create/update/remove). Public names stay; this is file moves plus visibility.
- `SceneViewPanel` → `SceneToolbar` (buttons, `syncControls`, camera choices), `SceneInputForwarder` (mouse/keys,
  play forwarding) and the panel as the canvas host and wiring. The panel remains the `SceneView`. Extracted classes
  take collaborators by constructor and hold no Swing state shared through fields: callbacks are lambdas.
  Testable logic (camera choices, button enablement) goes to Swing-free functions.
- `AssetPropertiesPanel` → row builders and `Thumbnail` out; the panel keeps layout and selection.

### D5. Named play frames (F6)

`FrameType(id)` enum; encode and decode look the id up once. Wire ids stay identical, so the protocol version does not
change. A round-trip test over every `FrameType` pins that.

### D6. Facade groups instead of one holder (F8)

`AbyssusCore` exposes `documents`, `assets`, `terrain` and `ray` groups (small classes constructed in it).
Entry points (actions, tool window factory, editor provider) take the group they use. The `service<AbyssusCore>()`
rule from `conventions.md` does not change: it is allowed only at those entry points. This reduces what each entry
point can reach, so tests can pass a group instead of the whole core. Fully qualified names inside the class are
replaced with imports.

### D7. One Gradle convention (F7)

Add `gradle/checks.gradle.kts` (applied with `apply(from = ...)`) that registers `checkNoSingletons(sources, excludes)`
and `checkNoRunCatching(sources)`. Modules call it with their paths. Task names stay the same, so the commands in
`AGENTS.md` and CI do not change. `checkNoJolt` stays in `physics-plugin`.

### D8. Verify before extracting (F9)

Task 9.1 diffs `MetalRayBackend` and `VulkanRayBackend` session code and records the result in this design. Extract a
shared base or helper only for blocks that are identical in behavior; otherwise leave them and say so. Same for
`PhysicsWorld`: split only along seams covered by separate tests (`PhysicsWorldTest`).

## Risks / Trade-offs

- **Churn conflicts** with open changes touching `AssetLoading`, `SceneViewPanel` and ray files. Mitigation: phases are
  independent commits; phases 4 and 5 go last and may be rebased.
- **Over-abstraction.** Strategy for field types adds indirection. Kept only because it removes five switches; if the
  prototype in task 3.1 is not smaller than what it replaces, stop and keep the switches.
- **Hidden behavior in copies.** The two `parse` copies could differ in subtle ways (the unsaved one drops errors
  silently). Task 1.1 writes a test first that pins both behaviors on the Untitled fixture and on a malformed meta.
- **Docs and the OpenSpec specs** must change in the same change that makes them wrong; each phase's last task does so.

## Open Questions

- Should the extracted `SceneToolbar` live in `sceneview/` or a new `sceneview/toolbar/` package? Default: new package.
- Is `AssetMetaEditor` meant to move to `core` as `conventions.md` says? Default here: fix the doc, do not move code.
