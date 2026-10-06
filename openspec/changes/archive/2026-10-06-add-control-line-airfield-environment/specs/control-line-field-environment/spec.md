# Spec Delta

## Purpose

Defines the reference-based appearance, facilities, landmarks and daylight presentation of the Control Line flying field while preserving its existing gameplay and documenting asset provenance and estimated dimensions.

## ADDED Requirements

### Requirement: Marked asphalt flying circle

The bundled field SHALL display a near-perfect circular dark-grey asphalt pad centred on the pilot, with a diameter
of 50 m (documented as an estimate within 48-55 m) and no smaller than twice the longest bundled line length plus
4 m. White painted rings SHALL mark each bundled plane's line-length radius (15, 18 and 21 m), and a white pilot
circle SHALL mark the centre. The pad SHALL show restrained wear (faint scuffs and tracks that do not hide the
rings) and a thin darker outer border, without a fence or safety net.

#### Scenario: Inspect the flying circle from above

- **WHEN** the bundled field is inspected from a top-down view in the Abyssus editor
- **THEN** the asphalt pad is circular, dark grey and of the documented diameter
- **AND** distinct white rings lie at 15, 18 and 21 m from the central pilot circle
- **AND** the pilot circle aligns with the existing pilot's position
- **AND** no fence or safety net encloses the circle

#### Scenario: Every plane takes off on pavement

- **WHEN** any bundled plane is placed for takeoff at its line length from the pilot
- **THEN** the plane and its takeoff roll lie on the paved, level surface

### Requirement: Worn ground and access paths

The field SHALL depict an open, approximately triangular worn plot bounded by the street to the north and the railway
to the south. It SHALL have light-brown compacted soil around the pad, sparse dry grass and weeds, irregular
vegetation, a northern access path from the street, and crossing dirt tracks west and south. The ground SHALL be
level to at least 3 m beyond the pad edge, then descend gently toward the southern railway.

#### Scenario: Inspect access and ground transitions

- **WHEN** the field is inspected from an oblique aerial view in the Abyssus editor
- **THEN** a worn path connects the northern street to the circle
- **AND** bare compacted ground transitions to dusty brown, grey-brown and muted-green vegetation patches
- **AND** multiple worn paths cross the western and southern ground
- **AND** the level ground around the pad transitions to a gentle southern descent

### Requirement: Eastern preparation facilities

The field SHALL include a small single-storey utility building a few metres east of the paved circle, with light-coloured walls and a grey or light roof, designated for WC and staff use. Adjacent packed-dirt preparation space SHALL contain wooden worktables and model stands. The building and the preparation area SHALL lie outside the clear flying area.

#### Scenario: Inspect the preparation area

- **WHEN** the eastern side of the circle is inspected
- **THEN** the utility building and adjacent preparation area are visible
- **AND** worktables and model stands occupy the packed-dirt area
- **AND** the environment documentation identifies the building's intended WC and staff use

### Requirement: Surrounding landmark layout

The field SHALL include Pribrezhnaya Street with sidewalks and lighting poles to the north, a brown-roofed DK "Plamya"
approximation to the north-northeast across the street, and a double-track railway with electrification poles and
wires to the south, running west-northwest to east-southeast. A long blue-metal-roof industrial building parallel to
the railway SHALL stand to the west-southwest. Low residential houses, gardens and apartment blocks SHALL form the
eastern background. Landmark positions SHALL follow the documented placement estimates, with north along -Z and east
along +X.

#### Scenario: Compare the landmark arrangement with the reference

- **WHEN** a north-up aerial view of the field in the Abyssus editor is compared with the supplied reference
- **THEN** the street, utility area, blue-roof warehouse, civic building, railway and residential context appear in their specified relative directions
- **AND** their distances from the pilot match the documented placement estimates
- **AND** the two railway tracks, electrification poles and wires are distinguishable

### Requirement: Reference-driven vegetation

