# Spec Delta

## Purpose
A parametric CAD description of the Classic F2B whose shape is controlled by named, bounded parameters and exported as standard CAD files, so the shape can be reproduced exactly and fitted by an optimiser.

## ADDED Requirements

### Requirement: Named, bounded parameter set
The CAD model SHALL be a pure function of a parameter set in which every parameter has a unique name, a default, lower and upper bounds, a unit and a description. Defaults SHALL reproduce the current hand-tuned Blender dimensions, and parameters SHALL be grouped by the part they shape.

#### Scenario: Defaults are valid
- **WHEN** the parameter set is created with defaults
- **THEN** every default lies within its own bounds
- **AND** the parameter names are unique

#### Scenario: Out-of-range value is refused
- **WHEN** a parameter value outside its bounds, or an unknown parameter name, is supplied
- **THEN** the set is rejected with an error that names the parameter
- **AND** no CAD model is built from it

#### Scenario: Parameters round-trip
- **WHEN** a parameter set is saved to JSON and loaded again
- **THEN** every value is identical

### Requirement: Datum dimensions are not parameters
Dimensions that define the registered photo cameras and the blueprint datums SHALL be fixed in every built model: the 740 mm and 710 mm wing semispans, the 345 mm stabilizer semispan, the wing and stabilizer tip corner stations, the 340 mm wing root chord, the 161 mm stabilizer root chord, the 240 mm canopy length, the 340 mm propeller diameter and the 125 mm fin height above the aft deck.

#### Scenario: Any in-bounds parameter set keeps the datums
- **WHEN** a model is built from a parameter set with every parameter at a bound or at the default
- **THEN** the built wing, stabilizer, canopy and fin extents equal the datum dimensions within 0.5 mm

### Requirement: Reproducible CAD export
Building a model SHALL be deterministic and SHALL export an assembly STEP file with one named solid per part and one STL file per part, in millimetres and with the engine side on the positive y axis, matching the Blender model's coordinate frame.

#### Scenario: Principal solids are valid
- **WHEN** the default model is built
- **THEN** the fuselage, both wings, both stabilizer halves and the fin are valid closed solids with positive volume

#### Scenario: STEP file reloads
- **WHEN** the exported STEP file is read back
- **THEN** it contains every named part
- **AND** the bounding box of the whole assembly equals that of the in-memory model within 0.1 mm

#### Scenario: Same parameters give the same model
- **WHEN** the same parameter set is built twice
- **THEN** the part volumes and bounding boxes are identical
