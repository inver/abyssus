# Spec Delta

## ADDED Requirements

### Requirement: Foliage colliders are static bodies

Starting a simulation SHALL add static collision for every copy of each OBJECT layer that declares a collider, on every
terrain entity whose foliage loads. Each copy's collider SHALL sit where the copy is drawn: on the terrain surface, with
its yaw and tilt, scaled by the copy's scale and the entity's scale, and moved by the collider's offset. A capsule or
sphere SHALL take the largest axis of a non-uniform scale, with one warning, as entity colliders do.

#### Scenario: A box hits a tree
- **WHEN** the `Physics` test project's dynamic box is dropped onto a single foliage copy whose layer has a CAPSULE collider of radius `0.5` and half height `2`
- **THEN** the box comes to rest on top of the capsule, not on the terrain beneath it

#### Scenario: No collider, no collision
- **WHEN** the same copy's layer has no collider
- **THEN** the box falls through the copy's place onto the terrain

#### Scenario: Grass never collides
- **WHEN** a DETAIL layer's `meta.json` holds a `collider`
- **THEN** that layer adds no collision and one warning names the layer

### Requirement: Foliage collision is bounded and validated

A foliage collider with a non-finite value or a size not greater than `0` SHALL be refused before it reaches the engine.
The refusal SHALL come with one warning naming the foliage asset and layer, and the rest of the scene SHALL be
simulated. Layers that would bring a world above 200,000 foliage colliders SHALL be left out, with one warning each. A
missing or unreadable bake SHALL add no foliage collision, with one warning.

#### Scenario: Zero radius
- **WHEN** a layer's collider has radius `0`
- **THEN** that layer adds no collision, one warning names the foliage asset and layer, and the other bodies are simulated

#### Scenario: Too many colliders
- **WHEN** two layers with colliders hold 150,000 copies each
- **THEN** the first layer in file order collides, the second adds no collision, and one warning names it

### Requirement: Foliage bodies are released

Closing a simulation SHALL release every foliage body and shape it created, together with the other bodies. Removing a
terrain entity SHALL remove its foliage bodies.

#### Scenario: Close after foliage
- **WHEN** a simulation with foliage colliders is closed
- **THEN** no foliage body or shape remains in the engine, and later calls fail with "physics world is closed"

#### Scenario: Terrain entity removed
- **WHEN** a terrain entity with foliage colliders is removed from the engine during a simulation
- **THEN** none of that entity's foliage bodies remain in the simulation, and the other bodies are unaffected
