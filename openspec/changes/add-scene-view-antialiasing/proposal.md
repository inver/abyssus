# Proposal

## Why

The scene view draws without antialiasing. The grid, gizmos, camera and light markers and selection boxes are one-pixel
lines, and model and terrain silhouettes have hard stair-stepped edges, which makes the editor look rough next to the
Control Line game, whose window already asks for 4× multisampling. Users also need a way to lower or turn it off on a
slow or unusual GPU.

## What Changes

- Scene views draw with 4× multisample antialiasing by default.
- A new IDE-wide setting, **Settings | Tools | Abyssus | Scene view antialiasing**, offers Off, 2×, 4× and 8×. It is
  remembered across IDE restarts and shared by every project.
- Changing the setting applies to every open scene view at once, without reopening the view. Camera, selection, the
  chosen look-through camera, Play and Ray Tracing mode are kept.
- If the graphics driver refuses the requested sample count, the view tries the next lower one down to Off, logs a
  warning once per view, and keeps drawing. A view never stays blank because of antialiasing.
- Reads and writes no document fields: `.abss`, `.scene` and asset `meta.json` stay unchanged and the Abyssus format
  does not change. No document validation is involved.
- Out of scope: post-process antialiasing (FXAA, SMAA, TAA), a per-scene or per-project value, a scene view toolbar
  toggle, antialiasing inside the ray-traced image (it keeps its own sample accumulation), shadow-atlas filtering, the
  Control Line game window and its play host, and multisampling in the GL test harnesses.

## Capabilities

### New Capabilities

- `scene-view-antialiasing`: Default multisampling of scene views, the IDE-wide setting, applying it to open views,
  and the fallback when the driver refuses a sample count.

### Modified Capabilities

None. Existing scene rendering, picking, Ray Tracing and Play requirements are unchanged; picking uses scene data, not
pixels.

## Impact

- Plugin `sceneview` package: the canvas pixel format in `SceneViewPanel`, canvas replacement on a setting change and on
  a refused pixel format.
- A new application-level setting with a settings page registered in `plugin.xml`, and its text in
  `AbyssusBundle.properties`.
- No changes to `core`, `gdx-model`, `runtime`, `physics`, `physics-plugin` or the game.
- The open `add-project-fps-counter` change adds project-scoped view settings in Abyssus Properties. This change keeps
  antialiasing IDE-wide and in the IDE settings dialog because it depends on the machine's GPU, not on the project; the
  two settings do not interact.
- Docs: README feature list, CHANGELOG Unreleased, `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md`, and
  `docs/ai/architecture.md` if its threading or extension notes become incomplete.
