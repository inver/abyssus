# Spec Delta

## Purpose
Fit the parametric Classic F2B to the supplied photographs by comparing silhouettes, so that deviations from the photographed aircraft are measured and reduced by a reproducible search rather than judged by eye.

## ADDED Requirements

### Requirement: Photo silhouette targets
The pipeline SHALL produce a binary silhouette mask for each registered photograph, taken from the photograph's transparency when its background has been removed and otherwise extracted by GrabCut seeded by the current model's silhouette through the fitted camera, and SHALL keep only photographs whose camera registration is reliable. Holes in the mask (such as transparent glazing) SHALL be filled and components that do not overlap the current model's silhouette SHALL be dropped. Close-up photographs and photographs with an unreliable camera SHALL NOT be used as targets, and the propeller disc SHALL be excluded from every comparison.

#### Scenario: Targets are produced and reviewable
- **WHEN** targets are generated
- **THEN** one mask is written per usable photograph together with an overlay image for visual review
- **AND** each mask overlaps the default model silhouette with an intersection over union of at least 0.8

#### Scenario: Unreliable views are excluded
- **WHEN** the target list is built
- **THEN** the low front photograph and the cockpit close-ups are not among the targets

### Requirement: Silhouette loss
The loss for a parameter set SHALL be computed from the silhouette of the tessellated CAD model projected through each photo's fitted camera and SHALL combine the area of the symmetric difference with the photo mask, normalised by the photo mask area, and the mean symmetric distance between the two contours. A parameter set whose CAD model cannot be built or is invalid SHALL receive an infinite loss.

#### Scenario: Perfect match scores zero
- **WHEN** the target masks are rendered from a parameter set and the loss of that same set is computed
- **THEN** the loss is below 1e-6

#### Scenario: Worse shape scores higher
- **WHEN** the fin height or the spat size of a parameter set is moved away from the target by more than 10 percent
- **THEN** its loss is higher than that of the target set

#### Scenario: Invalid model is rejected
- **WHEN** a candidate cannot be built into valid solids
- **THEN** its loss is infinite and the search continues

### Requirement: Seeded genetic search
The fit SHALL use a real-coded genetic algorithm over the bounded parameters, with tournament selection, crossover, mutation and elitism, evaluated in parallel. For a given seed, parameter set and targets, the search SHALL return identical results regardless of the number of worker processes, and the best loss SHALL never increase between generations.

#### Scenario: Search is reproducible
- **WHEN** the search is run twice with the same seed
- **THEN** the best parameter set and the loss history are identical

#### Scenario: Search recovers a known shape
- **WHEN** the targets are rendered from a parameter set that differs from the defaults
- **THEN** the search started from the defaults reaches a loss at most half of the default loss within the configured generations

#### Scenario: Result stays within bounds
- **WHEN** the search finishes
- **THEN** every fitted parameter lies within its bounds

### Requirement: Fit report
A fit SHALL write the fitted parameters as JSON, the per-generation loss history, the loss of the defaults and of the result for every view, silhouette overlays for the defaults and for the result on each photograph, and the fitted STEP file. It SHALL also report a held-out check in which each photograph is left out of a fit and its loss is compared with the loss of the defaults.

#### Scenario: Report is complete
- **WHEN** a fit finishes
- **THEN** the fitted parameters, loss history, per-view losses, before and after overlays and fitted STEP file exist in the output folder

#### Scenario: Overfitting is visible
- **WHEN** a photograph is held out of a fit
- **THEN** the report states whether its loss improved over the defaults
