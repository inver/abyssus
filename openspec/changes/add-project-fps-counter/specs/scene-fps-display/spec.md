# Spec Delta

## Purpose

Lets users monitor the frame rate of each scene viewport through an optional FPS overlay controlled by project view settings.

## ADDED Requirements

### Requirement: Project FPS preference

The FPS counter SHALL be off when no preference is saved. `Show FPS` in project properties SHALL apply to all scene views in the same IDE project, including later views, and SHALL be remembered when the project is reopened. Other IDE projects SHALL retain their own preference. Saving the preference SHALL NOT alter Mundus `.abss`, `.scene` or asset files.

#### Scenario: Enable existing views
- **WHEN** the user checks `Show FPS` while two scene views are open in the same IDE project
- **THEN** each visible view shows its own counter on its next rendered frame

#### Scenario: Disable existing views
- **WHEN** the user unchecks `Show FPS`
- **THEN** each visible view removes its counter on its next rendered frame without reopening

#### Scenario: Reopen the project
- **WHEN** a user enables `Show FPS`, closes the IDE project and reopens it
- **THEN** the checkbox is checked and newly opened scene views show the counter

#### Scenario: Independent IDE project
- **WHEN** the user enables `Show FPS` in one IDE project and opens a different IDE project without a saved preference
- **THEN** the second project's counter remains off

### Requirement: Readable viewport overlay

An enabled counter SHALL show `FPS: <integer>` in the upper-right corner of the scene viewport, with readable contrast against light and dark backgrounds. Before the first complete sample it SHALL show `FPS: --`. The overlay SHALL follow viewport resizing and display scaling, remain visible over loading feedback, and preserve camera navigation, picking and gizmo interaction.

#### Scenario: Enable on the fixture scene
- **WHEN** the user enables the counter in a copy of `Untitled` and opens `Main Scene`
- **THEN** the viewport shows `FPS: --` initially and a numeric FPS value after a complete sample

#### Scenario: Resize and navigate
- **WHEN** the enabled viewport is resized and the user orbits or selects `Model 0`
- **THEN** the counter stays in the upper-right corner and navigation and selection continue to work

#### Scenario: Loading feedback
- **WHEN** an enabled view is rendering loading feedback
- **THEN** its FPS counter remains readable above that feedback

### Requirement: FPS measures completed viewport frames

Each counter SHALL measure completed frames of its own viewport per elapsed second, rounded to the nearest nonnegative integer. It SHALL update after at least one second of active sampling and use actual elapsed time, including rendering and presentation delays. Scheduled callbacks without a completed frame SHALL NOT count. The measurement SHALL describe viewport presentation in both ordinary and Ray Tracing modes.

#### Scenario: Stable frame cadence
- **WHEN** a viewport completes 60 frames in one second of active sampling
- **THEN** its counter shows `FPS: 60`

#### Scenario: A slow frame
- **WHEN** a viewport completes 10 frames over a two-second sample because rendering stalls
- **THEN** its counter shows `FPS: 5` rather than its requested update rate

#### Scenario: Separate viewport rates
- **WHEN** two visible viewports complete frames at different rates
- **THEN** their counters show their respective measured rates

#### Scenario: Ray Tracing presentation
- **WHEN** Ray Tracing is active and a viewport redraws using the latest available ray image
- **THEN** the counter measures completed viewport frames, including redraws of that image, rather than ray-backend image production

### Requirement: FPS sampling follows view lifecycle

Disabled, hidden, minimized, zero-size, failed or disposed views SHALL NOT perform counter-driven rendering or count frames. Enabling the counter or resuming a suspended view SHALL discard its previous sample and show `FPS: --` until a fresh sample completes. Counter resources SHALL follow the view's graphics lifecycle without causing unsafe rendering or retaining closed views.

#### Scenario: Hidden view resumes
- **WHEN** an enabled view is hidden for ten seconds and then shown again
- **THEN** it starts a fresh FPS sample and does not include the hidden time or display the previous numeric sample

#### Scenario: Toggle on again
- **WHEN** the counter is disabled and then re-enabled
- **THEN** its first display is `FPS: --` and the next numeric value uses only new completed frames

#### Scenario: Unsafe surface or closed view
- **WHEN** an enabled view becomes zero-size, minimized or disposed
- **THEN** the counter causes no rendering work on that surface
