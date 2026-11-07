# Spec Delta

## Purpose

Lets users turn the modelled lamps of a placed model into real point lights of the scene, so the light comes from where
the lamp is without placing each light by hand.

## ADDED Requirements

### Requirement: Create Lights From Model is offered for model entities

The plugin SHALL offer Create Lights From Model in the right-click menu of a model entity row of the Abyssus tree. The
action SHALL be unavailable for an entity that is not a model entity, for an entity whose model asset cannot be read, and
for a scene file that cannot be read as a scene. Nothing is written when it is unavailable.

#### Scenario: Offered on a model entity

- **WHEN** the user right-clicks the entity of `Hangar` that renders `model_warehouse_fbx_model_free`
- **THEN** the menu has Create Lights From Model

#### Scenario: Not offered on a light

- **WHEN** the user right-clicks a spot light entity of `Hangar`
- **THEN** the menu has no Create Lights From Model

#### Scenario: Missing model asset

- **WHEN** the entity's `assetName` names an asset folder that does not exist
- **THEN** Create Lights From Model is disabled and the scene is unchanged

### Requirement: The user chooses the lamp material

The action SHALL list the materials of the entity's model (or of its `meshId` mesh when the entity names one), materials with a
non-zero emissive color first, each with its emissive color, and SHALL create lights only from geometry that uses the chosen
material. It SHALL write nothing when the user cancels.

#### Scenario: Warehouse materials

- **WHEN** the dialog opens for `model_warehouse_fbx_model_free`
- **THEN** it lists `Emissive` first, with the color `0.39, 0.78, 1.0`, then `Metal` and `WetConcrete`

#### Scenario: Cancel

- **WHEN** the user cancels the dialog
- **THEN** the scene file is unchanged

### Requirement: One point light per lamp

For the chosen material the action SHALL find each connected piece of its geometry (triangles that share vertices at the
same position) and SHALL add one `LIGHT_POINT` entity per piece, placed at the centre of the piece's bounds in world space. World space
SHALL include the entity's position, rotation and scale and the transforms of the model's nodes. The new entities' ids
SHALL follow the highest numeric entity id of the scene in order, and their names SHALL be the model entity's name,
`Lamp` and the lamp's number. Everything else in the file SHALL stay as it was.

#### Scenario: Separate lamps

- **WHEN** the chosen material has 12 pieces of geometry that touch each other nowhere
- **THEN** 12 point lights are added, one at the centre of each piece

#### Scenario: Entity transform applies

- **WHEN** the model entity is moved 10 units along x and scaled by 2
- **THEN** each light is at its lamp's centre after that move and scale

#### Scenario: Material not used

- **WHEN** the chosen material has no geometry
- **THEN** no light is added and the user is told that no lamp was found

### Requirement: Light values come from the lamp

Each new light SHALL use the chosen material's emissive color as its color, or white when that color is black or absent. It
SHALL have a default intensity and a range proportional to the largest dimension of its lamp's bounds, never smaller
than a documented minimum. The values SHALL be editable like any light's and SHALL be saved as ordinary light component values.

#### Scenario: Emissive color

- **WHEN** lights are created from `Emissive` of the warehouse
- **THEN** every new light has color `0.39, 0.78, 1.0`

#### Scenario: White fallback

- **WHEN** the chosen material has no emissive color
- **THEN** the lights are white

#### Scenario: Larger lamp, longer range

- **WHEN** one lamp is twice as large as another
- **THEN** its light has a longer range, until the other's minimum applies

### Requirement: The number of lights is capped

A single use of the action SHALL add at most a documented maximum number of lights. When the material has more pieces than the
maximum, the nearest pieces SHALL be merged into one light at their joint bounds centre until the maximum is reached, and
the user SHALL be told that lamps were merged.

#### Scenario: Too many pieces

- **WHEN** the chosen material has more pieces than the maximum
- **THEN** exactly the maximum number of lights is added and the user is told lamps were merged

### Requirement: Creating lights is one undoable edit that stays loadable

All new lights SHALL be added by one edit of the scene file, so one undo removes them all. The written scene SHALL stay loadable by the
view and the game, and the new lights SHALL light the view as any point light does.

#### Scenario: Undo

- **WHEN** the user creates 12 lights and then undoes once
- **THEN** the scene has none of the 12 lights

#### Scenario: Lit in the view

- **WHEN** the lights have been created from the warehouse's `Emissive` material
- **THEN** surfaces near each lamp are lit by the lamp's color in the Scene view

### Requirement: Lights can be created without the IDE

The headless editing API SHALL offer the same operation for a scene and a model entity, with the chosen material, and SHALL return
the ids of the added lights or a rejection message.

#### Scenario: Headless run

- **WHEN** the headless API is asked for lights from `Emissive` of the warehouse entity
- **THEN** it returns the ids of the added lights and the scene text holds them
