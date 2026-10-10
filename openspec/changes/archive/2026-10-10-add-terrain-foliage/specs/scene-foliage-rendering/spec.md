# Spec Delta

## Purpose

Lets users see the foliage of a terrain entity in the scene view, standing on the terrain surface, at a cost that stays
interactive for forests and large grass fields.

## ADDED Requirements

### Requirement: Foliage of a terrain entity is displayed

The scene view SHALL draw the copies of the foliage asset named by a terrain entity's `FoliageComponent.assetName`, using
the models of the project's assets. Each copy SHALL stand on the terrain surface at its position. It SHALL follow the
entity's position, rotation and scale, and lean toward the surface normal by the layer's alignment.

#### Scenario: Foliage appears
- **WHEN** a copy of Untitled's `Main Scene` gives entity `1` (`Terrain`) a `FoliageComponent` naming a foliage with a layer of `tree`
- **THEN** trees are drawn standing on that terrain, along with the scene's models

#### Scenario: Terrain entity moved
- **WHEN** the user moves entity `1` with the gizmo
- **THEN** the foliage moves with the terrain during the drag and after the release

#### Scenario: Aligned to the slope
- **WHEN** a layer has alignment `1` on a hillside
- **THEN** its copies lean perpendicular to the slope; with alignment `0` they stand upright

#### Scenario: Several entities, one terrain
- **WHEN** two terrain entities place the same terrain and only one has a `FoliageComponent`
- **THEN** only that entity shows foliage

### Requirement: Foliage follows the terrain surface

A copy's height SHALL come from the displayed terrain heights when it is drawn, so that copies never float or sink after
the terrain's heights change.

#### Scenario: Terrain regenerated
- **WHEN** the terrain under shown foliage is regenerated
- **THEN** the copies stand on the new surface at once

### Requirement: Draw distance and culling

DETAIL-layer copies SHALL NOT be drawn farther from the camera than their layer's draw distance. Copies outside the view
SHALL NOT cost drawing time. OBJECT-layer copies SHALL be drawn out to the camera's far plane.

#### Scenario: Grass ends at the draw distance
- **WHEN** a DETAIL layer has a draw distance of `80` and the camera looks across a large meadow
- **THEN** grass is drawn up to about 80 units from the camera and not beyond, while the trees of an OBJECT layer are drawn beyond it

#### Scenario: Large field stays interactive
- **WHEN** a foliage asset holds 1,000,000 copies and the view shows part of the terrain
- **THEN** the view keeps responding to orbit, pan and selection

### Requirement: Foliage follows edits

The displayed foliage SHALL follow changes to the foliage settings, masks, bake, terrain and scene. Changes made through
the plugin SHALL show on the next frame. A component added, changed or removed in the scene SHALL add, swap or remove
the foliage.

#### Scenario: Component removed
- **WHEN** the user deletes `FoliageComponent` from entity `1` in the text tab and pauses typing
- **THEN** the foliage disappears from the view

#### Scenario: Asset changed on disk
- **WHEN** `foliage.data` or a mask of a shown foliage changes on disk
- **THEN** the view shows the new copies without being reopened

### Requirement: Foliage failures are isolated

Foliage that cannot be shown SHALL be skipped and logged without hiding the rest of the scene. This covers a missing
folder, an unsupported or unreadable `meta.json`, a missing terrain, or a terrain other than the entity's. A layer model
that cannot be loaded SHALL drop only its own copies. A `foliage.data` that is corrupt or of an unsupported version SHALL
be treated as stale.

#### Scenario: Wrong terrain
- **WHEN** a `FoliageComponent` names a foliage asset bound to a terrain other than the entity's
- **THEN** no foliage is drawn for that entity, the terrain and models still are, and the reason is written to the IDE log

#### Scenario: Missing model
- **WHEN** one of a layer's two models has no asset folder
- **THEN** the other model's copies are drawn and the problem is logged

#### Scenario: Truncated bake
- **WHEN** `foliage.data` is truncated
- **THEN** the view shows copies generated from the settings and the panel says the bake is out of date

### Requirement: Copies are not picked

A click on a foliage copy SHALL act as a click on whatever is behind it, usually the terrain entity. Copies SHALL NOT be
selected or moved one by one, and drops SHALL NOT rest objects on them.

#### Scenario: Click a tree
- **WHEN** the user clicks a foliage tree standing on entity `1`
- **THEN** entity `1` is selected

### Requirement: Animated models in foliage

A layer model with animations SHALL be drawn in its rest pose, without playing animations.

#### Scenario: Animated model scattered
- **WHEN** a layer uses an animated model
- **THEN** its copies are drawn still, in the rest pose

### Requirement: Foliage is left out of ray tracing

The ray-traced preview SHALL NOT include foliage. It SHALL say so while a shown scene has foliage.

#### Scenario: Ray tracing a scene with foliage
- **WHEN** the user turns on ray tracing in a scene view that shows foliage
- **THEN** the ray-traced image has no foliage and the view says that foliage is not ray traced
