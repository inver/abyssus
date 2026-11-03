# Spec Delta

## Purpose

Defines executable UI acceptance coverage for the plugin's implemented authoring workflows so creators can rely on editing, persistence, undo, validation and recovery working together. Existing feature specifications remain authoritative for product behavior; these requirements define the regression evidence the suite must provide.

## ADDED Requirements

### Requirement: Full-cycle acceptance evidence

The suite SHALL exercise user-facing actions in a real editor against disposable native projects. For each supported mutation it SHALL verify visible state, current document, saved files, Undo, Redo and save/reopen. Creation cycles SHALL end through supported removal, unregister or creation Undo. Rejected, cancelled and unchanged operations SHALL preserve document text and add no edit. Scenario IDs SHALL map to executable cases and results; unavailable checks SHALL remain explicitly unverified.

#### Scenario: COV-01 Complete a component cycle
- **WHEN** a modeled component is added, edited, undone, redone, saved, reopened and removed in a copy of Untitled
- **THEN** the panel, tree and saved component payload agree at every stage, Undo restores the preceding text, and removal preserves unrelated components and entities

#### Scenario: COV-02 Retain a demonstrated regression
- **WHEN** a required scenario exposes a confirmed product defect
- **THEN** the defect is fixed with the reproduction retained, and the scenario is rerun successfully without weakening its expected behavior or disabling the test

#### Scenario: COV-03 Report actual coverage
- **WHEN** a run finishes with a missing, skipped or environment-blocked required case
- **THEN** its coverage record identifies the unverified scenario and reason instead of reporting that feature as fully covered

### Requirement: Continuous creator journey coverage

The suite SHALL run representative creator journeys without resetting fixtures between stages. It SHALL compare UI, document and saved-file state at mutation and persistence checkpoints. Undo of removal SHALL restore edited state and identity. Reopen SHALL mean editor reopen in the same session unless project reopen is explicit; project reopen SHALL test persistence without requiring history to survive. Removal SHALL use only supported actions.

#### Scenario: JRN-01 Model instance from placement to removal
- **WHEN** a user adds model asset tree in a copy of Untitled, renames and moves its new instance, undoes/redoes those edits, saves/reopens the scene editor, removes the instance, undoes/redoes removal and saves/reopens again
- **THEN** each checkpoint shows the expected name, pose and identity; Undo of removal restores the edited instance rather than its creation defaults; final reopen confirms absence while the tree asset and unrelated scene content remain unchanged

#### Scenario: JRN-02 Edited component survives removal undo
- **WHEN** a user adds an eligible Light component to Model 0, changes two fields, attempts an invalid value, undoes/redoes the valid field edits, saves/reopens, removes the component, then undoes/redoes removal
- **THEN** rejection does not occupy an undo step, each valid edit restores its preceding state, Undo of removal brings back both edited field values rather than defaults, and Redo removes only that component

#### Scenario: JRN-03 Shared model instances remain independent
- **WHEN** a user adds two instances of a prepared PBR model, changes one instance's transmission and IOR, undoes/redoes its edits, saves/reopens, then removes that instance and undoes/redoes its removal
- **THEN** the other instance and shared model files stay unchanged throughout; Undo of removal restores the chosen instance's identity and optical overrides, and the final removed state remains absent after save/reopen

#### Scenario: JRN-04 Two water surfaces retain independent edits
- **WHEN** a user adds Lake and Sea, sets different names/levels/extents, undoes/redoes their edits, saves/reopens, then removes Lake and undoes/redoes its removal before saving/reopening again
- **THEN** both retain their own identities and edited settings until removal; Undo restores Lake with its edited values; Sea and unrelated extension data remain unchanged by Lake's removal history; final reopen contains Sea and no Lake

#### Scenario: JRN-05 Generated terrain remains reusable after instance removal
- **WHEN** a user creates a terrain from a preview, undoes/redoes creation, places an instance, changes generation settings and regenerates, undoes/redoes regeneration, saves/reopens, then removes the scene instance and undoes/redoes that removal
- **THEN** creation Redo preserves asset identity/files, regeneration history restores the corresponding heights/recipe, removal Undo restores the instance's edited state, and final save/reopen leaves the generated terrain asset reusable with no removed instance

#### Scenario: JRN-06 Foliage layer removal preserves the remaining composition
- **WHEN** a user creates foliage with two layers, edits/applies their settings, paints a mask, undoes/redoes accepted changes, saves/reopens, then removes one layer and applies, undoes/redoes that Apply and saves/reopens again
- **THEN** each applied metadata/mask/bake checkpoint agrees with the viewport; layer removal leaves the other layer's settings and identity intact; Undo restores the removed layer's edited settings and prior bake; final reopen matches the accepted single-layer composition

#### Scenario: JRN-07 Imported model remains reusable after its instance is removed
- **WHEN** a user imports a local model with placement, undoes/redoes that combined operation, renames/moves the instance, undoes/redoes its edits, saves/reopens, then removes the instance and undoes/redoes removal before final save/reopen
- **THEN** import history removes/restores the folder and original placement together; removal history restores the later edited instance rather than its initial placement; final state retains the reusable imported asset, excludes the instance and leaves source files unchanged

