# Spec Delta

## MODIFIED Requirements

### Requirement: Raster remains the shipped path and boundaries hold

The pipeline SHALL work with 4x multisampling on the default framebuffer and on a 3.3 core context, SHALL NOT make the game
depend on any editor, game-link or ray-tracing module, and SHALL NOT change ray mode. On a context that cannot run the
pipeline the game SHALL draw as before and log the reason.

#### Scenario: Shipped classpath

- **WHEN** `checkShippedClasspath` runs
- **THEN** it passes

#### Scenario: Ray mode unchanged

- **WHEN** the game is launched with the ray field view
- **THEN** the ray image is produced as before and the raster pipeline does not run for the field

#### Scenario: Frame time

- **WHEN** the pilot view is drawn on the recorded reference machine and resolution
- **THEN** the steady frame time, measured by the repository's performance harness apart from loading, stays within the field-renderer
  and whole-frame budgets in the "Performance" section of the game's README (`control-line-performance`)