The asphalt SHALL remain free of vegetation. Dry grass, weeds, small bushes and scattered trees SHALL occupy irregular surrounding patches, with denser tree cover northeast and east near residential areas. Vegetation density SHALL follow the reference rather than a fixed instance count and SHALL respect the clear flying area.

#### Scenario: Inspect vegetation distribution

- **WHEN** the field is inspected from above and from the pilot camera
- **THEN** vegetation is absent from the paved circle and sparse immediately around it
- **AND** denser tree cover appears toward the northeastern and eastern residential areas
- **AND** no tree crown or bush extends into the clear flying area

### Requirement: Realistic daylight presentation

The field SHALL use ground and scenery materials based on photo-sourced textures, under bright midday sun. In the
game, the scene's sun SHALL cast soft-edged shadows from models onto the terrain and other models. Pavement,
compacted earth, dry vegetation, roofs and rails SHALL remain distinguishable from the pilot camera.

#### Scenario: Inspect midday appearance

- **WHEN** the field is rendered in the game from the pilot camera
- **THEN** trees, buildings, the pilot and the plane cast soft-edged shadows on the ground and on other scenery
- **AND** lighting and material detail communicate a dry, worn midday environment

#### Scenario: Gameplay cameras are unchanged

- **WHEN** the game is played
- **THEN** the menu, plane-select and flying cameras behave as before

### Requirement: Clear flying area

No scenery SHALL lie within 28 m horizontally of the pilot, at any height. This covers buildings, furniture, poles,
wires, tree crowns and bushes, measured by their transformed footprints and crown extents rather than their origins.
The radius covers the Racer's 21 m line, plane and handle reach, a safety margin, and 3 m beyond the pad edge. The
pilot, the parked planes and ground surface detail are exempt. Scenery SHALL preserve the pilot position, the existing
plane choices and the flight settings, and SHALL NOT introduce new physics colliders.

#### Scenario: Scenery preserves flight setup

- **WHEN** the scenery is added to the bundled Field scene
- **THEN** Racer, Stunter and Trainer retain their existing flight settings and the pilot retains its existing position
- **AND** every scenery footprint and crown lies at least 28 m horizontally from the pilot
- **AND** the new scenery introduces no physics colliders

### Requirement: Redistributable environment assets

Each downloaded scenery asset SHALL be accompanied by its title, source URL, author and redistribution license. Only assets whose license permits redistribution in this public repository (for example CC0 or CC-BY) SHALL be bundled. Assets under personal-use or no-redistribution terms SHALL NOT be bundled. Attribution-licensed assets SHALL be credited in the environment documentation. Generated assets SHALL retain their generation instructions and reference role. Required models and external textures SHALL be bundled in the native project.

#### Scenario: Inspect a downloaded model

- **WHEN** a developer opens a downloaded scenery asset folder
- **THEN** its source record identifies its author, source URL and redistribution license
- **AND** its declared model and texture files are present

#### Scenario: Attribution-licensed asset is credited

- **WHEN** a bundled asset is licensed CC-BY
- **THEN** the environment documentation credits its author, title, source URL and license

#### Scenario: Non-redistributable candidate is rejected

- **WHEN** a candidate asset's license forbids sharing or is unverified
- **THEN** it is not bundled and a generated or other licensed stand-in is used instead

#### Scenario: Inspect a generated asset

- **WHEN** a developer opens the generated ground asset documentation
- **THEN** the generation instructions identify the supplied image as a layout reference and describe the requested ground appearance

### Requirement: Documented reconstruction estimates

The environment documentation SHALL distinguish the user's artistic brief and supplied image from surveyed measurements. Chosen dimensions, elevations, vegetation species and building identities SHALL be identified as estimates or intended representations, without claiming an exact reconstruction. Where the brief and the image or gameplay disagree, the documentation SHALL record the choice made and why.

#### Scenario: Review reconstruction accuracy

- **WHEN** a developer reads the environment documentation
- **THEN** the chosen pad diameter, landmark distances and placement assumptions are recorded as estimates with the image scale used
- **AND** the utility building and DK "Plamya" are identified as intended representations based on the supplied brief
