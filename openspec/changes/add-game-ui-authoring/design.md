# Design

## Context

See proposal.md for motivation. Control Line's GameUi constructs Scene2D Stage/Table widgets and forwards input to GameFlow. There is no corresponding UI asset type or editor. Its active F2B changes continue to own its hardcoded screens; this change does not migrate them. The existing scene-play-mode contract runs simulation in a separate process and explicitly omits game screens. Menu preview is a separate editor mode and does not extend that protocol.

AssetMeta currently enumerates supported types, while ProjectDto has no UI registry. SceneDocumentWriter.editSceneJson already delegates to DocumentTextEditor for lossless native edits. AssetFileCommand and its transaction tests provide a staging/rollback pattern for resource creation. Existing project usage analysis assumes scenes are its only roots; the delta preserves its existing scenarios and adds UI roots.

## Goals / Non-Goals

**Goals:** Share the runtime UI model and renderer across hosts; isolate authoring edits from preview interaction; make layout, binding validation, navigation and JSON transforms independently testable.

**Non-Goals:** No editor dependency in the runtime, game class loading in the IDE, new scene ECS representation for UI, automatic code import, or production Control Line screen migration. No generic expression evaluator or executable skin scripts.

## Decisions

### Native UI assets rather than a new document extension

Store a UI collection in assets/<name>/meta.json with native markers, UUID, type UI, and additional.ui. The UI block has integral schemaVersion 1, entryScreen, screens, bindings, resources and styles. A collection can contain multiple screens and overlays. Project uiAssetNames is an optional ordered list of UI asset folder names, defaulting to empty without inserting it on read. Creation registers the asset; explicit register/unregister controls support existing assets. Runtime games can open a registered collection by name, without making UI a renderable entity.

Each screen has a stable id, name, kind (screen or overlay), referenceSize (default 1280x720), modal (overlay default false), and root widget. Widgets have stable id, type, properties, layout, style, bindings, actions and ordered children. Only containers have children. Duplicate screen/widget IDs are errors; unknown widget kinds remain raw and appear as inert placeholders. Cross-asset navigation targets are {assetName, screenId}; local targets omit assetName.

Use AbyssusDocumentFormat admission before UI decoding, then validate UI schema, finite dimensions, binding types, references and hierarchy. Unsupported native/UI versions prevent the entire collection from running; localized bad references disable affected controls. Unknown extension payloads stay in the original SceneJson tree. Additive UI fields do not bump native formatVersion. A separate UI schema version permits later UI-specific evolution.

Alternative: embed UI into .scene ECS data. Rejected because screen reuse, overlay composition and screen navigation have different lifecycles from scene entities. A new extension would unnecessarily duplicate native admission and asset discovery.

### Module boundaries and ownership

- lib-core/ui/model and ui/validation hold immutable decoded definitions and validation, depending on neither GL nor the IDE. MetaType and AssetMetaBinder gain UI support.
- lib-core/ui/layout holds pure layout resolution and measured-size contracts. ui/state holds pure navigation, binding descriptors and write-result handling. ui/render builds Scene2D actors from those definitions, delegates geometry to the shared layout engine, and owns host-provided resources.
- lib-core-editor/ui holds UiDocumentEdits, UiSelection, UiHitTest, UiLayoutEdits and UiPreviewState. These classes operate on data, rectangles and SceneJson only: no Swing, AWT, GL or platform classes.
- plugin-abyssus/ui owns IDE actions, editor provider for UI meta.json, widget tree, properties adapters, document watches and guarded canvas integration. Reuse the existing scene rendering composition for the backdrop, not a second runtime implementation.
- Hosts inject UI resource access, value snapshots, write callbacks and action sinks. Standalone hosts depend on lib-core only. A test/demo entry point in the game module supplies actual changing values and accepted/rejected writes without modifying its normal launch flow. It uses its own fixture project under the module's demo/test resources, never `project/ControlLine`, whose scene tests assert on.

Maintain acyclic packages and constructor wiring. Keep user-facing editor text in injected EditorMessages or AbyssusBundle as appropriate. Diagnostics carry codes and data; hosts choose presentation.

Alternative: Swing preview with Scene2D runtime. Rejected because layout, text metrics and input behavior would diverge.

### Layout and styling

Use logical coordinates with top-left origin in document/layout data; the Scene2D adapter converts coordinates. Fit the screen reference rectangle uniformly into the target viewport and center it with letterboxing. Anchor calculations operate inside that reference rectangle. Preview scales the target viewport to fit the editor panel without changing its logical dimensions.

Panel children use nine-point anchors, matching pivot, x/y offsets and explicit or preferred width/height. Rows/columns allocate measured preferred sizes plus fixed sizes, padding and spacing, distributing remaining main-axis space by nonnegative grow weights; cross-axis alignment supports start/center/end/stretch. Parent automatic layout controls child positions; dragging there reorders children, while numeric offsets are unavailable. Reparenting converts the widget to the destination's layout policy as one explicit undoable operation. Children clip to container bounds; hit testing uses the same resolved rectangles. Negative sizes and nonfinite inputs are rejected; insufficient space clips with a diagnostic instead of silently changing authored sizes.

