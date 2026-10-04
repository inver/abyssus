# Spec Delta

## Purpose

Lets users save a scene's raytracing quality target, work budget and transport depth, and see those settings applied consistently after edits and reopening.

## ADDED Requirements

### Requirement: Saved scene raytracing settings

The scene SHALL store optional `rayTracing` fields `targetSamplesPerPixel`, `maxRaysPerFrame`, `maxReflectionBounces`, and `maxRefractionBounces`. Their effective defaults SHALL be 256, 2097152, 1, and 0 respectively. Reopening SHALL restore saved values; selecting a scene SHALL write nothing. Resetting a field to its default SHALL remove that field without removing unknown data.

#### Scenario: Reopen a configured scene
- **WHEN** a copy of Untitled's Main Scene is saved with target samples 512, ray budget 1048576, reflection depth 3 and refraction depth 4 and the project reopens
- **THEN** its Properties controls and subsequent ray rendering use those values

#### Scenario: Existing scene without settings
- **WHEN** Main Scene has no `rayTracing` block and the user selects it
- **THEN** the four defaults are displayed and the scene remains byte-for-byte unchanged

#### Scenario: Reset one field
- **WHEN** reflection depth is reset from 3 to 1
- **THEN** only `maxReflectionBounces` is removed, other settings and unknown fields remain, and an empty settings block is removed only if it has no other fields

### Requirement: Validate scene raytracing limits

Edits SHALL accept integer target samples from 1 through 4096, ray budgets from 1 through 67108864, and each bounce limit from 0 through 16. Invalid edits SHALL retain the saved value and explain the error without writing. Malformed saved settings SHALL be reported and prevent raytracing activation until corrected, while ordinary scene editing remains available.

#### Scenario: Invalid panel edit
- **WHEN** a user enters a fraction, nonnumeric text, or an out-of-range value in any control
- **THEN** the edit is rejected, the previous value remains displayed and the file is unchanged

#### Scenario: Invalid external edit
- **WHEN** scene text sets `maxRefractionBounces` to -1 or a settings field to null
- **THEN** Properties reports that field's error, raytracing uses raster fallback with an explanation, and the invalid file is not rewritten automatically

### Requirement: Samples and ray budget work together

Target samples SHALL mean accumulated camera samples per pixel for unchanged content at the current internal resolution. Maximum rays per frame SHALL bound all ray intersection queries in each submitted frame, including primary, reflection, refraction, shadow and retry queries. Adaptive quality SHALL respect both settings and backend resource limits; it SHALL explain fallback when even minimum-quality work cannot fit.

#### Scenario: Small work budget
- **WHEN** a valid ray budget permits fewer samples per submitted frame than the target
- **THEN** stable rendering accumulates samples over multiple frames without exceeding that budget and stops at the target

#### Scenario: Increase transport depth
- **WHEN** the user increases bounce limits while leaving the ray budget unchanged
- **THEN** rendering reduces work per submission as needed rather than exceeding the budget

#### Scenario: Minimum work cannot fit
- **WHEN** the saved ray budget is too small for one sample at the minimum internal resolution
- **THEN** the view uses raster fallback and explains that the ray budget is too small, without altering the saved setting

### Requirement: Settings refresh every view

Accepted edits, external scene changes, Undo and Redo SHALL refresh the panel and every open view of that scene. Changed rendering settings SHALL invalidate accumulation and older results. Edits SHALL be single undoable commands preserving unrelated scene text, number spelling and key order; equal-value and superseded-value edits SHALL not overwrite data.

#### Scenario: Undo with two views
- **WHEN** target samples changes from 256 to 512 with two Main Scene views open and the user chooses Undo
- **THEN** the original scene text is restored, both views and the panel use 256, and results rendered under the previous settings are discarded

#### Scenario: Superseded value
- **WHEN** a field changes externally before an older populated editor commits
- **THEN** the newer value is preserved and the panel refreshes with a conflict explanation

### Requirement: Runtime enable state remains independent

Saving settings SHALL NOT enable Ray Tracing or persist its switch state. Settings SHALL be editable with no view open or on unsupported hardware. Newly opened views SHALL start with Ray Tracing off and use the saved settings when enabled.

#### Scenario: Edit before opening a view
- **WHEN** a user changes the ray budget with no scene view open and later opens one
- **THEN** the view starts with Ray Tracing off and reads the saved budget when enabled
