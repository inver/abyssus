# Spec Delta

## ADDED Requirements

### Requirement: Layer colliders

OBJECT layers SHALL accept optional BOX, SPHERE or CAPSULE colliders with the defaults of a collider component.
The panel SHALL offer None by default and fields for the chosen shape. DETAIL layers SHALL offer no collider editor.
Active sizes SHALL be finite and positive, and offsets finite. Invalid collider data SHALL not remove the layer's
rendered copies. Untouched nested extension data SHALL survive edits.

#### Scenario: Give trees a trunk
- **WHEN** the user selects CAPSULE, radius `0.4`, half height `3` and applies
- **THEN** the collider records those values with unrelated layer members unchanged

#### Scenario: Grass has no collider
- **WHEN** a DETAIL layer is edited
- **THEN** no collider fields are offered and imported extension data is not silently erased

#### Scenario: Invalid size
- **WHEN** an active radius is zero or an offset or active half extent is non-finite
- **THEN** a field reason prevents Apply and no file changes

#### Scenario: Incompatible kind change
- **WHEN** a layer with a collider is changed from OBJECT to DETAIL
- **THEN** validation requires explicit collider removal before applying the incompatible change

### Requirement: Collider-only changes leave the bake untouched

Changing only colliders SHALL edit only foliage metadata in one undoable operation, without generation or changing
bake/mask bytes or timestamps. It SHALL work with a stale or missing bake, keep that prior stale state and preserve
unrelated native text. Cancel or a source revision change SHALL prevent publication of an obsolete edit.

#### Scenario: Metadata-only Apply and Undo
- **WHEN** a collider is changed and Apply, Undo and Redo are performed with metadata open in a text editor
- **THEN** each restores the intended metadata and neither bake nor masks change, and no separate reload-from-disk Undo appears

#### Scenario: Missing bake
- **WHEN** a valid OBJECT layer gains a collider while its bake is missing
- **THEN** Apply changes metadata and leaves the bake missing and its warning visible

#### Scenario: Remove a collider
- **WHEN** None is applied
- **THEN** only the collider member is removed and unrelated key order and numeric spelling remain unchanged

#### Scenario: External change before Apply
- **WHEN** metadata changes or the owning panel closes before a prepared collider edit is applied
- **THEN** that stale edit writes nothing and no pending work updates the closed panel
