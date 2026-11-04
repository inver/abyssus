# Spec Delta

## ADDED Requirements

### Requirement: The package smoke test fails on a crash record

The Linux package smoke test SHALL fail when the started game writes a crash record, as well as when it exits early or logs
an exception. It SHALL print the record's contents.

#### Scenario: Game crashes in the smoke run

- **WHEN** the packaged game writes a crash record during the smoke run
- **THEN** the smoke test fails, naming the package, and prints the crash record

### Requirement: The game's GL tests run in continuous integration

Every build of the main branch and every pull request SHALL run the game's GL tests for the field renderer, its pipeline,
shadows and culling on a Linux runner with software OpenGL 3.3. A GL test that cannot get a context there SHALL fail rather
than be skipped. macOS and Windows startup SHALL be listed in the game's README as a hand check before a release.

#### Scenario: Pull request with a broken field renderer

- **WHEN** a pull request makes the field renderer throw on its first frame
- **THEN** the CI GL job fails on that pull request

#### Scenario: No context on the runner

- **WHEN** the CI runner cannot create an OpenGL 3.3 context
- **THEN** the GL job fails with that reason instead of reporting the tests as skipped