#### Scenario: JRN-08 Menu button retains its authored behavior through history
- **WHEN** a user creates a menus UI asset in a copy of Untitled, adds/styles/reparents a button and assigns an action, undoes/redoes those edits, previews its action, saves/reopens, then deletes the button and undoes/redoes deletion before saving/reopening again
- **THEN** preview creates no authored edit; Undo of deletion restores the button's ID, parent/order, layout, style and action; Redo removes it without changing other widgets/resources; final metadata and uiAssetNames remain consistent

#### Scenario: JRN-09 Clear a dependency before removing its target
- **WHEN** a user creates two entities, makes one the other's parent, edits/saves/reopens, attempts removal of the referenced parent, clears the parent reference, undoes/redoes clearing, then removes the target and undoes/redoes removal before final save/reopen
- **THEN** the blocked attempt writes nothing and adds no undo step; clearing history restores/removes the link correctly; Undo of target removal restores the target's edited state while the already-cleared reference remains clear; final state has no dangling reference or unintended change to the surviving entity

#### Scenario: JRN-10 Continue authoring after project reopen
- **WHEN** a user creates/edits scene content and a menus UI asset, undoes/redoes accepted edits, saves and closes/reopens the project, verifies those saved states, then performs new edits/removals with Undo/Redo and saves/closes/reopens the project again
- **THEN** both documents and their asset references preserve saved identities/values across each project reopen; new-session history restores only the appropriate preceding states; final removed entities/widgets do not reappear from stale buffers and unrelated assets/registration remain consistent
- **AND** the check does not require undo history from the previous project session to remain available

### Requirement: Component catalog and reference coverage

The suite SHALL cover every component kind actually offered by the plugin and its distinct field editor types, through both tree and Properties entry points where provided. It SHALL verify defaults, duplicate exclusion, supported reference rules and unknown/game-component read-only behavior. It SHALL NOT require new component editors merely to make a coverage case executable.

#### Scenario: CMP-01 Add from both editing surfaces
- **WHEN** an eligible component is added to Model 0 through the tree and, in an independent cycle, through Properties
- **THEN** each surface offers only applicable missing kinds, creates the expected default payload and refreshes the other surface

#### Scenario: CMP-02 Choose a render asset
- **WHEN** Render is added to an eligible entity with MODEL asset tree selected
- **THEN** its native renderable uses kind asset and names MODEL/tree, and unrelated entity data is unchanged

#### Scenario: CMP-03 Remove from both editing surfaces
- **WHEN** a removable component is removed through its tree menu or Properties control
- **THEN** the component disappears from both surfaces and the file, and Undo/Redo restore/remove the same payload

#### Scenario: CMP-04 Reject references and cycles
- **WHEN** an entity-reference field names absent entity 99, an entity is made its own parent, or a parent chain would loop
- **THEN** the change is refused with a field-specific explanation and no document edit

#### Scenario: CMP-05 Clear an optional reference
- **WHEN** a supported optional entity reference is cleared using its offered None value
- **THEN** the stored representation follows native default omission and unrelated component data remains intact

#### Scenario: CMP-06 Preserve read-only components
- **WHEN** an unknown or game component without an editable schema is selected and another modeled component is edited beside it
- **THEN** the unknown/game payload remains readable and byte-preserved and has no unsupported add/remove/field editor

#### Scenario: CMP-07 Respect registered field constraints
- **WHEN** a registered editable kind is available and its choice, number, Boolean, asset or entity-reference field is changed
- **THEN** valid values round-trip and declared limits, types and reference choices reject invalid values without affecting other fields

### Requirement: Shared field commit coverage

The suite SHALL verify Enter and focus-loss commits for text fields, immediate choice/checkbox commits, rejected-value reversion and error recovery across asset and component Properties. It SHALL verify that one accepted user change produces one undoable edit even when focus subsequently leaves the editor.

#### Scenario: FLD-01 Commit by Enter
- **WHEN** a component name or editable asset number is changed and Enter is pressed
- **THEN** the new value is persisted once and one Undo restores the prior text

#### Scenario: FLD-02 Commit by leaving the field
- **WHEN** a valid component or asset text value is typed and focus moves to another control
- **THEN** the value is saved without requiring Enter and the displayed value agrees with the file

#### Scenario: FLD-03 Do not double-commit
- **WHEN** Enter commits an edit and focus then leaves that field
- **THEN** there is no second identical edit and one Undo reverses the user's change

#### Scenario: FLD-04 Reject and recover
- **WHEN** invalid text is submitted, followed by a valid replacement
- **THEN** rejection shows the reason and restores the stored value without writing, and the valid replacement saves and clears the error

#### Scenario: FLD-05 No-op and selection changes
- **WHEN** a field is submitted with its existing value or a choice/checkbox rebuilds after selection changes
- **THEN** no edit is created merely by submission, rebuilding or selecting content

