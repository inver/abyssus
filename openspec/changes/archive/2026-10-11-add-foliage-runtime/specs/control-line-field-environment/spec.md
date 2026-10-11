# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
