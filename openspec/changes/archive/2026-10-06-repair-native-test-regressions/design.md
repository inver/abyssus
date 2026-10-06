# Design

## Context

See proposal.md. The baseline lists 69 failures in the parent change's test-failures.md. Examples directly
reproduced include ecs.entities access against a flat ecs map, String comparisons against MetaType, generated scene
UUID expectations against id 0, and an HDR test reading a file deleted when the fixture switched to sky.exr.

## Goals / Non-Goals

Preserve the purpose and strength of each assertion while replacing obsolete setup. No production source changes,
implicit document migration, ignored failures, removed tests or weaker preservation/undo checks.

## Decisions

Repair literal JSON at the test source to match native identity and asset kind; do not relax admission in production.
Prefer self-contained test inputs when coordinates, metadata or settings are explicitly pinned; shared fixtures may
be read but are not edited through the IDE. Expected text is still compared exactly when proving preservation.
Existing data classes and enum contracts determine expected types. Rejected-input tests remain rejected inputs.

Separate test source ownership across sceneview, properties/terrain, and projectview/ecs/root tests. Build execution
stays centralized because plugin tests share the sandbox and report directories. Existing CPU tests remain headless;
GL tests retain their explicit flag. No new production threading or service lifetime is introduced.

The user authorized the existing TinyEXR library's native classifiers on the root test runtime. They are declared
with `testRuntimeOnly`, so EXR preview tests can decode filesystem-backed inputs without changing plugin packaging.
The tree suite uses a stable scene snapshot under `project/Tree`, independent of the interactive Untitled scene.
Asset bean rows retain the stored-property order and complete derived rows; the test recognizes both observed
JVM getter-discovery orders and verifies repeated reads keep the same order. Face-edit preservation compares
both texts with terminal whitespace removed, matching the existing assertion's scope.

## Risks / Trade-offs

Mistaking a production regression for stale setup → trace each mismatch to current implementation and its spec;
report a genuine behavior conflict before editing production. Weakening tests → retain exact JSON and undo checks,
review every assertion change, run focused cases and full check. Shared fixture drift → pin test-owned values rather
than mutate committed scenes to satisfy one test.