### Requirement: Entity lifecycle coverage

The suite SHALL cover ecs creation and the model, terrain and light creation choices through each supported tree/toolbar entry point. It SHALL verify placement, selected identity, edit/reopen/remove, reference rejection and focus-aware Undo/Redo according to scene-component-editing, scene-asset-placement and scene-light-creation.

#### Scenario: ENT-01 Create from ecs
- **WHEN** Camera or Render is added through ecs in a fresh copy of Untitled's Main Scene.scene
- **THEN** the new ID is one above the current maximum, currently 11, its name is Entity 11, existing entities 0 through 10 are unchanged and the new row is selected

#### Scenario: ENT-02 Add models and terrain
- **WHEN** tree or the fixture terrain asset is added through a scene row and, independently, through the Scene View toolbar
- **THEN** the saved asset identity, type and shader match the choice and placement follows the entry point's origin/orbit-target and terrain-centering rules

#### Scenario: ENT-03 Add each light choice
- **WHEN** each offered light preset is created through the tree and toolbar
- **THEN** its type, defaults and placement follow the selected preset and the new entity can be edited and reopened

#### Scenario: ENT-04 Remove an instance
- **WHEN** a newly created asset instance is removed
- **THEN** its row, properties and scene selection are cleared appropriately, the source asset remains intact and the saved scene reopens without that instance

#### Scenario: ENT-05 Guard referenced removal
- **WHEN** entity 3, named 'Direction' handle, is removed while Camera 4 still looks at it, or a registered reference/arrangement relationship blocks another target
- **THEN** removal explains the blocking relationship and leaves every entity unchanged

#### Scenario: ENT-06 Undo from the initiating surface
- **WHEN** an entity is removed from the tree and Undo/Redo are invoked with the tree focused
- **THEN** the same entity returns/disappears in the file, tree, Properties and open Scene View without needing to open the text editor

### Requirement: Typed asset property coverage

The suite SHALL cover the existing terrain, cube-sky and procedural-sky field catalogs, effective omitted defaults, reference choices, validation and concurrent-edit rejection. Other asset types SHALL retain their specified read-only behavior, previews and diagnostics.

#### Scenario: AST-01 Terrain metadata fields
- **WHEN** the fixture terrain's supported size, texture or texture-repetition field is edited
- **THEN** only that metadata field changes, reference storage uses the required native identity, and Undo/Redo and reopen preserve the result

#### Scenario: AST-02 Cube face choices
- **WHEN** a local image is selected for a cube-sky face and later cleared where None is supported
- **THEN** the face reference and preview follow the choice and unrelated faces remain unchanged

#### Scenario: AST-03 Omitted procedural defaults
- **WHEN** an omitted supported atmosphere field is displayed, changed and reset to its default
- **THEN** its effective value is visible before editing and default omission follows the native metadata contract after reset

#### Scenario: AST-04 Invalid combinations
- **WHEN** terrain size is invalid or procedural atmosphere radii form an invalid combination
- **THEN** the field shows a useful reason and the metadata remains unchanged

#### Scenario: AST-05 Unresolved and stale values
- **WHEN** a stored reference is unresolved, or metadata changes while an older field editor is awaiting commit
- **THEN** the unresolved identity remains visible rather than silently replaced, and a stale commit cannot overwrite the newer value

#### Scenario: AST-06 Preview and read-only states
- **WHEN** model, HDR or cloud metadata is selected, or a cube/HDR preview source is missing or unreadable
- **THEN** supported read-only values remain visible, missing previews show their diagnostic, and selection or failed decoding does not rewrite metadata

### Requirement: Per-entity optical material coverage

The suite SHALL cover model-instance transmission and IOR overrides, conversion of displayed percentages, valid ranges, default omission and material identity diagnostics. It SHALL verify that edits affect only the chosen entity and do not rewrite the source model or other instances.

#### Scenario: OPT-01 Percentage and IOR round trip
- **WHEN** transmission is set to 25 percent and IOR to 1.7 for an identified PBR material
- **THEN** the instance stores transmission 0.25 and IOR 1.7, the panel reopens with those values, and Undo/Redo restore both editing states

#### Scenario: OPT-02 Instance isolation and defaults
- **WHEN** one of two instances sharing a model is edited and its optical values are later reset to defaults
- **THEN** the second instance and source model remain byte-identical and default-valued override members are omitted as specified

#### Scenario: OPT-03 Invalid optical values
- **WHEN** transmission is below 0 or above 100 percent, IOR is outside 1 through 3, or either input is nonfinite/nonnumeric
- **THEN** the value is refused, the previous display value returns and no edit is written

#### Scenario: OPT-04 Material diagnostics
- **WHEN** a model is unreadable, a material ID is missing/duplicated, a material is non-PBR, or a stored override names a material no longer present
- **THEN** the panel explains the condition, does not offer unsafe edits and preserves unresolved override data

