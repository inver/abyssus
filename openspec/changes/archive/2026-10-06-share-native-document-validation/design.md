# Design

## Context

See proposal.md. The existing plugin validator is already pure Jackson logic; core loaders and runtime raw ECS helpers bypass it. Metadata binding deduplication remains in the following refactor.

## Goals / Non-Goals

**Goals:** one admission implementation available without the IDE, before binding and before raw ECS mutation or output; preserve editor localization and valid binding.
**Non-Goals:** generic validation in JsonProcessor, new format semantics, metadata table consolidation, changes to source assets or document writers.

## Decisions

Move the validator and rejection types to `core.format`. Keep Kotlin aliases at the former plugin location for existing source callers; there is no published binary compatibility guarantee for these internal helpers. Inject constructor-built validators with defaults into project/scene/metadata and raw ECS boundaries. A shared validator avoids duplicating rules; plugin wrappers alone would leave JVM callers unguarded.

Validate parsed document trees in ProjectLoader, SceneLoader and AssetMetaLoader before DTO binding. UnsavedMetaLoader checks the same shared validator before its existing parsing. AssetMetaReader keeps its validation. Rejections continue through existing throwing or null-and-log APIs; unsaved unsupported metadata must not fall back to saved metadata. Cache rejection warnings per saved metadata revision.

EcsLoader validates before adding any entity; EcsWriter validates carried extras and completed output, and render serializers/deserializers guard direct renderable access. Inspect only reserved component paths, accepting the short name and exact runtime built-in fully qualified RenderComponent name; never reject arbitrary extension components because their name has the same suffix. No header is needed for raw helpers.

All checks run synchronously on the caller's thread with no Swing, IntelliJ, IO writes or GL. Preserve parser nodes; validation does not mutate or re-serialize input.

## Risks / Trade-offs

- Previously accepted unmarked test inputs will now reject: update only supported native test inputs with the required header; keep rejection cases explicitly unmarked.
- Existing unrelated check failures may prevent the final full gate: record exact failures without fixing unrelated game work or broadening this prerequisite.
- Kotlin aliases preserve source use, not existing binary signatures: build editor and extension modules together and retain rejection message contents.

## Migration Plan

No data migration. Complete and verify this change before phase 1 of refactor-solid-dedup; that refactor preserves the newly guarded boundaries. Reverting this change reopens the admission gap but never rewrites user documents.
