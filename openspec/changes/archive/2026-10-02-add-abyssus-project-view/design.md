# Design

## Context

See `proposal.md` - Why for motivation.

Current state that shapes the approach:

- The plugin is a Kotlin + IntelliJ Platform plugin (`build.gradle.kts`, Kotlin 2.4, platform
  type/version from `gradle.properties`). Java sources exist alongside Kotlin; both are compiled.
- `plugin.xml` (`src/main/resources/META-INF/plugin.xml`) currently registers only a tool window,
  a `GltfFileType` (`LanguageFileType` with a `GLTF` language), and the GLTF parser definition.
  The `com.intellij.projectViewPane` EP (`AbstractProjectViewPane.EP`) is free to register; there is no
  `projectViewProvider` extension point.
- `net.nevinsky.abyssus.scene.SceneDto` (`src/main/kotlin/.../scene/SceneDto.kt`) is a stub: all
  fields are `private` with hardcoded values (`id = 0`, `name = null`, `ecs = null`) and there is
  no reader. `BaseLightDto` and `FogDto` exist alongside it. `net.nevinsky.abyssus.project.ProjectDto`
  already declares `val name: String` and `val scenes: List<SceneDto>`.
- There are no `*.scene` or `*.abss` files in this repository and no test fixtures for them. Any
  reader must tolerate unknown/absent formats.
- Localizable strings live in `src/main/resources/messages/AbyssusBundle.properties` via
  `AbyssusBundle.message(key, params)`.
- Icons are SVGs loaded from `src/main/resources/icons` via `IconLoader.getIcon(...)`
  (`GltfIcons.FILE` is the existing pattern).
- Tests are JUnit 4 + `testFramework(P)` (`src/test/kotlin/net/nevinsky/abyssus/MyPluginTest.kt`).

## Goals / Non-Goals

**Goals:**

- A selectable `Abyssus` view in the Project tool window that lists all `*.scene` and `*.abss`
  files.
- Each file expands into the structure of its DTO - nested objects and DTO lists - with values.
- Reading an asset never writes to disk and never breaks the tree.
- Layout: one node per asset file, one child per model property, reusing IntelliJ's
  `ProjectViewNode` machinery so refresh, rename, and VFS events work.
- Parsing lives in small, replaceable readers so a real file format can be adopted later without
  touching the view code.

**Non-Goals:**

- Editing assets from the view (rename/reorder/write-back), drag-and-drop, or an asset editor.
- Navigating from a `*.abss` node to the `.scene` files in its `scenes` folder.
- A dedicated language, lexer, or grammar for `.scene`/`.abss` (unlike `.gltf`, no `Language`
  is defined).
- Custom view columns beyond the name; the view stays name-only.
- Refactoring the existing tool window / OpenGL panel.

## Decisions

### 1. Register a `projectViewPane`, not a new tool window

Use `<projectViewPane implementation="...AbyssusProjectViewPane"/>` in `plugin.xml`. The pane
extends `AbstractProjectViewPane` (id `Abyssus`, localized title, icon, `weight`) and builds
its tree with `createStructure()` over `ProjectViewNode`s.

- Chosen over a separate tool window: the request is for a view in the project panel, and
  a project view pane gives directory grouping, VFS-driven refresh, rename, and
  "scroll from source" for free.
- Alternative: a standalone `ToolWindowFactory` (like `MyToolWindowFactory`). Rejected because it
  would have to re-implement refresh and gives up the view-selector integration.

### 2. Flat top level of asset nodes; no directory nodes

The pane's (hidden) root node's children are the asset nodes themselves: every `.abss` project
first, then any `.scene` that is not part of a project. A scene belongs to a project when it sits
in a `scenes` folder whose parent holds an `.abss` file, and is then shown only inside that
project. Directories are never rendered, so the `scenes` folder does not appear.

- An `AbyssusAssetNode` per asset file performs the presentation; discovery: walk the content roots with
  `FilenameIndex.getVirtualFilesByName(...)` or a `VirtualFileVisitor` whose predicate is
  `virtualFile.extension in SUPPORTED_EXTENSIONS` (`setOf("scene", "abss")`, exact lowercase
  suffix match - satisfies the spec's case-sensitivity and `.scene.bak` scenarios; a glob match
  on `*.scene` would wrongly match `.SCENE` and `.scene.bak`).
- The scope must include excluded and non-source roots, so the search runs over
  `ProjectRootManager.contentRoots` walked recursively rather than a default
  `GlobalSearchScope` (which honours exclusions).

Alternative: a `TreeModel` that reimplements the whole directory tree. Rejected - duplicating
platform refresh semantics is unnecessary. (`StructureViewModel` is the Structure tool window's
model and is not used here.)

### 2a. Eye toggle is painted and hit-tested by the tree, and writes through a document

The pane's tree (`ProjectViewTree` subclass) paints the eye at the right edge of the visible
rectangle for rows whose entry has an `enabled` state, and a mouse listener hit-tests the same
rectangle. A renderer-based icon cannot sit at the right edge, so it is not used. Each entry
knows its source file and key path (`DtoValue.Obj.source`, restarted for scenes listed under a
project); `toggleEnabled` parses that file's document with Gson (no HTML escaping, nulls kept),
flips the boolean in a `WriteCommandAction` (undoable), saves, and refreshes the pane. Number
and key text round-trip unchanged, so toggling twice restores the original bytes.

### 3. DTOs expose one uniform ordered property list for rendering