#### Scenario: OPT-05 Optical conflict
- **WHEN** another editor changes an optical override before an older field commits
- **THEN** the older commit reports a conflict and does not overwrite the newer value

### Requirement: Tree and skybox chooser coverage

The suite SHALL cover discovery, row actions, selection/expansion refresh, unused filtering/counts and the skybox chooser's search, None, preselection and cancellation paths. Visibility and rename edits SHALL change only their intended native scene field.

#### Scenario: TRE-01 Stable navigation and refresh
- **WHEN** a copied project is expanded, edited, switched away from and reopened in the Abyssus view
- **THEN** project/scene/entity/asset rows are neither duplicated nor stale and surviving selection/expansion is preserved

#### Scenario: TRE-02 Rename and visibility cycles
- **WHEN** the fixture scene is renamed or an ambient/fog/sky visibility eye is toggled off and on
- **THEN** only name or the corresponding Enabled flag changes and Undo/Redo restore the preceding states

#### Scenario: TRE-03 Unused filter and counts
- **WHEN** Show Only Unused Assets is enabled and disabled after asset usage changes
- **THEN** the asset list follows usage while scene rows remain visible and the footer totals remain independent of filtering

#### Scenario: TRE-04 Chooser filtering and None
- **WHEN** the skybox chooser is opened, filtered with different letter case or no matches, and a sky or None is chosen
- **THEN** choices, retained selection and footer agree with the filter, and accepting changes only skyboxName

#### Scenario: TRE-05 Cancel and current selection
- **WHEN** rename or skybox selection is cancelled, or the current sky is accepted without a change
- **THEN** the scene and its edit history remain unchanged

### Requirement: Scene gesture coverage

The suite SHALL cover actual selection, navigation and authoring gestures, including mode/space/snap changes, Drop, framing and orientation-widget input. A completed authoring gesture SHALL be one edit; view navigation and cancelled/no-op gestures SHALL not write scene content. A scenario that cannot obtain a GL context SHALL be recorded as unverified rather than passed.

#### Scenario: GES-01 Pick and clear
- **WHEN** Model 0, Camera 4 or an eligible light is clicked and then empty space is clicked
- **THEN** view/tree selection agrees for the picked object and empty space clears selection

#### Scenario: GES-02 Mode and navigation
- **WHEN** Q, W and E or their toolbar controls switch Select, Move and Rotate, and a drag starts away from a handle
- **THEN** the selected tool and applicable handles agree, navigation remains usable and navigation alone leaves scene bytes unchanged

#### Scenario: GES-03 World and local transforms
- **WHEN** an eligible object is moved/rotated using World and Local handles
- **THEN** saved position/rotation follows the chosen axes, only intended fields change, and Undo from Scene View restores the preceding pose and text

#### Scenario: GES-04 Camera and look-at transforms
- **WHEN** Camera 4 is moved or a directional/spot light aimed at a direction handle is rotated
- **THEN** linked camera positions or direction-handle fields follow the transform contract and prohibited rotate handles remain absent

#### Scenario: GES-05 Snap and cancellation
- **WHEN** a move uses the displayed snap step or a rotation uses angular snap, followed by an independent drag cancelled with Escape
- **THEN** committed values are quantized as specified and the cancelled drag restores its starting pose without an edit

#### Scenario: GES-06 Drop and framing
- **WHEN** an eligible object is dropped, or Frame Selection is used with and without a selection
- **THEN** Drop changes only the permitted placement, framing changes only the view, and unavailable actions remain disabled

#### Scenario: GES-07 Camera chooser and orientation widget
- **WHEN** the user switches between free and scene cameras or clicks an orientation axis
- **THEN** the active camera/view follows the choice, editing availability follows the camera mode and no authored camera pose changes merely from viewing

### Requirement: Path arrangement coverage

The suite SHALL cover model choice, drawing/finish/cancel, point/settings edits, regeneration and copy detachment through supported tree/toolbar/viewport entry points. It SHALL verify copy identities, slots, counts, terrain placement, reference guards and one-edit Undo/Redo without silently deleting owned copies.

#### Scenario: PTH-01 Draw and finish
- **WHEN** a model is chosen for Add Path Arrangement and a valid path is drawn and finished
- **THEN** the arrangement, points, slots and copies are persisted together, the arrangement is selected and reopening retains their identities and tree counts

#### Scenario: PTH-02 Cancel and insufficient points
- **WHEN** path drawing is cancelled or finishing is attempted without enough valid points
- **THEN** cancellation leaves no partial arrangement/copies and incomplete drawing cannot persist an invalid path

#### Scenario: PTH-03 Edit and regenerate
- **WHEN** points are moved/inserted/removed, spacing/sides/offset/alignment are changed or Regenerate is used after terrain changes
- **THEN** copies follow the path and terrain rules, surviving slots retain their identities and one Undo restores the preceding arrangement/copy state