Styles resolve bundled type defaults, a named style, widget overrides, then state overrides. State priority is disabled, pressed, hover, focused, normal; focus also retains a visible focus indicator. Resources map IDs to image files (PNG/JPEG) or bitmap fonts (.fnt plus texture pages) copied beneath the owning UI asset. Version one supports bitmap fonts; runtime TTF generation and a theme editor are deferred. Resource paths must resolve within the owning asset after normalization/symlink resolution. No network resources or class instantiation from resource files. Imported files use generated collision-safe names and staging.

### Binding and action contract

Binding declarations map names to number, boolean or string types, access (readOnly/write), sample value and optional numeric min/max/step or string maxLength. Widget bindings map supported properties to binding names: text, value, maximum, checked, visible and enabled as applicable. Text has a literal prefix/suffix and number precision option, not expressions. Progress uses value and maximum bindings, clamps the fraction for display and diagnoses maximum <= 0.

The host publishes an immutable value snapshot once per update. Missing/wrong-type values show a diagnostic and use the sample fallback; controls with invalid writable targets are disabled. Sliders commit once on release or keyboard step, checkboxes on toggle, text fields on Enter or focus loss. A write carries binding name, typed value and origin widget. The synchronous host result is Accepted(authoritativeValue) or Rejected(reason); long-running host work must be scheduled by the host, not block UI rendering. Host-published changes update controls with listeners suppressed. Accepted normalized values update all consumers; rejected values restore the last authoritative value. The host must still validate authorization and game constraints.

Navigation supports openScreen, back, showOverlay, closeOverlay and emitAction. A bounded navigation history restores focus; popping at its root is a no-op. Topmost modal overlays block all lower input; nonmodal overlays consume only handled interactive-widget events. UI never pauses gameplay itself. Tab/Shift-Tab follows visible enabled tree order; Enter/Space activates focused buttons. Escape closes the topmost overlay, otherwise goes back, and does nothing at the root; this applies to both runtime and preview, a runtime host may handle Escape first, and it never invokes physics Play.

### Authoring, writes and preview

Existing metadata and project edits use editSceneJson and pure DocumentTextEditor transformations; capture document revision at gesture start and refuse stale commits. Dragging uses transient geometry and commits once on release. Undo restores the exact previous text. Metadata visual and text editors share document events; invalid unsaved input shows diagnostics and suspends interaction.

New asset/resource creation needs filesystem staging because editSceneJson cannot create binary resources or an absent file. Use the existing asset transaction pattern for creation/import only, with an enclosing undoable command coordinating project registration through editSceneJson. Validate all paths and writable targets before applying, roll back partial writes, and do not overwrite existing files. Existing metadata always stays on the lossless document edit path. Undo of creation removes the created asset and registration; redo restores them. File/metadata conflict checks prevent overwriting subsequent edits.

Preview receives immutable decoded definitions and a session-local sample binding store; authored samples change only through explicit authoring properties. Interactive writes never touch documents. Actions append to a bounded log. Reset restores authored samples, selected starting screen and empty history/log. Definition changes reset preview transient state; texture/font changes reload affected resources. Invalid input suspends interaction until a valid revision arrives. Backdrop selection and preview resolution are editor-local preferences, not scene fields. A backdrop consumes admitted current scene text and normal asset rendering, without simulation or game modules.

### Threads and lifecycle

In the plugin, capture documents and selection on EDT; background read actions resolve VFS snapshots, parsing and validation off EDT, with I/O dispatched only around I/O. Publish results on EDT only if their revision remains current. Widget tree/property changes and undoable writes run on EDT. All libGDX actor/resource/input/render work runs inside GdxRuntime.withContext on the AWT render thread, and GL only when GuardedGLCanvas.glSafe. Background resource preparation is CPU-only. Runtime hosts call their UI instance on their own GL thread, with explicit ownership rather than global services.

One editor owns its UI session, backdrop and resources. Closing/rebuilding releases actors, textures, fonts, watches, input focus and timers on their owner thread; late background results are discarded. Shared bundled resources use explicit leases. Preview window visibility/zero dimensions stop rendering safely. Cancellation propagates through runCatchingKeepingCancellation.

## Risks / Trade-offs

- Shared renderer integration is substantial -> build pure model/layout/state first, prove host parity in a dedicated demo, then integrate editor interaction.
- Overlay backdrop may duplicate scene GPU allocations -> one owned backdrop per editor, lazy loading, explicit disposal and no simulation.
- Reference-size fit introduces letterboxing -> predictable first-version behavior; viewport dimensions are visible in preview.
- Bitmap fonts require prebuilt resources -> bundled font works immediately, supported import formats are documented.
- Malformed user-authored trees or expensive resources can stall UI -> enforce depth/node/image limits with diagnostics before allocation; limits are depth 64, 10,000 widgets and 8192 pixels per image dimension (normative, in game-ui-authoring).
- Project registration and file creation span multiple documents -> transaction tests cover failure, undo/redo and concurrent edits before UI integration.
- Other changes touch Control Line screens -> leave those screens intact; the separate demo avoids competing ownership.

## Migration Plan

Deliver additive runtime support before enabling the editor's UI creation action. Existing projects with no uiAssetNames remain unchanged and existing Play behavior stays intact. No automatic migration of menus or scene files occurs. Rollback disables the new editor/runtime feature while preserving UI asset files as unknown metadata for later reopening; users must keep a version supporting UI to run authored menus. Do not remove data on downgrade.