Keep the typed fields (`SceneDto.fog`, `ProjectDto.scenes`, ...) so callers can read them
directly, and additionally give every DTO a single uniform accessor producing an ordered
`List<DtoProperty>` used by the view. `DtoProperty` carries a name and a value that is either a
scalar (including `null`), a nested DTO, a list of DTOs, or a generic JSON object/array - the view
then renders that value recursively without knowing which type it is. The generic JSON value
holds sections without a fixed schema, notably `SceneDto.ecs` (`entities` is a map keyed by
entity id, with `components` of varying shape and `class` discriminators): object keys become
property names, array items become indexed children.

- Chosen over reflection/visitor walks over fields: self-describing, order is explicit, and no
  dependence on JVM field names (which Kotlin may mangle).
- `ProjectDto` already exposes its fields publicly; it only needs the ordered property list (and
  `BaseLightDto`/`FogDto`/`SceneDto` need readable accessors at all - they are stubs today).
- Alternative: JVM reflection over DTO fields. Rejected - fragile under Kotlin name mangling, no
  ordering guarantee, and it mixes model concerns into the UI layer.

### 4. Each asset file gets one node; its children are DTO property nodes

An asset node is a `ProjectViewNode` wrapping the `VirtualFile`/`PsiFile`; its `getChildren()`
returns `DtoProperty` nodes (not more `ProjectViewNode`s) so expanding a file cannot duplicate
it in the parent directory model (the "expanding a file does not duplicate it" scenario). The
same node class serves both extensions; its icon and reader come from the extension.

The node keeps `ValueWithDependencies`/`getPresentation()` caching keyed on the file so
`getChildren()` is not re-parsed on every repaint; the presentation uses the file name
(`forest.scene`, `game.abss`) and a `ValueCalculation` so it updates when the VFS reports the
file changed. List elements render as `name` when the element DTO has a name, otherwise
`scenes[0]`, `scenes[1]`, ... - a localizable format string.

Alternative: children as `ProjectViewNode`s over a synthetic in-memory `VirtualFile`. Rejected -
inventing fake files confuses rename/open-in-editor and doubles refresh cost.

### 5. One reader interface per DTO type, selected by extension

A common interface - `AssetReader { fun read(file: VirtualFile): AssetReadResult }` where the
result carries either the ordered `DtoProperty` tree or a failure message - implemented twice:
`SceneReader` (produces `SceneDto` properties) and `ProjectReader` (produces `ProjectDto`
properties, including its `scenes` list). The node picks the reader by extension.

- `ProjectReader` fills `ProjectDto.scenes` by reading every `*.scene` file in the `scenes`
  directory beside the `.abss` file with `SceneReader`, ordered by file name (the format
  declares no order). A missing `scenes` folder yields an empty list; one unreadable scene
  becomes a placeholder entry and does not fail the project. These scene entries are
  display-only: no navigation to the files.
- The default implementations parse the file as JSON (libGDX serialises assets as JSON with
  `class` discriminators) and return a failure result rather than throwing when content is not
  readable.
- Failure path: the node shows a localizable placeholder from `AbyssusBundle` (e.g.
  `assetParseError=<message>`) and stays a leaf, so one bad file cannot break the rest of the
  tree.
- Files are read via `ReadAction` / `runReadAction` on PSI or `VirtualFile.contentsToByteArray`
  outside the EDT-blocking path; parsing never writes.
- Alternative: one reader that sniffs content to decide the DTO type. Rejected - the extension is
  the authoritative, user-visible signal and sniffing couples both formats' schemas.
- Alternative: reuse the existing GLTF JSON/PSI infrastructure. Rejected - `.scene`/`.abss` are not
  GLTF and the generated PSI is bound to the `GLTF` language.

### 6. Plain `FileType`s for `.scene` and `.abss`, not `LanguageFileType`s

`SceneFileType` and `AbyssusProjectFileType` both extend `FileType` (not `GltfLanguage`), with
`getDefaultExtension()` of `scene` and `abss` respectively, and icons loaded from
`/icons/scene_file_icon.svg` and `/icons/abss_file_icon.svg` following `GltfIcons`. Both are
registered via `<fileType>` in `plugin.xml`.

- Chosen because no language/parser exists for these extensions and the goal is recognition plus
  distinct icons.
- Alternative: reuse the `GLTF` language with `LanguageFileType`s. Rejected - it would make these
  files parse with the GLTF grammar and produce bogus PSI/errors.
- Registrations are additive: `.gltf` handling is untouched (separate `FileType` instances,
  distinct extensions).

## Risks / Trade-offs

- **Unknown `.scene`/`.abss` on-disk formats** → the default JSON readers may not match real
  files. Mitigation: each reader is one interface with one implementation; fixtures in
  `src/test/testData` plus a reader swap are isolated. Tracked as an open question below.
- **Large projects: walking every content root on refresh** → slow first paint. Mitigation:
  `FilenameIndex`-backed discovery with a cached `CachedValue` invalidated on VFS changes, and
  lazy `getChildren()` so only expanded directories parse.
- **Repeated parsing per expansion** → Mitigation: per-`VirtualFile` cache of the parsed result
  tied to the file's modification stamp, reused by the property nodes.
- **DTO property nodes are not `ProjectViewNode`s**, so some platform features (selection
  mapping, "find usages" on a property) will not apply to property rows → accepted; they are
  decorative views of file-derived data.
- **The pane is not selected automatically**: a pane only appears in the view selector; making it
  the initial view would need to override the user's persisted choice, so it is left to the user.

## Migration Plan

Additive only; no data migration. Rollback = remove the `projectViewPane` and both
`<fileType>` entries from `plugin.xml` and the `projectView` package.

## Open Questions

(Resolved: both formats are plain JSON, as seen in `src/test/testData/project/Untitled`; there
is no libGDX wrapper.)

- Should property nodes be selectable/expandable-on-click, or is click-through to open the file in
  an editor the intended behaviour? This affects only interaction polish in the view.
