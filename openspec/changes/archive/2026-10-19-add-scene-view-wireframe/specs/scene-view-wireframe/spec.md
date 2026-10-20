# scene-view-wireframe Specification

## Purpose

Defines how the Scene view shades what it draws, adding a Wireframe mode alongside Lit and Unlit so the user can inspect the edge topology of models, terrain, foliage, skybox and water.

## ADDED Requirements

### Requirement: Wireframe shading mode exists

The scene view's shading dropdown SHALL offer Wireframe as a third mode alongside Lit and Unlit, Lit being the default for every view. Choosing Wireframe SHALL make every scene-geometry draw the renderer issues draw as edges only (models, terrain meshes, foliage OBJECT layers, skybox cube and water surfaces), while lights, shadows, fog, the grid, markers, selection box and gizmos are unchanged. Wireframe SHALL apply to the raster path only: while Ray Tracing presents a frame, the ray image is drawn as usual.

#### Scenario: Shading dropdown offers Wireframe

- **WHEN** the user opens the scene view's shading dropdown
- **THEN** it lists Lit, Unlit and Wireframe

#### Scenario: A new view starts at Lit

- **WHEN** a scene view is opened
- **THEN** its shading is Lit

#### Scenario: Wireframe draws edges only

- **WHEN** shading is Wireframe and the scene has a model, a terrain, a skybox and water
- **THEN** those draw as edge lines instead of filled surfaces, and the grid, markers, selection box and gizmos are still drawn; no shadow atlas is produced while shading is Wireframe

#### Scenario: Ray Tracing keeps showing the ray image

- **WHEN** Ray Tracing is presenting a frame and the user chooses Wireframe
- **THEN** the ray-traced image is shown as usual

### Requirement: Wireframe is per view and never saved

Wireframe SHALL be view state only, never written to `.abss`, `.scene` or any asset `meta.json`, and never read back from them. A reopened view or another view of the same scene starts at Lit.

#### Scenario: Wireframe is not persisted

- **WHEN** the user sets shading to Wireframe, closes the scene view and reopens it
- **THEN** shading is Lit again
