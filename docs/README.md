# Docs

Start with `AGENTS.md` at the repository root: commands, layout, hard rules. Pull in the pages below when a task
needs them.

## Where things are documented

| Place | Holds | Source of truth for |
|---|---|---|
| `AGENTS.md` | Commands, layout map, hard rules, workflow | How to build, test and run; what not to touch |
| `docs/ai/architecture.md` | Modules, registrations, data flow, threading, extension points | How the parts fit together |
| `docs/ai/file-formats.md` | `.abss`, `.scene`, `meta.json`, asset reachability | What the files contain and how the plugin reads them |
| `docs/ai/glossary.md` | Native Abyssus terms | What a word means here |
| `docs/ai/conventions.md` | JSON, writing files, errors, UI text, code style | How code is written |
| `docs/ai/testing.md` | Test layout, fixtures, GL tests, seams | How to test |
| Package and module `README.md`s (`sceneview`, `projectView`, `editor-core`, `gdx-model`, `core`, `runtime`, `physics`, `raytracing`, `games/control-line`) | The non-obvious parts of one package | That package's internals |
| `openspec/specs/` | One spec per capability | **Required behavior.** Read the capability before changing a feature |
| `openspec/changes/` | Changes in progress: proposal, delta specs, design, tasks | What is being changed and why |
| `openspec/changes/archive/` | Finished changes, dated | Why past decisions were made |
| `docs/reviews/` | Dated architecture and documentation audits | Findings at the reviewed revision; verify against current code |
| `docs/superpowers/` | One early design and plan (the scene view shell) | History only; superseded by the specs |
| `README.md` | User-facing feature description and the marketplace description block | What users see |
| `CHANGELOG.md` | Release notes (`[Unreleased]` first) | What changed for users |

**One place per fact.** These pages link to code and to each other rather than copying. `AGENTS.md` is the command
index; testing and module pages add focused examples. File-format details live in `docs/ai/file-formats.md`, and
required behavior lives in `openspec/specs/`. Documentation describes the implementation; a known gap against a
requirement belongs in a dated review, not an unannounced rewrite of the spec.

## Keeping docs true

- **Update in the same change:** a change that makes a page wrong (a renamed class, a new writer, a new command)
  updates that page in the same change. OpenSpec changes add a docs task for it.
- **Path check:** `scripts/check-docs.sh` fails when `AGENTS.md` or `docs/ai/*.md` name a repository path that
  doesn't exist. Run it before committing doc changes.
- **What gets documented:** only what is committed. Work in an open change is described by that change's files, not
  here.
