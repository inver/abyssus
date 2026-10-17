# Antialiasing verification — project persistence revision

The user replaced the original IDE-wide preference with a native project setting. The previous application service
and configurable are removed. The current implementation uses project Properties and the `.abss`
`sceneViewAntialiasing` field, with 4× omitted.

## Focused checks

- `ProjectAntialiasingSettingsTest`: parsing, validation, independent native projects, ignored old application
  preferences, default omission, isolated writes, Undo/Redo, unsaved edits and settings notifications.
- `ProjectAntialiasingPropertiesTest`: project combo, persistence, native validation, Undo/Redo, text refresh,
  unchanged scene/asset files, and absence of the old IDE configurable.
- `SceneFileEditorTest`: owning-project initial/live values, independent projects in one IDE project, new/replaced
  views, disposal, standalone 4×, Undo/Redo, unsaved edits and Play preservation.
- The focused run including the above classes and `ProjectFpsSettingsTest` passed. A subsequent review requested an
  additional external VFS regression; that case requires the final verification run.
- Existing pure fallback and real-window GL tests are retained with the shared enum's new package.

## Full checks

Final full-suite and GL results are pending for this revision. The clean check caught a recurrence of the previously
repaired native water binding after its annotations disappeared from the shared working copy. The same small
`SceneDto` binding repair was restored under the user's existing instruction to fix all test failures; unrelated
concurrent work was left intact.

## Review

Independent review found no important defects in native validation, default omission, isolated document edits,
owning-project filtering, disposal or Play preservation. The requested external VFS regression was added, and the
shared enum's comment now describes its native document field.

## Manual checks

Task 5.1 remains open: the changed project Properties workflow requires visual IDE checks on a fixture copy, including
Ray Tracing, Play, hidden tabs, reopening, independent projects and macOS minimise/restore. No native desktop
interaction tool is available in this session; automated real-window GL checks do not replace these workflows.

## Earlier baseline

Before the project-persistence revision, all test failures reported in the first antialiasing run were repaired and
`./gradlew check --continue --console=plain` passed. Those repairs restored native water binding, normalized a boxed
numeric assertion, synchronized a README example and the current action title, and replayed documented post-migration
ray settings in the foliage reproduction tests while preserving historical source/manifest provenance and strict
checksum assertions. Earlier log: `/private/tmp/abyssus-test-repair-final-check.log`.
