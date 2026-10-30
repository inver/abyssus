# Control Line Field Environment Specification

## Purpose

Defines the reference-based appearance, facilities, landmarks and daylight presentation of the Control Line flying field while preserving its existing gameplay and documenting asset provenance and estimated dimensions.

## Requirements

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

The asphalt SHALL remain free of vegetation. Dry grass, weeds, small bushes and scattered trees SHALL occupy irregular surrounding patches, with denser tree cover northeast and east near residential areas. Vegetation density SHALL follow the reference rather than a fixed instance count and SHALL respect the clear flying area. Trees and bushes SHALL be foliage copies of per-species OBJECT layers on the site and outer terrains rather than individual scene entities, each species within 25% of its previous count of 52 broadleaf, 40 young and 33 acacia trees, 48 low and 42 tall bushes. A DETAIL grass layer SHALL cover irregular site-terrain patches outside the asphalt pad and documented hard/worn regions.

#### Scenario: Inspect vegetation distribution

- **WHEN** the field is inspected from above and from the pilot camera
- **THEN** vegetation is absent from the paved circle and sparse immediately around it
- **AND** denser tree cover appears toward the northeastern and eastern residential areas
- **AND** no tree crown or bush extends into the clear flying area

#### Scenario: Vegetation is foliage

- **WHEN** the bundled `Field.scene` is loaded
- **THEN** it has no entity whose model is a tree or bush asset, terrain entities `0` and `300` carry `FoliageComponent`, and each species' copy count in the bakes is within 25% of its previous count

#### Scenario: Grass stays off the asphalt

- **WHEN** the grass copies of `foliage_airfield_site` are inspected
- **THEN** no transformed grass footprint intersects the asphalt pad's 25 m radius of the pilot, and no copy occupies a region excluded by the documented splat thresholds

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
wires, tree crowns and bushes, including foliage copies, measured by their transformed footprints and crown extents rather than their origins.
The radius covers the Racer's 21 m line, plane and handle reach, a safety margin, and 3 m beyond the pad edge. The
pilot, the parked planes and ground surface detail are exempt. Scenery SHALL preserve the pilot position, the existing
plane choices and the flight settings, and SHALL NOT introduce new physics colliders; the field's foliage layers SHALL declare no collider.

#### Scenario: Scenery preserves flight setup

- **WHEN** the scenery is added to the bundled Field scene
- **THEN** Racer, Stunter and Trainer retain their existing flight settings and the pilot retains its existing position
- **AND** every scenery footprint and crown lies at least 28 m horizontally from the pilot
- **AND** the new scenery introduces no physics colliders

#### Scenario: Foliage keeps the flying area clear

- **WHEN** every OBJECT copy of the field's foliage bakes is placed on its terrain with its model's bounds
- **THEN** every copy's footprint and crown lies at least 28 m horizontally from the pilot, and no foliage layer of the field declares a collider

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

### Requirement: Field foliage migration is reproducible

The committed vegetation migration SHALL record source placements, species counts, seeds, mask/splat rules and
asset provenance. Repeating generation on original or migrated project copies SHALL yield identical authored outputs.
It SHALL preserve non-vegetation scene text and flight settings, use an explicit output project, and refuse invalid
or conflicting input without publishing partial files. The game SHALL not depend on the migration tool.

#### Scenario: Rerun after entities are removed
- **WHEN** the generator runs twice on a migrated project copy
- **THEN** the second run uses the recorded source placements, keeps per-species counts within 25%, and leaves all generated files byte-identical

#### Scenario: Preserve the flying setup
- **WHEN** the migration replaces the 215 original vegetation entities
- **THEN** entity text unrelated to vegetation and the two foliage attachments remains unchanged, and no bundled foliage layer declares a collider

#### Scenario: No outer foliage under the site
- **WHEN** the two generated foliage bakes are inspected
- **THEN** no outer-terrain copy lies under the transformed site square

### Requirement: Field foliage has a recorded performance acceptance check

Before accepting grass settings, the project SHALL record a representative desktop baseline, hardware, resolution,
quality, workload, measured CPU/GPU frame times and an agreed acceptance budget. The migrated pilot view SHALL meet
that recorded budget and preserve responsive flight controls. Measurements SHALL distinguish loading/compilation
from steady rendering and CPU submission from GPU execution.

#### Scenario: Unspecified hardware budget
- **WHEN** no target hardware or frame-time budget is supplied
- **THEN** baseline capture and budget selection precede grass tuning, and final acceptance records the selected machine and budget rather than claiming an unmeasured speedup

### Requirement: Optional ray-traced field view

The game SHALL support an opt-in ray-traced display of the field chosen at launch. Without the option the field renders exactly as the raster requirements above describe; the option SHALL NOT change any other behavior. In ray mode the field image SHALL come from the ray tracer and SHALL show the field's terrain, the field scene's models — with moving planes and the pilot following their simulation transforms — plus its sunlight, sky and fog from the current simulation state, through the game's existing camera view. Input, physics, flight, scoring and the HUD, menus and screens SHALL behave as in raster mode and stay raster-drawn. Ray mode SHALL NOT draw foliage and SHALL draw animated models in their bind pose; both limits SHALL be recorded in the game's README. When the ray view is requested but no ray-tracing backend is usable — runtime or crate missing, device unsupported, or the probe refusing it — the game SHALL start the raster field and SHALL state the reason in the window title and the application log; it SHALL NOT fail to start and SHALL NOT silently ignore the request. If ray rendering fails after start (quality limit, stalled frame or lost device), the game SHALL fall back to the raster field and state the reason the same way.

#### Scenario: Ray mode shows the field through the ray tracer
- **WHEN** the game is launched with the opt-in ray field view on a machine with a usable ray-tracing backend
- **THEN** the field image is produced by the ray tracer and shows the terrain, models (moving planes included), sunlight, sky and fog, the window title identifies ray mode, and flying, scoring and the menus behave as in raster mode

#### Scenario: Runtime failure falls back like startup
- **WHEN** ray rendering fails after start with a quality limit, a stalled frame or a lost device
- **THEN** the game continues with the raster field and states the reason in the window title and the log, with no crash

#### Scenario: Raster default is untouched
- **WHEN** the game is launched without the opt-in option
- **THEN** the field renders exactly as before, with no ray-mode indicator and no ray work started

#### Scenario: Explicit fallback with a reason
- **WHEN** the game is launched with the opt-in ray field view but no ray-tracing backend is usable
- **THEN** the game runs the raster field with the reason stated in the window title and the log, and does not crash

#### Scenario: Documented ray mode limits
- **WHEN** ray mode draws the field while it contains foliage and an animated model
- **THEN** the image omits the foliage, the model appears in its bind pose, and the game's README lists both limits
