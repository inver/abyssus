# Design

## Context

See proposal.md - Why. Current state: no `AGENTS.md`/`CLAUDE.md`; `README.md` is template text plus an
"Abyssus view" user section; `docs/superpowers/` holds one historic design + plan (scene view shell);
`openspec/specs/` holds 13 main specs (the required behavior), `openspec/changes/archive/` the 8 archived
changes that produced them, and `show-project-assets` is the one other change still open; `gdx-model/README.md`
documents the Mundus fork.
`README.md` is also machine input: `patchPluginXml` extracts the `<!-- Plugin description -->`
block into `plugin.xml` and fails the build if the markers are missing.

Facts the docs must capture (observed in the repo at HEAD): IntelliJ Platform plugin (IC 2025.2.4,
since-build 252, Java 21, Kotlin 2.4.10) in two Gradle modules:
- root: the plugin, all Kotlin in `src/main/kotlin`; packages `dto` (readers, `ProjectLayout`,
  `ProjectAssets`, `JsonNodes`), `scene` (scene DTOs), `ecs` (Ashley components and systems, the scene
  ECS loader/writer and component codecs that carry unknown Mundus components through unchanged),
  `filetype`, `projectView` (tree, eye toggle, rename, skybox chooser, and `editSceneJson`, the one
  undoable, formatting-preserving write path for scene files), `properties` (Abyssus Properties tool
  window), `sceneview` (GL view: models, terrain, skybox, lights, cameras and look-through, picking,
  move/rotate gizmos written back by `SceneTransformWriter`), `language` (GLTF PSI); `src/main/java` holds only the
  grammar sources `Gltf.bnf`/`Gltf.flex`, generated into the git-ignored `src/main/gen`;
  `com.badlogic.gdx.backends.lwjgl3.GdxGlBridge` reaches package-private libGDX backend code.
- `:gdx-model`: plain JVM library (no IntelliJ dependency) with the Kotlin fork of Mundus'
  `lib-core`/`lib-assets` (32-bit-index meshes, `ModelBatch`, Assimp loader) in
  `net.nevinsky.abyssus.lib.core` / `lib.assets.assimp`; no longer diffs against upstream.
Jackson for JSON (bundled `jackson-databind`); libGDX hosted on an LWJGL3-AWT GL canvas with `Gdx.*`
shims (`GdxRuntime`); tests in `src/test/kotlin` and `gdx-model/src/test/kotlin`, fixtures in
`src/test/testData/project/Untitled` and `.../Animated`; GL tests open a window and run only with
`-Dabyssus.glTests=true`; CI runs `./gradlew check`.

## Goals / Non-Goals

**Goals:**
- An agent can start from `AGENTS.md` alone and know how to build, test, where things live and what
  not to touch, then pull deeper pages only when needed (progressive disclosure).
- Docs state verifiable facts and point at code paths rather than copying code.
- Staleness is detectable mechanically.

**Non-Goals:**
- No user manual or marketplace copy beyond fixing the README template text.
- No rewriting of `docs/superpowers/` or OpenSpec artifacts; no migration of them.
- No documentation of in-flight changes (today `show-project-assets`) beyond their committed code.
- No code, build or CI changes.

## Decisions

1. **`AGENTS.md` is canonical; `CLAUDE.md` is a one-line `@AGENTS.md` import.** `.agents/` already
   exists beside `.claude/`, so more than one tool is in use. Alternative: duplicate content (drifts)
   or symlink (breaks on Windows checkouts).
2. **Keep `AGENTS.md` under ~100 lines, as a map.** Long always-loaded files dilute attention. It holds
   commands, layout one-liners, hard rules, and links; detail lives in `docs/ai/`. Alternative: one big
   file (rejected, context cost every session).
3. **`docs/ai/` pages, one topic each**, named in the proposal. Placed under `docs/` (not
   `openspec/`) because OpenSpec specs describe required behavior of changes, not orientation.
   Alternative: fold into `openspec/specs` (rejected: wrong purpose, specs state required behavior,
   not how to find your way around).
4. **Single source per fact.** Build commands only in `AGENTS.md`; format details only in
   `file-formats.md`; required behavior only in `openspec/specs` (docs link to the capability, never
   restate its requirements); README user section stays the user-facing source and is linked, not copied.
5. **Hard rules are explicit and justified** (each with a one-line why): do not edit `src/main/gen`
   (regenerated from `.bnf`/`.flex`); `gdx-model` stays a plain JVM library (no IntelliJ or plugin
   imports, so other libGDX projects can use it); view code never writes files except the documented scene edit actions,
   all through `editSceneJson` as undoable commands that keep the file's formatting; `Gdx.*` is process-global so install only while rendering and serialize
   (see `GdxRuntime`); keep the `README.md` description markers; use OpenSpec for non-trivial changes.
   Each rule must be confirmed against code or an existing spec/README during writing.
6. **Staleness check as `scripts/check-docs.sh`** (plain shell, no Gradle): extracts backtick-quoted
   spans from `AGENTS.md` and `docs/ai/*.md`, treats a span as a repo path only when it contains `/`
   or ends in a file extension and has no spaces or glob characters (so `Gdx.*` and
   `./gradlew check` are skipped), and fails if any such path does not exist; `...` segments are
   not allowed in checked paths. Running it from CI is out of scope but noted. Alternative: a
   Gradle task (slower, config-cache concerns) or nothing.
7. **Package READMEs only for `sceneview`, `projectView` and `ecs`**: the places with non-obvious
   threading/lifecycle (`GuardedGLCanvas` stable-size gate, `GdxRuntime`, off-GL-thread asset
   loading and `AssetCache` release, picking, gizmo drags and their write-back), tree/eye-toggle/
   entity-selection mechanics and the shared `editSceneJson`, and the ECS round trip (codecs, carried
   unknown components, Mundus write-back order). Others are discoverable from code; `gdx-model` already
   has its README.

## Risks / Trade-offs

- [Docs drift from code] → mechanical path check; facts link to files; keep pages short.
- [Later changes invalidate docs] → each change updates the docs it invalidates (noted in
  `docs/README.md`); `scripts/check-docs.sh` catches renamed or removed paths.
- [Wrong claim stated confidently] → every rule/gotcha is verified against code while writing;
  uncertain items are omitted rather than guessed.
- [README edit breaks plugin build] → keep markers; verify with `./gradlew patchPluginXml`.