#### Scenario: PTH-04 Detach a copy
- **WHEN** an arranged copy is moved through a supported property or gizmo edit
- **THEN** its transform, parent removal and empty arrangement slot are written in one edit and the detached copy appears as an ordinary entity

#### Scenario: PTH-05 Guard ownership on removal
- **WHEN** a still-owned arrangement/copy is targeted for entity removal, followed by removal of an eligible detached copy
- **THEN** the ownership-blocked removal leaves all slots/copies intact and the eligible removal affects only the detached entity

### Requirement: Display preference and viewport lifecycle coverage

The suite SHALL distinguish view-local display controls from saved authoring fields and project FPS preferences. It SHALL verify shared preference propagation, reopen persistence, independent projects, toolbar reachability and recovery after hidden or unavailable view states.

#### Scenario: DSP-01 View-only toggles
- **WHEN** Lit/Unlit, Shadows, Fog, Grid, Local/World or Snap are changed and returned to their initial state
- **THEN** pressed/enabled controls and stats follow the state while native project, scene and asset files remain unchanged

#### Scenario: DSP-02 Shared FPS preference
- **WHEN** Show FPS is changed while multiple scene views are open and another view is opened later
- **THEN** all views in that IDE project follow the preference, while another IDE project's preference remains independent

#### Scenario: DSP-03 Remember and resume FPS
- **WHEN** an enabled view is hidden and shown, or its project is closed and reopened
- **THEN** the preference is retained, resumed sampling begins afresh and no native file is edited to store the preference

#### Scenario: DSP-04 Narrow and unavailable views
- **WHEN** the editor becomes narrow or rendering is unavailable
- **THEN** the toolbar remains reachable with appropriate disabled actions and a useful rendering diagnostic rather than an inaccessible editing surface

#### Scenario: DSP-05 Close and reopen the canvas
- **WHEN** a scene tab is hidden, resized to an unsafe surface or closed, then an eligible view is shown again
- **THEN** interaction recovers without stale selection/input capture, unsafe rendering or work retained by a closed view

### Requirement: Ray mode and saved-limit coverage

The suite SHALL cover the Properties ray switch separately from saved rayTracing settings, including opening/cancelling requests, unavailable/failed states and retry. All five saved fields SHALL be editable and validated without requiring accelerated hardware. Native image correctness SHALL have separately identified supported-hardware evidence rather than an inferred pass from a control click.

#### Scenario: RAY-01 Request without an open view
- **WHEN** the Properties switch is enabled with no Scene View open
- **THEN** a view opens and follows the request, while scene text remains unchanged by requesting the mode

#### Scenario: RAY-02 Cancel and close requests
- **WHEN** the request is switched off before the view becomes ready, or an active view is closed
- **THEN** a cancelled request does not later reactivate tracing and the panel returns to the appropriate idle state

#### Scenario: RAY-03 Unavailable and failed modes
- **WHEN** tracing is unavailable or a supported execution reports a failure
- **THEN** Properties shows the reason and correct switch availability, exposes Retry for failure, and a retry follows the resulting mode without editing the scene

#### Scenario: RAY-04 Five saved limits
- **WHEN** targetSamplesPerPixel, maxRaysPerFrame, maxReflectionBounces, maxRefractionBounces or maxInstances is edited
- **THEN** the value persists through Undo/Redo and reopen, including when no Scene View or accelerated backend is available

#### Scenario: RAY-05 Invalid and conflicting limits
- **WHEN** a saved limit is fractional, outside its displayed range, nonnumeric or superseded by a newer edit
- **THEN** the panel explains the rejection/conflict and does not overwrite valid newer data

#### Scenario: RAY-06 Malformed saved data and foliage note
- **WHEN** rayTracing is not an object, an individual saved limit is malformed, or the selected open scene contains foliage
- **THEN** settings diagnostics/editability identify malformed data and the ray foliage limitation is shown when applicable, without rewriting input

### Requirement: Water lifecycle coverage

The suite SHALL cover both water presets through scene tree and toolbar entry points, Properties and gesture edits, visibility, rename/removal and invalid-surface recovery. It SHALL verify that water remains distinct from ECS entities and that edits preserve other surfaces and scene content.

#### Scenario: WAT-01 Create both presets
- **WHEN** Lake and Sea are added through the supported tree/toolbar choices
- **THEN** each surface receives its own identity/defaults, is selected and appears under the Water group with the expected placement

#### Scenario: WAT-02 Edit and remove
- **WHEN** a water surface is renamed, resized, moved or toggled, then saved/reopened and removed
- **THEN** only that surface changes, Undo/Redo restore each accepted edit and removal clears its properties/selection

#### Scenario: WAT-03 Invalid input and surface
- **WHEN** an extent or other typed field is invalid, or a prepared surface contains invalid saved data
- **THEN** the rejection/invalid state is visible, unsupported edits are unavailable and permitted rename/removal remain usable

#### Scenario: WAT-04 Incompatible extension
- **WHEN** the scene's water extension has an incompatible shape
- **THEN** water creation is unavailable or refused with a reason and no existing extension data is replaced

