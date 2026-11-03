# Spec Delta

## Purpose

Lets users explicitly mark individual scene model materials as transmissive and inspect glass refraction and multiple reflections within saved transport limits.

## ADDED Requirements

### Requirement: Saved per-instance optical material overrides

For a model's PBR material with a unique stable identifier, the scene SHALL accept `RenderComponent.rayTracingMaterials.<material-id>.transmission` from 0 through 1 and `ior` from 1 through 3, both finite. Defaults SHALL be 0 and 1.5 and omitted when reset. Overrides SHALL affect only that entity, survive reopening, and preserve model source and shared assets.

#### Scenario: Two instances share a model
- **WHEN** Transmission is set to 1 and IOR to 1.45 on one of two entities using the same model
- **THEN** only that entity's scene override changes, the second instance retains its original material, and reopening restores the first instance's values

#### Scenario: Reject invalid optics
- **WHEN** Transmission is set to 1.1 or IOR to 0, NaN or nonnumeric text
- **THEN** the previous value remains and no scene edit occurs

#### Scenario: Missing material after asset replacement
- **WHEN** a stored override names a material no longer present in the entity's model
- **THEN** the panel identifies the unresolved override, preserves its data and does not apply it to a different material

### Requirement: Configurable reflection depth

PBR reflections SHALL follow scene geometry up to the saved reflection limit, counting each spawned reflection direction. Zero SHALL disable scene reflection tracing and use the existing environment specular approximation. Exhausted reflection paths SHALL use a bounded terminal environment approximation. Nontransmissive blended geometry SHALL retain its existing exclusion from reflections.

#### Scenario: Mirror reflects another mirror
- **WHEN** two opaque PBR mirrors require two reflection events to reveal an offscreen colored object
- **THEN** depth 2 reveals the object while depth 1 terminates before tracing that second scene reflection

#### Scenario: Disable scene reflections
- **WHEN** reflection depth is set to 0
- **THEN** PBR surfaces use environment specular and spawn no scene reflection rays

### Requirement: Actual dielectric refraction

Explicitly transmissive opaque PBR model surfaces SHALL bend transmitted rays according to IOR, support entry and exit of a closed nonoverlapping dielectric solid, and show scene geometry or environment reached by those rays. Each transmitted surface crossing SHALL consume one refraction bounce. Refraction limit 0 SHALL disable transmission tracing and retain ordinary surface shading.

#### Scenario: Glass bends the background
- **WHEN** a closed glass object with Transmission 1 and IOR 1.5 is viewed at an angle with refraction limit at least 2
- **THEN** geometry behind it is displaced by entry and exit refraction rather than merely alpha blended

#### Scenario: Exhaust refraction depth
- **WHEN** a path reaches its saved refraction limit
- **THEN** further transmission uses a terminal environment approximation without tracing additional scene intersections

### Requirement: Fresnel and total internal reflection respect limits

Dielectric transport SHALL combine reflection and transmission using angle-dependent Fresnel weighting. Total internal reflection SHALL produce reflection rather than an invalid transmitted direction and consume reflection depth. Both limits and the ray budget SHALL remain enforced even when a path alternates reflection and transmission.

#### Scenario: Internal reflection
- **WHEN** a ray inside IOR 1.5 glass reaches an exit beyond its critical angle
- **THEN** it reflects internally within the reflection limit, with finite image values and bounded work

### Requirement: Existing transparency and editor behavior remain usable

Materials without explicit transmission SHALL retain existing alpha behavior. New optics SHALL apply in Ray Tracing mode only. Picking, overlays, camera and transform controls SHALL remain usable and editor decorations SHALL be excluded from optical rays. Unsupported optical geometry or backend features SHALL produce explained raster fallback without rewriting saved settings.

#### Scenario: Existing blended surface
- **WHEN** a scene contains alpha-blended geometry without a transmission override
- **THEN** its existing compositing and shadow rules remain and it does not automatically become refractive

#### Scenario: Unsupported transmission material
- **WHEN** transmission is requested on a masked or blended PBR material, an intersecting/nested dielectric path, or a backend without the scene optics feature
- **THEN** the view falls back to raster with a reason and keeps scene editing, settings and selection
