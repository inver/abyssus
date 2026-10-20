## ADDED Requirements

### Requirement: Dimension-constrained editable reference model
The Classic F2B asset SHALL preserve explicit blueprint dimensions while documenting estimated external contours.

#### Scenario: Rebuild and inspect the model
- **WHEN** the reference model is regenerated
- **THEN** the saved editable model retains the 1450 mm wing span, 690 mm stabilizer span, 340 mm wing root chord, 62 mm wing root thickness, 240 mm canopy length, 65 mm canopy rise above the fuselage deck, 125 mm fin height above the aft deck
- **AND** its main gear follows the photographed aircraft: a 450 mm wheel track triangulated from both photographs, not the plan's 370 mm
- **AND** its principal airframe solids are closed

### Requirement: Photo-triangulated corrections
Where both aircraft photographs contradict the plan, the asset SHALL follow the photographed aircraft and record the measurement evidence.

#### Scenario: Correct a feature the plan contradicts
- **WHEN** a model feature is moved to match the photographs
- **THEN** the photo cameras are fitted only to landmarks unrelated to that feature
- **AND** the feature's photographed position is triangulated from both photographs, with known model points reported as checks
- **AND** the documentation names the plan value that was overridden

### Requirement: Local photographic comparison
The asset SHALL include reproducible comparison views using both aircraft photographs and the cockpit photograph supplied in its textures directory.

#### Scenario: Review a shape correction
- **WHEN** a model correction is delivered
- **THEN** fresh model renders and blueprint outlines are available for visual comparison
- **AND** all three photographs are packed in the editable model
- **AND** the documentation distinguishes checked dimensions from traced or photographically inferred details
