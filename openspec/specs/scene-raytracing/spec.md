# scene-raytracing Specification

## Purpose

An optional live ray-traced view of a scene: its availability and quality bounds, its editor integration and fallback, and the safe runtime lifecycle every backend follows — frames that complete or fail within a bound, and loading that works on any host configuration.

## Requirements

### Requirement: Bounded frame completion and stall recovery

The renderer SHALL bound the completion of an in-flight frame. A submitted frame that does not report completion within the session's frame-completion deadline SHALL be treated as a failed frame — never as indefinitely pending — and the failure SHALL be distinguishable from a lost device as a stall. The deadline SHALL have a bounded default (seconds, not minutes) and SHALL be configurable. A stalled frame SHALL NOT stop later submissions: the session SHALL release the in-flight frame, report the stall with a reason, and follow the same recovery path as a lost device — raster rendering resumes, the editing state is preserved, and the failure is communicated through the existing non-modal rendering-failure UX. Re-activating ray tracing after a stall SHALL re-probe the backend and SHALL only resume when a completed frame confirms the backend works. A frame that completes within the deadline SHALL never be treated as stalled, however slow it is. These guarantees SHALL hold on every backend through the shared frame scheduling.

#### Scenario: Stalled frame recovers to raster
- **WHEN** a backend holds one submitted frame incomplete past the frame-completion deadline
- **THEN** the stall is reported with a reason, rendering falls back to raster with the editing state preserved, and no later submission is blocked behind the stalled frame

#### Scenario: Retry after a stall works
- **WHEN** the user re-activates ray tracing after a stall and the backend now completes frames
- **THEN** the backend is re-probed and the ray view renders again

#### Scenario: Slow frame within the deadline is not a stall
- **WHEN** a frame completes, however slowly, before the frame-completion deadline passes
- **THEN** the session continues in ray mode with no fallback and no stall report

### Requirement: Backend runtime loading is independent of host initialization

Backend runtime loading SHALL succeed whether the host leaves the graphics binding's automatic initialization enabled or disables it in favor of explicit initialization. When the loader library is absent, the probe SHALL report the runtime as unavailable in both modes and ray tracing SHALL stay off; loading SHALL NOT surface an unclassified error for either configuration.

#### Scenario: Explicit initialization host
- **WHEN** the host runs with automatic initialization disabled and the loader present
- **THEN** the probe reports the same availability as with automatic initialization, and ray tracing works

#### Scenario: Missing loader with either configuration
- **WHEN** the loader library is absent, whether automatic initialization is enabled or disabled
- **THEN** the probe reports the runtime as unavailable and the scene stays in raster
