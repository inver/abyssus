# Spec Delta

## MODIFIED Requirements

### Requirement: PBR models are lit by the sky

A model drawn with the PBR shader SHALL take its ambient diffuse light from the irradiance cube in the direction of
its surface normal, and its ambient specular light from the specular cube in the direction of the view reflected
about the normal, at the mip level for its roughness, combined with the shader's existing environment BRDF
approximation and occlusion. A model drawn with the PBR shader while no HDR sky lights the scene SHALL use a shader
compiled without the sky path, identical to today's. While Ray Tracing mode is active, PBR specular reflections SHALL instead include scene geometry and use the existing sky environment for rays that miss geometry, respecting metallic and roughness; diffuse HDR illumination SHALL retain the existing behavior. Disabling Ray Tracing SHALL restore the raster specular path.

#### Scenario: Diffuse follows the sky

- **WHEN** a rough white PBR sphere is lit by a sky that is bright above the horizon and dark below it
- **THEN** the sphere is bright on top and dark underneath

#### Scenario: Smooth metal reflects the sky

- **WHEN** a PBR sphere with metallic 1 and roughness 0 is lit by a sky with a small bright patch
- **THEN** a sharp reflection of the patch appears on the sphere, and with roughness 1 it spreads into a broad
  soft highlight

#### Scenario: No sky, no change

- **WHEN** Main Scene, which names no skybox, is drawn with Ray Tracing off
- **THEN** Model 6 (PBR) is drawn exactly as before this change

#### Scenario: Geometry replaces the sky at a reflection hit

- **WHEN** Ray Tracing is active and a reflected direction intersects scene geometry in front of the HDR sky
- **THEN** the reflected geometry appears in that direction while misses still show the HDR sky

#### Scenario: Disable Ray Tracing

- **WHEN** the user disables Ray Tracing in an HDR-lit scene
- **THEN** PBR materials resume the existing sky specular behavior and diffuse sky lighting continues
