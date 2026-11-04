# Spec Delta

## Purpose

States the hardware Control Line promises to run on, its frame-time, hitch and allocation budgets, the evidence its
performance workload must produce to judge them, and the frame-cost behaviours that keep the game within them.

## ADDED Requirements

### Requirement: Minimum spec and budgets are recorded

The game's README SHALL name its minimum-spec and reference machines (GPU, OS, driver, JDK) and its budgets: p99 whole-frame
time, flight-start and debrief-open hitch, and steady allocation per frame. A budget the maintainer has not approved SHALL
be marked proposed, and no budget SHALL be claimed met without a report from the machine it names.

#### Scenario: Budget table

- **WHEN** a reader opens the README's performance section
- **THEN** it lists each budget with its value, its machine and whether it is approved or proposed

### Requirement: The workload measures the whole frame

The game's performance workload SHALL report CPU time percentiles (p50, p95, p99) for the whole frame and separately for
input and flight, the field renderer, and the UI, while a Watch AI flight is flown over the field at a fixed step.

#### Scenario: Flying workload

- **WHEN** the workload runs with GL tests enabled on the reference machine
- **THEN** the report holds `MEASURED` percentiles for `frame`, `flight`, `field` and `ui`, from a flight in progress

#### Scenario: Window not presented

- **WHEN** the workload runs while the screen is locked or the window is not visible
- **THEN** the report marks its CPU percentiles as not comparable, naming why, and keeps the GPU times, counters, allocation
  and hitches

#### Scenario: Coverage agent attached

- **WHEN** the workload's JVM runs with a coverage or profiling agent attached
- **THEN** the report names the agent and is never compared with a baseline taken without it

### Requirement: The workload measures GPU time per pass

Where the context supports timer queries, the workload SHALL report GPU time percentiles for the sky, shadow, terrain,
model and line passes, and a total. Where it does not, it SHALL report the GPU fields as unavailable with the reason and
SHALL NOT substitute CPU time.

#### Scenario: Context without timer queries

- **WHEN** the workload runs on a context that reports no timer-query support
- **THEN** the GPU fields are empty and the report gives the reason

### Requirement: The workload reports render counters

The workload SHALL report draw calls, shader switches, texture bindings and vertices per frame, averaged over the samples,
for the main view and the shadow pass.

#### Scenario: Counters present

- **WHEN** a measured report is read
- **THEN** it holds the four counters for both the main view and the shadow pass

### Requirement: The workload measures hitches

The workload SHALL report the wall time from the moment a flight is requested to the first frame drawn with it, and the
same for opening the debrief, as the median and the maximum of at least five repetitions after a warm start.

#### Scenario: Flight start

- **WHEN** the workload starts a flight five times from plane select
- **THEN** the report holds the median and maximum flight-start time in milliseconds

### Requirement: Allocation per frame stays within budget

In a steady flight on the reference machine, allocation per whole frame SHALL stay within the recorded budget, and the
workload SHALL fail a run that exceeds it.

#### Scenario: Steady flight

- **WHEN** the workload measures a steady Watch AI flight on the reference machine
- **THEN** the reported allocation per frame is at or under the budget, or the run fails naming the measured value

### Requirement: Models outside the view are not drawn

A model whose bounds lie wholly outside the camera's view SHALL NOT be submitted for drawing. A model wholly outside the
sun's shadow volume SHALL NOT be drawn into the shadow map. A model playing an animation clip SHALL always be submitted.
The image SHALL NOT change.

#### Scenario: Looking away from the buildings

- **WHEN** the camera faces open field with the facilities behind it
- **THEN** fewer model draw calls are reported than when facing the facilities, and the pixel evidence matches its recording

#### Scenario: Rebuilt model

- **WHEN** a model asset is rebuilt with larger bounds
- **THEN** culling uses the new bounds from the next frame

### Requirement: Starting a flight stays within its hitch budget

Starting a flight or opening the debrief SHALL stay within the recorded hitch budget on the reference machine. A flight SHALL
start with the field already shown, without the field's models disappearing for a frame.

#### Scenario: Fly again

- **WHEN** the player flies, lands and flies again
- **THEN** the second flight starts within the hitch budget, and no frame between the two shows the field without its models

### Requirement: The shadow pass stays within its share of the GPU budget

On the minimum-spec machine the shadow pass SHALL take no more GPU time than the share of the frame recorded for it. When
the static shadow cache is used to meet that share, static casters SHALL be redrawn into the map only when the sun's shadow
camera changes, moving models SHALL be drawn every frame, and the shadows shown SHALL match those drawn without the cache.

#### Scenario: Plane flying over a cached map

- **WHEN** the plane flies through a static building's shadow with the cache on
- **THEN** both the building's and the plane's shadows are drawn, and the pixel evidence matches the uncached run

### Requirement: Colliders are not rebuilt for every flight

A collider shape built from an asset SHALL be built once per game run for each asset, shape kind and scale, and reused by
later flights. A convex hull SHALL be built from at most a bounded number of input points, so its build time does not grow
with the model's vertex count. The collision behaviour SHALL stay within the flight specs' tolerances.

#### Scenario: Second flight of the Stunter

- **WHEN** the player flies the Stunter, returns to the menu and flies it again
- **THEN** the second flight builds no convex hull, and its start stays within the flight-start hitch budget

#### Scenario: Detailed model

- **WHEN** a plane's model has 84,826 vertices, as the bundled Stunter does
- **THEN** its hull is built from at most the bounded number of points, and a crash and a landing on the field behave as
  `control-line-flight` requires

### Requirement: The sky stays within its share of the GPU budget

On the reference machine the sky, clouds included, SHALL take no more GPU time than the share of the frame recorded for it.
The automatic cloud step-down SHALL act on the approved frame budget, not only below 30 frames a second, and SHALL log each
step as today.

#### Scenario: Volumetric clouds over budget

- **WHEN** volumetric clouds keep the frame over the approved budget for two seconds
- **THEN** the sky steps down to the next simpler technique and logs it

### Requirement: Shadow casters are drawn in shader order

The sun's shadow pass SHALL draw its casters grouped by depth shader, binding each shader program once per group, with an
unchanged shadow map.

#### Scenario: Shadow pass counters

- **WHEN** the workload reports the shadow pass's counters on the bundled field
- **THEN** its shader switches are at most the number of distinct depth shaders plus one, and the pixel evidence matches
  its recording
