# Spec Delta

## ADDED Requirements

### Requirement: Foliage colliders are static bodies

Simulation SHALL add static collision for valid OBJECT-layer colliders on static terrain owners, using the same
readable baked copies and current surface/alignment as runtime drawing. Local collider offsets SHALL follow each
copy's affine transform. A sphere or capsule under non-uniform representable scale SHALL use its largest scale axis
with one layer warning. DETAIL layers SHALL not collide.

#### Scenario: A box hits a tree
- **WHEN** a dynamic test box falls onto one copy with a CAPSULE collider of radius `0.5` and half height `2`
- **THEN** after settling it rests on the capsule rather than the terrain

#### Scenario: No collider, no collision
- **WHEN** the same layer has no collider
- **THEN** the box falls onto terrain through the copy's location

#### Scenario: Grass never collides
- **WHEN** a DETAIL layer carries an imported collider
- **THEN** it contributes no collision and one warning names the layer

#### Scenario: Non-uniform capsule scale
- **WHEN** a capsule has a finite orthogonal copy transform with scales 1, 2 and 3
- **THEN** both radius and half height scale by 3, its transformed offset and pose match the copy, and one warning reports the approximation

### Requirement: Foliage collision is bounded and validated

Invalid collider declarations or unrepresentable effective transforms SHALL be refused before native allocation with
one warning naming foliage/layer. Layers SHALL be admitted in numeric entity-id then metadata-layer order, within
200,000 colliding copies and available world body capacity. Rejected or failed layers SHALL add no partial collision;
other layers and ordinary bodies SHALL keep working.

#### Scenario: Zero radius or unknown shape
- **WHEN** a collider has radius zero or an unknown shape
- **THEN** its layer has no collision, a warning names it and its renderable copies remain available

#### Scenario: Unsupported affine transform
- **WHEN** rotation under non-uniform terrain scale produces shear, or the copy transform is singular, reflected or non-finite
- **THEN** the affected collider layer is refused with a reason instead of silently misplaced

#### Scenario: Moving terrain owner
- **WHEN** a foliage owner declares dynamic or kinematic motion
- **THEN** its foliage adds no static collision and a warning explains the unsupported moving owner

#### Scenario: Too many colliders
- **WHEN** successive layers hold 150,000 colliding copies each
- **THEN** the first is admitted and the second is wholly refused with one warning

#### Scenario: Too many chunk bodies
- **WHEN** eligible layers fit the copy cap but exceed available world body capacity
- **THEN** the excess layer is refused before creating bodies and existing bodies continue simulating

#### Scenario: Native construction failure
- **WHEN** a native allocation fails after part of a layer was staged
- **THEN** all of that layer's staged resources are released, no partial collision remains, and later valid layers can be admitted

### Requirement: Runtime and collision use the same bake policy

Collision SHALL consume committed copies without generation or asset writes, using the same current terrain and model
mapping as runtime drawing. A readable stale or unverifiable bake SHALL remain usable with a bounded warning.
A missing or unreadable bake, wrong terrain or unsupported native metadata SHALL add no foliage collision.

#### Scenario: Stale density settings
- **WHEN** layer density changes without rebaking
- **THEN** simulation collides with saved copies at the same placements runtime draws and no file is regenerated

#### Scenario: Unreadable bake
- **WHEN** the bake cannot be read
- **THEN** ordinary simulation continues without its foliage collision and one warning identifies the problem

### Requirement: Foliage contacts identify their owning terrain

Contacts with foliage SHALL identify the owning terrain entity and preserve relative-speed and first-contact
reporting, including when that entity has no ordinary collider. Multiple chunk bodies SHALL not replace or corrupt
that entity's ordinary body record.

#### Scenario: Hit a foliage-only owner
- **WHEN** a dynamic body touches foliage attached to a terrain entity without an ordinary collider
- **THEN** a contact names that dynamic body and the terrain owner

### Requirement: Foliage bodies are released

Closing simulation SHALL release all foliage bodies and native shapes/references exactly once. Removing a terrain
owner SHALL release its foliage even if it has no ordinary collider. Failed construction SHALL leave no retained
native resource. Closed-world operations SHALL fail with the established closed-world error.

#### Scenario: Close after foliage
- **WHEN** 100 successive simulations create, step and close foliage collision
- **THEN** runs retain no prior foliage resources and stepping any closed world fails with the established closed-world error

#### Scenario: Terrain entity removed
- **WHEN** a foliage-only terrain owner is removed
- **THEN** its chunk bodies and owned resources are released while other scene bodies remain valid
