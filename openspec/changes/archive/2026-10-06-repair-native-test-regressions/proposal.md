# Proposal

## Why

The refactor's full plugin suite reproduces 69 baseline failures, including stale test inputs and expectations after
native document and DTO changes. The user authorized repairing tests without changing current production logic.
Assertions exposing production defects remain in place; those failures are not all test regressions.

## What Changes

- Repair test-owned JSON to use native headers, current ECS shapes and asset renderables.
- Update expected values/types to the existing runtime DTOs; keep exact preservation, undo and rejection assertions.
- Repair thumbnail test inputs after the fixture HDR file was replaced with EXR.
- Pin tree and runtime ECS regression inputs in a stable native scene snapshot; keep the interactive project intact.
- Verify focused groups and the complete check before closing prerequisite gates.

## Capabilities

No new or modified requirements. `skip_specs: true`: test-only maintenance against existing
`abyssus-document-format`, `scene-component-editing`, `abyssus-project-assets` and rendering requirements.
Native fields `format`, `formatVersion`, `ecs`, `kind` and asset settings retain their existing contracts.

## Impact

Test sources and test-owned data only, plus the user-authorized TinyEXR native classifiers on the test runtime.
Production logic, document formats, packaged runtime dependencies and unrelated Control Line work are out of scope.
This is a prerequisite for `refactor-solid-dedup` and shared validation's integration gate.
