# Proposal

## Why

The repository has no entry point for an AI coding agent: no `CLAUDE.md`/`AGENTS.md`, and the
`README.md` is still the IntelliJ plugin template with a user-facing section bolted on. What an
agent (or a new contributor) needs to be productive is scattered or only in people's heads: how
to build and test, the two-module layout (the plugin and the plain-JVM `gdx-model` library forked
from Mundus), that `src/main/gen` is generated, that `Gdx.*` is process-global inside the IDE, that
GL tests are opt-in, that every scene-file write goes through one undoable, formatting-preserving edit, how the ECS
round trip keeps Mundus data it doesn't model, and how `openspec/` and `docs/superpowers/` relate. Every session
re-derives this from the source, which is slow and error-prone, and the plugin keeps growing (ECS, model rendering,
the properties panel, scene cameras and gizmos), so the cost rises.

## What Changes

- Add a root `AGENTS.md`, the tool-neutral entry point, kept short (a map, not a manual):
  what the project is, build/test/run commands, layout, hard rules and gotchas, workflow
  (OpenSpec for changes), and links into `docs/`. Add a `CLAUDE.md` that only imports `AGENTS.md`.
- Add `docs/ai/` with focused, link-dense reference pages an agent loads on demand:
  `architecture.md` (modules, data flow, threading), `glossary.md` (Mundus/Abyssus terms: scene,
  project, asset, ECS, `.abss`), `file-formats.md` (`.scene`, `.abss`, asset `meta.json`),
  `conventions.md` (Kotlin/Java split, tests, JSON handling, no-write rules) and `testing.md`.
- Add `docs/README.md` indexing `docs/ai/`, `docs/superpowers/` (historic design/plan notes) and
  `openspec/`, and stating which is the source of truth for what.
- Replace the template boilerplate in `README.md` with an accurate project description; keep the
  `<!-- Plugin description -->` markers because `patchPluginXml` requires them.
- Add package-level `README.md` stubs only where code is non-obvious (`sceneview`, `projectView`, `ecs`),
  next to the existing `gdx-model/README.md`.
- Add a shell consistency check (`scripts/check-docs.sh`, see `design.md`) so docs do not rot:
  every repo path referenced in `AGENTS.md`/`docs/ai` must exist.

No production code, build logic or runtime behavior changes. Nothing is **BREAKING**.

## Capabilities

### New Capabilities
<!-- None: documentation only, no spec-level behavior. -->

### Modified Capabilities
<!-- None. The change sets `skip_specs: true`. -->

## Impact

- New files: `AGENTS.md`, `CLAUDE.md`, `docs/README.md`, `docs/ai/*.md`, `scripts/check-docs.sh`,
  `src/main/kotlin/.../sceneview/README.md`, `src/main/kotlin/.../projectView/README.md`,
  `src/main/kotlin/.../ecs/README.md`.
- Modified: `README.md` (template text only; plugin description section is still extracted into
  `plugin.xml` by `patchPluginXml`, so its content must stay valid Markdown and the markers intact).
- Modified: `CHANGELOG.md` (one "Unreleased" entry).
- No dependency, API or plugin behavior impact. The docs describe what is committed at HEAD. The one other change
  still open, `show-project-assets`, is documented only as far as its code is committed. Each later change updates
  the docs it invalidates.
