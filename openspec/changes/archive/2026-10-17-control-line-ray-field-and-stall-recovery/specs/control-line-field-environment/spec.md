# Spec Delta

## ADDED Requirements

### Requirement: Optional ray-traced field view

The game SHALL support an opt-in ray-traced display of the field chosen at launch. Without the option the field
renders exactly as the raster requirements above describe; the option SHALL NOT change any other behavior. In ray
mode the field image SHALL come from the ray tracer and SHALL show the field's terrain, the field scene's models —
with moving planes and the pilot following their simulation transforms — plus its sunlight, sky and fog from the
current simulation state, through the game's existing camera view. Input, physics, flight, scoring and the HUD,
menus and screens SHALL behave as in raster mode and stay raster-drawn. Ray mode SHALL NOT draw foliage and SHALL
draw animated models in their bind pose; both limits SHALL be recorded in the game's README. When the ray view is
requested but no ray-tracing backend is usable — runtime or crate missing, device unsupported, or the probe refusing
it — the game SHALL start the raster field and SHALL state the reason in the window title and the application log;
it SHALL NOT fail to start and SHALL NOT silently ignore the request. If ray rendering fails after start (quality
limit, stalled frame or lost device), the game SHALL fall back to the raster field and state the reason the same
way.

#### Scenario: Ray mode shows the field through the ray tracer

- **WHEN** the game is launched with the opt-in ray field view on a machine with a usable ray-tracing backend
- **THEN** the field image is produced by the ray tracer and shows the terrain, models (moving planes included),
  sunlight, sky and fog, the window title identifies ray mode, and flying, scoring and the menus behave as in raster
  mode

#### Scenario: Runtime failure falls back like startup

- **WHEN** ray rendering fails after start with a quality limit, a stalled frame or a lost device
- **THEN** the game continues with the raster field and states the reason in the window title and the log, with no
  crash

#### Scenario: Raster default is untouched

- **WHEN** the game is launched without the opt-in option
- **THEN** the field renders exactly as before, with no ray-mode indicator and no ray work started

#### Scenario: Explicit fallback with a reason

- **WHEN** the game is launched with the opt-in ray field view but no ray-tracing backend is usable
- **THEN** the game runs the raster field with the reason stated in the window title and the log, and does not crash

#### Scenario: Documented ray mode limits

- **WHEN** ray mode draws the field while it contains foliage and an animated model
- **THEN** the image omits the foliage, the model appears in its bind pose, and the game's README lists both limits
