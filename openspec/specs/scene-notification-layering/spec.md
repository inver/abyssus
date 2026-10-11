# scene-notification-layering Specification

## Purpose
Keep IntelliJ notifications visible and interactive above the native scene canvas, with safe restoration of live rendering after an overlay leaves.

## Requirements

### Requirement: IDE overlays remain visible above the scene
The scene viewer SHALL allow overlapping IDE notification balloons to remain visible and interactive. It MAY pause scene rendering and display the last frame while overlap lasts. It SHALL resume live rendering when overlap ends, without changing scene data.

#### Scenario: Notification overlaps the scene
- **WHEN** an IDE notification overlaps the scene viewer
- **THEN** its text and actions remain fully visible and clickable above the last rendered scene frame

#### Scenario: Notification disappears
- **WHEN** all overlapping overlays disappear or move away
- **THEN** live scene rendering resumes once it is safe to render

#### Scenario: Notification outside the scene
- **WHEN** an overlay does not intersect the scene or is below it in z-order
- **THEN** the scene continues rendering normally
