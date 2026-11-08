# Spec Delta

## Purpose

Lets the author mark a model part as a hinged control surface that turns about its pivot's X axis between two limits,
stored in the model asset, and check how it swings in the Scene view without changing any file.

## ADDED Requirements

### Requirement: A part's hinge is stored in the model asset

An entry of `additional.parts` in a MODEL asset's `meta.json` MAY hold a `hinge` with `min` and `max`, in degrees. A
hinge SHALL turn the part about the X axis of its pivot frame (its node's frame when it has no pivot), positive by the
right-hand rule. `min` SHALL be at most 0, `max` at least 0, both within -180 and 180. A hinge that breaks these rules
SHALL be refused with one warning naming the model and node, and Properties SHALL refuse writing such values. A hinge
on a part whose pivot is refused SHALL still turn about the node's own X axis.

#### Scenario: Trainer flaps

- **WHEN** `model_trainer/meta.json` holds `parts.LeftFlap.hinge` with `min` -35 and `max` 35
- **THEN** Properties shows the hinge of `LeftFlap` with those limits

#### Scenario: Limits the wrong way round

- **WHEN** the user types `min` 10 for the hinge of `LeftFlap`
- **THEN** the field shows an error and the file is not changed

### Requirement: Hinge editing in Properties

The part section SHALL offer Add Hinge on a part without one, the `min` and `max` fields on a part with one, and
Remove Hinge. Each change SHALL be one undoable edit of the model's `meta.json`, and a part entry with neither pivot nor
hinge SHALL be removed.

#### Scenario: Add a hinge

- **WHEN** the user selects `RightFlap` of the trainer and presses Add Hinge
- **THEN** `parts.RightFlap.hinge` is written with `min` -30 and `max` 30, and Undo removes it

### Requirement: Deflection preview

On a part with a hinge, the part section SHALL show a Preview slider from `min` to `max`, at 0 when shown. Moving it
SHALL turn the part in the Scene view about its hinge, together with the part's child nodes and the entities bound to
it, and the part outline SHALL follow. The preview SHALL NOT write any file, SHALL NOT mark the scene changed, and SHALL
reset to 0 when the part selection changes, the hinge changes or the view closes. Picking and the gizmo SHALL use the
rest pose.

#### Scenario: Flap down

- **WHEN** the user drags the Preview of `LeftFlap` to 20
- **THEN** the left flap is drawn turned 20 degrees about its hinge line and `Field.scene` and the meta are unchanged

#### Scenario: Reset on selection

- **WHEN** the Preview of `LeftFlap` is at 20 and the user selects `RightFlap`
- **THEN** the left flap is drawn in its rest pose again