### Requirement: Terrain preview and regeneration coverage

The suite SHALL cover new terrain and regeneration with all offered generation settings, preview invalidation, seed randomization, Apply/Cancel, invalid geometry/names and concurrent-source guards. Preview-only operations SHALL not persist data; accepted writes SHALL preserve unrelated metadata and restore exact affected files on Undo.

#### Scenario: TER-01 Matching preview before creation
- **WHEN** valid name/geometry/settings are entered and a matching preview finishes
- **THEN** Create becomes available and creates a native terrain whose saved heights/recipe match the preview, with Undo/Redo and reopen preserving identity

#### Scenario: TER-02 Invalidate and randomize
- **WHEN** size, resolution or a generation setting changes after preview, or the seed is randomized
- **THEN** an old preview cannot be applied until a matching new preview completes; a valid name-only change does not unnecessarily invalidate its height result

#### Scenario: TER-03 Validate and cancel
- **WHEN** the name collides, geometry/settings are invalid, or preview is cancelled/closed
- **THEN** the reason and availability are correct and no terrain files are created or changed

#### Scenario: TER-04 Regenerate existing terrain
- **WHEN** the fixture terrain is previewed and regenerated with changed settings
- **THEN** only intended heights/recipe data change, shared instances observe the updated terrain and Undo/Redo restore exact affected bytes

#### Scenario: TER-05 Source changes after preview
- **WHEN** heights or unsaved metadata change after preview but before Apply
- **THEN** Apply refuses to overwrite the changed source and preserves the newer bytes/text

#### Scenario: TER-06 Unusable or superseded work
- **WHEN** terrain data is not a usable square grid, its recipe is malformed, selection changes during preview or an older preview finishes late
- **THEN** the appropriate diagnostic/defaults are shown and unusable or superseded results cannot replace the current selection/result

### Requirement: Foliage layer and collider coverage

The suite SHALL cover foliage creation/attachment, layer kinds/order, weighted model choices, field validation, preview/Apply/Cancel, limits and rebake. Collider-only edits SHALL be covered separately from scattering, including stale/missing bake states and preservation of unrelated collider extension data.

#### Scenario: FOL-01 Create and attach
- **WHEN** a foliage asset is created for an eligible terrain and attached through the supported action
- **THEN** the asset and component references agree, the result reopens correctly and guarded Undo respects later references

#### Scenario: FOL-02 Layer and model cycles
- **WHEN** layers are added, reordered, edited or removed and weighted models are added/changed/removed
- **THEN** displayed counts/order and applied settings/bake agree, invalid inputs are rejected and Undo/Redo restore the accepted file states

#### Scenario: FOL-03 Drafts, limits and cancellation
- **WHEN** changes exceed the copy limit, Cancel is used, selection changes, or the source changes before Apply
- **THEN** refusal/cancellation is visible and unapplied or stale drafts do not overwrite source files

#### Scenario: FOL-04 Rebake stale data
- **WHEN** a stale or corrupt bake is regenerated through the offered Rebake path
- **THEN** the saved bake matches current accepted settings and Undo can restore the exact earlier bake bytes

#### Scenario: FOL-05 Collider-only Apply
- **WHEN** an OBJECT layer's None/Box/Sphere/Capsule collider or dimensions/offset are changed without scatter edits and Apply is used
- **THEN** only collider metadata changes, no preview/bake is required and Undo/Redo preserve all existing bake bytes or leave a missing bake missing

#### Scenario: FOL-06 Collider availability and validation
- **WHEN** a non-OBJECT layer is selected, invalid collider dimensions are entered or a collider with extension data is replaced/cleared
- **THEN** controls follow the layer kind, invalid edits write nothing and unrelated extension members remain intact

### Requirement: Foliage painting coverage

The suite SHALL cover brush availability, layer/radius/strength controls, paint/erase gestures, cancellation, empty strokes, masks/bakes and transitions to other selections or Play. One committed stroke SHALL be one undoable operation and late sessions SHALL not restore painting for an obsolete selection.

#### Scenario: PNT-01 Brush controls and availability
- **WHEN** a paintable foliage-bound terrain is selected and Paint Foliage is enabled
- **THEN** the brush strip follows the selected layer/radius/strength and gizmo/Drop availability follows painting; an ineligible selection cannot enter painting

#### Scenario: PNT-02 Paint and erase
- **WHEN** a stroke is painted and then erased using the erase checkbox or Shift
- **THEN** the intended mask and bake update, viewport copies follow the stroke and Undo/Redo plus reopen restore the corresponding persisted states

#### Scenario: PNT-03 Cancel and no-op strokes
- **WHEN** Escape cancels a stroke or an erase stroke changes no density
- **THEN** no mask/bake edit or extra undo step is created and ordinary navigation remains usable

#### Scenario: PNT-04 Selection, Play and concurrent changes
- **WHEN** selection changes, Play starts or relevant files change while a brush/session is active
- **THEN** painting ends or safely revalidates as specified, obsolete session results cannot reactivate it and newer source files are not overwritten

