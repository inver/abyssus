## ADDED Requirements

### Requirement: IDE overlays remain visible above the scene
The scene viewer SHALL allow overlapping IDE notification balloons to remain visible and interactive. It MAY pause scene rendering and display the last frame while overlap lasts. It SHALL resume live rendering when overlap ends, without changing scene data.

#### Scenario: Notification overlaps the scene
- **WHEN** a lightweight IDE notification overlaps the native scene canvas
- **THEN** the native surface is hidden and the scene is represented by a Swing snapshot beneath the notification

#### Scenario: Notification disappears
- **WHEN** all overlapping overlays disappear or move away
- **THEN** the live canvas is restored and rendering resumes after its surface safety gate opens

#### Scenario: Notification outside the scene
- **WHEN** an overlay does not intersect the scene or is below it in z-order
- **THEN** the scene continues rendering without framebuffer readback
