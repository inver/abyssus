# Spec Delta

## Purpose

Presents authored menus and overlays in standalone games with consistent layout, live typed data, validated write-back and explicit game actions.

## ADDED Requirements

### Requirement: Shared screen and overlay presentation
Games SHALL load registered UI assets and present a standalone screen or layered overlays using the same layout and styling behavior as editor preview. Showing or hiding UI SHALL NOT change authored documents. Overlays SHALL declare modal or nonmodal input behavior; the game SHALL control whether gameplay pauses.

#### Scenario: Pause overlay
- **WHEN** the game opens a modal pause overlay over its scene
- **THEN** the scene remains visible, the overlay blocks input to lower layers, and closing it restores the previous UI focus
- **AND** gameplay pauses only if the game requests that separately

#### Scenario: HUD input
- **WHEN** a nonmodal HUD is visible and input occurs outside its interactive widgets
- **THEN** the input remains available to the game

### Requirement: Typed live values
UI assets SHALL declare named number, boolean and string bindings with initial sample values and read-only or writable access. Widgets SHALL update from published game values, including text, progress, visibility and enabled state. Missing or incompatible values SHALL yield a visible diagnostic and declared fallback without stopping unrelated widgets.

#### Scenario: Live health
- **WHEN** the game publishes health 25 and maxHealth 100
- **THEN** the bound bar shows one-quarter progress and the bound health label updates without rebuilding the screen

#### Scenario: Invalid progress range
- **WHEN** a bar receives a maximum of zero
- **THEN** it shows empty progress with a diagnostic and does not divide by zero

### Requirement: Validated write-back
Sliders, checkboxes and text fields SHALL submit typed changes only to writable bindings. The game SHALL accept, reject or normalize a proposed value. Widgets SHALL reflect the authoritative result, show rejection feedback and avoid emitting write events when merely receiving published values.

#### Scenario: Normalized setting
- **WHEN** a volume slider submits 0.76 and the game accepts it as 0.75
- **THEN** all volume-bound widgets show 0.75 without a feedback loop

#### Scenario: Rejected edit
- **WHEN** a text field submits a name rejected by the game
- **THEN** it restores the authoritative name and shows the rejection reason

#### Scenario: Read-only target
- **WHEN** a slider targets a read-only score binding
- **THEN** editing is disabled and a binding diagnostic explains the mismatch

### Requirement: Navigation and named actions
Buttons SHALL support opening screens, going back, showing and closing overlays, or emitting a named game action. Invalid navigation targets and unhandled actions SHALL be reported without leaving the current UI unusable. Pointer input, Tab/Shift-Tab focus traversal and Enter/Space activation SHALL be supported; hidden and disabled widgets SHALL be skipped.

#### Scenario: Open settings and return
- **WHEN** the user activates Settings and then Back
- **THEN** the settings screen opens and the prior screen and focus are restored on return

#### Scenario: Start game
- **WHEN** the player activates a button bound to startGame
- **THEN** the game receives one startGame action and decides how gameplay starts