### Requirement: Import and weather snapshot coverage

The suite SHALL cover local model/aircraft imports and weather snapshots with name/source/settings validation, supported previews, cancellation, persistence and guarded Undo/Redo. Source files SHALL remain unchanged. Conflicts and later saved/unsaved references SHALL prevent unsafe removal or overwrite.

#### Scenario: IMP-01 Model settings and preview
- **WHEN** a local model is opened and unit/up axis, original/extent/height fit or preview animation is changed
- **THEN** source/default/chosen values and transformed preview/status agree, invalid target sizes disable Create and preview animation choice does not discard other imported animations

#### Scenario: IMP-02 Model import and placement
- **WHEN** a valid model is imported with and without Add to scene
- **THEN** its native folder/provenance and optional instance are created as the promised single operation, reopen correctly and Undo/Redo restore the folder and placement together

#### Scenario: IMP-03 Placement availability and cancellation
- **WHEN** no eligible Scene View exists, Play is active, the scene is unreadable, a source/name is invalid or the import is cancelled
- **THEN** placement/Create availability explains the relevant condition and no partial asset or scene edit is left behind

#### Scenario: IMP-04 Aircraft import settings
- **WHEN** a local minimal FlightGear archive is imported with selected parts and a valid size
- **THEN** native output follows the selected parts/scale, unavailable licence information is visible, and Undo/Redo restore the same imported files

#### Scenario: IMP-05 Weather snapshot
- **WHEN** a weather preset is created from a procedural sky's cloud source
- **THEN** source settings are revalidated on Create, the snapshot appears as a new asset, source sky/cloud files remain unchanged and Redo preserves identity, timestamp and bytes

#### Scenario: IMP-06 Guarded asset history
- **WHEN** a new asset gains a saved/unsaved reference before Undo, its files change, or a conflicting folder appears before Redo
- **THEN** the history operation is refused without deleting references or overwriting conflicting files, with a useful reason

### Requirement: Game UI authoring coverage

The suite SHALL cover UI asset registration, screens/overlays, all eleven offered widget kinds, hierarchy changes, typed properties, declarations, resources and lists. It SHALL verify unique identities, one-edit gestures, Undo/Redo, save/reopen and preservation of unknown widget/extension data.

#### Scenario: GUI-01 Asset and screen cycles
- **WHEN** a menus asset is created in a copy of Untitled, registered/unregistered and edited with screen/overlay add/rename/delete operations
- **THEN** uiAssetNames and asset metadata agree, entry-screen indication follows valid state, and Undo/Redo restore each supported operation

#### Scenario: GUI-02 Every widget kind
- **WHEN** panel, row, column, label, image, button, progressBar, slider, checkbox, textField and list are each added and edited
- **THEN** each appears with the correct kind-specific fields, can complete its supported duplicate/delete cycle and retains stable unique IDs after save/reopen

#### Scenario: GUI-03 Ordering, parenting and canvas gestures
- **WHEN** a widget is reordered with controls or tree drag/drop, reparented or moved/resized through the canvas
- **THEN** tree/canvas/properties agree, layout fields follow the parent policy and one completed gesture is reversed by one Undo

#### Scenario: GUI-04 Invalid hierarchy and stale gestures
- **WHEN** a descendant/self-parenting drop is attempted or metadata changes after a canvas gesture began
- **THEN** the edit is refused with a visible reason and the current hierarchy/text remains unchanged

#### Scenario: GUI-05 Bindings, styles and actions
- **WHEN** declarations and widget binding/style/action choices are added, changed or removed
- **THEN** typed samples, limits, access rules, colors and screen/resource targets are validated, invalid/missing references are explained and valid declarations round-trip through Undo/Redo and reopen

#### Scenario: GUI-06 Image and font import
- **WHEN** an image or bitmap font with pages is imported using a supported local source
- **THEN** copied files and resource declaration form one undoable operation, names do not collide, and invalid or concurrently changed inputs cannot leave partial files

#### Scenario: GUI-07 List authoring
- **WHEN** a list's row template, optional empty state, item-field bindings, row limit and arrow-navigation setting are edited
- **THEN** template/empty rows stay distinct in the tree, item bindings use their declared record types and Undo/Redo plus reopen preserve the result

### Requirement: Game UI interactive preview coverage

The suite SHALL cover Edit/Preview behavior, isolated sample state, actions/navigation, lists, resolution/backdrop preferences, invalid-input recovery and closing resources/input. Preview interaction SHALL not edit authored metadata, undo history or game settings, and SHALL not start game code.

#### Scenario: PRV-01 Selection versus interaction
- **WHEN** a button is clicked in Edit and then Preview
- **THEN** Edit selects it, Preview performs its authored preview action, and returning to Edit restores authoring selection without a preview-induced edit

