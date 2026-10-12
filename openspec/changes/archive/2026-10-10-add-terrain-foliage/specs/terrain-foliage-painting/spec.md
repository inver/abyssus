# Spec Delta

## Purpose

Lets users decide by hand where foliage grows, by painting and erasing a layer's density on the terrain in the Scene
view, with the copies updating as they paint and each stroke undoable.

## ADDED Requirements

### Requirement: Paint Foliage mode

The Scene view toolbar SHALL offer Paint Foliage while the selected entity is a terrain entity whose `FoliageComponent`
names a readable foliage asset with at least one layer. In this mode a brush strip SHALL offer the layer, the radius in
world units, the strength from 0 through 1, and Paint or Erase. Leaving the mode, or selecting another entity, SHALL end
it.

#### Scenario: Enter the mode
- **WHEN** entity `1` (`Terrain`) of `Main Scene` shows `foliage_meadow`, which has a layer, and the user turns on Paint Foliage
- **THEN** the brush strip appears with that layer chosen, and move and rotate gizmos are hidden

#### Scenario: Not available
- **WHEN** the selected entity is a model, or a terrain entity without foliage, or its foliage has no layers
- **THEN** Paint Foliage is disabled

#### Scenario: Selection changes
- **WHEN** the user clicks the tree row of another entity while painting
- **THEN** Paint Foliage turns off and the newly selected entity shows its gizmo

### Requirement: Brush cursor

In Paint Foliage mode, a circle of the brush radius SHALL be drawn on the terrain surface under the mouse. No circle
SHALL be drawn when the mouse is not over the terrain of the selected entity.

#### Scenario: Over the terrain
- **WHEN** the mouse moves over the selected terrain
- **THEN** a circle of the brush radius follows the terrain surface under the cursor

#### Scenario: Off the terrain
- **WHEN** the mouse points at the sky or at another terrain entity
- **THEN** no circle is drawn and a press does not paint

### Requirement: Painting the density mask

A left-button drag over the terrain SHALL raise (Paint) or lower (Erase) the chosen layer's mask under the brush. The
change SHALL be strongest at the centre and fall smoothly to nothing at the radius, scaled by the strength. Holding Shift
SHALL erase while Paint is chosen. Mask values SHALL stay from 0 through 255. Other mouse buttons and the wheel SHALL
keep navigating the view.

#### Scenario: Paint a patch
- **WHEN** the user drags across the terrain with Paint, radius `10` and strength `1`, on a layer whose mask is 0 everywhere
- **THEN** copies of that layer appear in a band about 20 units wide along the drag, and nowhere else

#### Scenario: Erase with Shift
- **WHEN** the user holds Shift and drags over painted copies
- **THEN** the copies under the brush disappear

#### Scenario: Navigation still works
- **WHEN** the user drags with the right button or turns the wheel in Paint Foliage mode
- **THEN** the view orbits, pans or zooms as outside the mode, and the mask is unchanged

#### Scenario: A rotated, scaled terrain entity
- **WHEN** the terrain entity is rotated and scaled and the user paints
- **THEN** the copies appear under the circle that is drawn

### Requirement: Live update while painting

The copies under the brush SHALL update while the user drags, in every open Scene view showing the foliage, without
waiting for the release. Copies outside the painted area SHALL NOT change.

#### Scenario: Copies follow the stroke
- **WHEN** the user drags slowly across the terrain
- **THEN** copies appear behind the brush during the drag, and copies elsewhere on the terrain keep their positions

### Requirement: Strokes are written and undoable

Releasing the button SHALL write the stroke's mask and the matching bake as one undoable operation. Undo SHALL be
available from the Scene view and from the foliage panel. Esc during a drag SHALL discard the stroke and write nothing.
A stroke that changes no mask value SHALL write nothing.

#### Scenario: One stroke, one undo
- **WHEN** the user paints two strokes and invokes Undo once from the Scene view
- **THEN** the second stroke's copies disappear and the first stroke's remain, and Redo brings the second back with the same copies

#### Scenario: Cancel a stroke
- **WHEN** the user presses Esc before releasing the button
- **THEN** the copies return to how they were before the press and no file is written

#### Scenario: Erase where nothing grows
- **WHEN** the user erases over an area whose mask is already 0
- **THEN** no file is written and no undo step is added

#### Scenario: Files changed elsewhere
- **WHEN** the mask file was replaced outside the IDE after a stroke and the user invokes Undo for that stroke
- **THEN** Undo is refused with a reason and the replaced file is kept

### Requirement: Painting respects the copy limit

A stroke whose result would exceed the foliage copy limit SHALL be refused on release with a reason. The copies SHALL
return to how they were before the stroke, and nothing SHALL be written.

#### Scenario: Over the limit
- **WHEN** a stroke on a dense DETAIL layer would bring the foliage above 1,000,000 copies
- **THEN** a notice explains the limit, the stroke is discarded and the files are unchanged