#### Scenario: PRV-02 Typed samples and reset
- **WHEN** samples are edited directly or via slider/checkbox/text-field controls, invalid values are attempted and Reset is used
- **THEN** valid values update bound widgets, invalid values show their reason and Reset restores authored samples/navigation and clears the action log

#### Scenario: PRV-03 Actions and focus
- **WHEN** screen/overlay navigation, a logged game action, Tab/arrows or a select-on-focus text field is exercised
- **THEN** focus/navigation/log behavior follows the authored configuration without launching game code or writing documents

#### Scenario: PRV-04 Lists and clickable rows
- **WHEN** sample list rows are added/edited/removed, an empty list is shown or a row action is activated
- **THEN** template content, empty state, row limits and item-specific actions/styles follow sample data without changing the authored list

#### Scenario: PRV-05 Sizes and backdrop
- **WHEN** a preset/custom viewport size or native-scene backdrop is chosen, including an invalid custom size, and the tab is reopened
- **THEN** layout uses the accepted size, invalid dimensions show a reason, preferences reopen as specified and the backdrop does not run physics or edit its scene

#### Scenario: PRV-06 Refresh, recovery and close
- **WHEN** unsaved UI text becomes invalid and is corrected, a referenced resource changes, or Preview closes while a control has focus
- **THEN** invalid interaction stops with a diagnostic, current valid content/resources rebuild correctly and a closed editor no longer consumes input or adopts late results

### Requirement: Physics and Play coverage

The suite SHALL cover project physics settings, offered typed component availability, overlays and external Play controls. It SHALL verify no native scene edits from simulation, editing/physics-off transitions, start failure recovery and cleanup of the owned play process.

#### Scenario: PHY-01 Project toggle and catalog
- **WHEN** Physics is toggled in project Properties, undone/redone or changed through unsaved project text
- **THEN** the checkbox, typed component choices and overlay availability follow current admitted settings without requiring reselection or losing stored disabled-physics payloads

#### Scenario: PHY-02 Components and overlay
- **WHEN** offered physics components are added/edited/removed and Show Physics is toggled
- **THEN** typed fields and overlay follow current authored state and Undo/Redo, and the overlay toggle alone does not edit native files

#### Scenario: PHY-03 Play control cycle
- **WHEN** Play, Pause, Step, resume and Stop are used in a disposable physics project
- **THEN** control enablement follows phase, stepping advances paused simulation, Stop restores authored display state and source scene bytes remain unchanged

#### Scenario: PHY-04 Edit and physics-off transitions
- **WHEN** authored content is edited or project physics is disabled during startup, playing or pause
- **THEN** Play ends/rejects stale startup as specified and editing controls recover without persisting simulated poses

#### Scenario: PHY-05 Input, failure and cleanup
- **WHEN** keyboard focus moves into/out of Play, startup fails, the host exits or its scene editor closes
- **THEN** input routes only to the active game, failure is visible, restart remains possible and the owned process does not remain after cleanup

### Requirement: Native admission, preservation and isolation coverage

The suite SHALL cover supported native markers, malformed/future/legacy documents, unknown data and number/key/default preservation. It SHALL distinguish intentional text-editor formatting from authoring edits and read-only navigation. Disposable projects and editor teardown SHALL prevent one scenario's files, preferences, buffers, history or pending work from corrupting another.

#### Scenario: DOC-01 Reject unsupported documents
- **WHEN** prepared project, scene or metadata inputs have missing/foreign markers, nonintegral/unsupported formatVersion, reserved legacy fields or malformed JSON
- **THEN** the affected authoring surface reports/unoffers unsupported operations and leaves input bytes unchanged without implicit migration

#### Scenario: DOC-02 Preserve unrelated native data
- **WHEN** representative scene, project, asset or UI edits are performed beside unknown extension data and number text 1.000
- **THEN** unrelated payloads, numeric spelling, key order and omitted defaults survive, including in admitted wrapped native scenes

#### Scenario: DOC-03 Text-tab formatting
- **WHEN** supported unformatted scene/project text is opened in its text editor, then an already formatted fixture is switched between source and Scene View
- **THEN** the first operation performs only the documented undoable formatting while later view switches do not introduce additional changes; unsupported documents are never formatted

#### Scenario: DOC-04 Current unsaved text and read-only documents
- **WHEN** an authoring operation encounters newer unsaved text or a nonwritable document
- **THEN** it uses/revalidates current text or reports refusal as specified and does not replace newer edits or bypass read-only state

#### Scenario: DOC-05 Repeatable fresh scenarios
- **WHEN** a scenario creates asset folders, leaves editors/history open or starts asynchronous work before teardown and a fresh scenario begins
- **THEN** the new scenario has pristine disposable inputs, no leftover empty-folder collisions or stale edits, and committed fixture hashes remain unchanged

#### Scenario: DOC-06 File recognition and diagnostics
- **WHEN** native project/scene files and glTF files are opened alongside ordinary files, or an asset's metadata is missing/unreadable
- **THEN** registered types/editor tabs and relevant diagnostics are correct and unrelated file types remain usable without unintended writes
