# Abyssus: libGDX Scene Editor for IntelliJ IDEA, and the Control Line Flight Game

![Build](https://github.com/inver/abyssus/workflows/Build/badge.svg)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**Abyssus is an open-source 3D scene editor and level editor for [libGDX](https://libgdx.com/) games, built as a plugin for
IntelliJ IDEA and other IntelliJ-based IDEs.** Build game worlds, terrain, water, foliage, lighting and game UI beside your
Kotlin or Java code, then test them with physics in a separate Play process. No separate engine or editor application is
needed.

**[Control Line](projects/app-game-control-line/README.md) is the game made with it:** a free, open-source control-line
(U-control) model airplane flight simulator for Windows, macOS and Linux, with arcade Free flight and an F2B pattern mode
that is judged like the real FAI aerobatics schedule. Its field, planes and menus are Abyssus project data.

## Abyssus: a scene editor inside your IDE

Abyssus gives a libGDX project what Unity, Godot and Unreal give their games, a visual editor, while keeping everything as
plain JSON files in your repository and the code in the IDE you already use.

- **3D scene editor:** select, move and rotate models, lights and cameras with undoable gizmos; orbit, pan and zoom a
  live libGDX view with Lit, Unlit and Wireframe shading and adjustable antialiasing.
- **Terrain generator and foliage painter:** generate heightmaps from seeded noise, then scatter and paint trees, rocks and
  grass with brushes.
- **Water, sky and lighting:** animated lakes and seas with reflections and refraction, procedural skies and clouds,
  cloud shadows, sky-lit scenes, fog, and directional, point and spot lights.
- **Model import:** bring OBJ, FBX, 3DS, DAE, glTF and GLB files, or FlightGear aircraft, into a project as model assets.
- **Game UI editor:** design menus, HUDs and pause screens visually (widget tree, canvas, bindings, styles) and try them in
  an interactive preview. The same layout renders in your game.
- **Physics and Play:** enable built-in Jolt physics (rigid bodies, colliders, constraints) and run your scene, or your own
  game code, in a separate process with Play, Pause, Step and Stop.
- **GPU ray tracing preview (experimental):** ray traced shadows, reflections and glass on Apple silicon (Metal) and
  Vulkan 1.2 devices.
- **Safe, diff-friendly files:** every edit is one undoable command that keeps your formatting, key order and number text,
  so scene changes review cleanly in Git.

New to Abyssus? Read the [user guide](docs/user-guide.md).

## Control Line: a control-line flight game

[Control Line](projects/app-game-control-line/README.md) is a control-line flight simulator written in Kotlin on libGDX
and Jolt physics. You fly a model airplane on two lines around a pilot standing in the centre of a circle, and the handle
moves only the elevator. It is also the reference project for building a real game with Abyssus.

- **Free flight:** an arcade mode that scores laps, loops, wingovers, figure eights and landings, with combo multipliers
  and a high-score table.
- **F2B pattern:** practise the FAI class F2B aerobatic schedule (15 manoeuvres in 7 minutes) against a judge that marks each
  manoeuvre from the track you actually flew, with a Pro, Mid or Noob assist level, an on-screen guide for beginners,
  and a manoeuvre-by-manoeuvre debrief replay.
- **Watch AI:** an AI pilot flies the whole schedule with the same physics you use.
- **Real flight model:** line tension, fuel and stall, with three planes (Stunter, Racer and Trainer).
- **Native packages:** macOS disk image, Windows installer and Linux `.deb`, `.rpm` and AppImage, each with its own bundled
  Java runtime, so players need no Java. See [Packages](projects/app-game-control-line/README.md#packages).

Run it from source:

```
./gradlew :app-game-control-line:run
```

## Install the Abyssus plugin

- Using the IDE's built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "abyssus"</kbd> >
  <kbd>Install Plugin</kbd>

- Manually: download the [latest release](https://github.com/inver/abyssus/releases/latest) and install it with
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

Then open a native Abyssus project (a folder with an `.abss` file, `scenes/` and `assets/`) and choose the **Abyssus** view
in the Project tool window. Open a copy of the Control Line project in `projects/app-game-control-line/project/ControlLine`
to see a complete example.

## Abyssus feature reference

<!-- Plugin description -->
Edit native Abyssus projects and libGDX scenes in the IDE.

Abyssus format version 1 requires `"format":"abyssus"` and `"formatVersion":1` in every `.abss`, `.scene` and
asset `meta.json`. This breaks compatibility with earlier unmarked files and projects from other editors. Unsupported files
remain readable in the text editor; plugin editing and loading are refused. No importer or automatic migration is included.

- **Abyssus view**: a project tree of `.abss` projects, their scenes, entities and assets, with unused assets marked.
  Toggle scene options, rename scenes and choose a scene's skybox from the tree.
- **Abyssus Properties**: the `meta.json` of the selected asset, with skybox face previews; terrain size, texture
  repetition and textures, cube skybox faces and procedural sky parameters can be edited, terrain heights can be generated
  from seeded noise, **New Terrain** on the Assets node creates a terrain asset, **Import Model** turns an OBJ, FBX,
  3DS, DAE, glTF or GLB file into a model asset with a live preview, and **Import FlightGear Aircraft** turns an
  aircraft from a FlightGear `.zip` into a model asset.
- **Scene view**: a 3D view of a `.scene` with its models, animations, terrain, skybox, lights and cameras, with an optional GPU Ray Tracing mode. Select
  objects, move and rotate them with gizmos (saved to the scene file, undoable), and look through a scene camera. The toolbar's shading
  dropdown offers Lit, Unlit and Wireframe (per view, never saved); Wireframe draws every content drawable as edges only.
  Edges and lines use 4× antialiasing by default; select the project in the Abyssus tree and use
  **Abyssus Properties | Scene view antialiasing** to choose Off, 2×, 4× or 8×. The choice is saved in its `.abss`
  and applies to that project's open views, with automatic fallback for unsupported GPUs.
- **Water**: **Add Water** (a Lake or a Sea, from the Scene view toolbar or a scene's right-click menu) adds a finite
  horizontal surface that is drawn from above with animated waves, reflections of the sky and the scene, wave-distorted
  refraction that fades to a tint with depth and animated foam where terrain meets it. Terrain above the level defines
  the shoreline and islands. Select a surface in the view or the tree to move it with the Move gizmo and edit its level,
  size, tint, clarity, waves and foam in Properties; hide, rename, remove and undo like anything else. Water is saved in
  the scene's `abyssus.waterSurfaces` and is Abyssus-only data; there is no underwater look, and water is not a drop target.
- **Terrain foliage**: **New Foliage...** creates layered scatter settings for trees, rocks and grass;
  **Add Foliage...** attaches them to a terrain entity, and **Paint Foliage** paints or erases their density, with Undo.
- **Sky light and cloud shadows**: a procedural sky with `"lightsScene": true` lights models and terrain with its own colour
  (blue from a clear noon sky, grey under overcast, warm at sunset), changing smoothly with the sun and the clouds, and
  clouds cast drifting shadows on the sun's light. Both follow the sky's atmosphere parameters, not a custom `sky.frag`
  that departs from them; Ray Tracing sees the same clouds and cloud shade.
- **Game UI**: author menus, HUDs and pause screens as `UI` assets in a visual editor (screens and overlays, a widget
  tree, a canvas, properties, bindings and styles), try them in an interactive preview with sample data, and run the same
  layout and styling in a game.
- **Built-in Physics**: select a project’s `.abss` file and enable **Physics** in Properties for editable physics
  components, collider/constraint overlays and Play in a separate process.
- **Ray Tracing settings**: a selected scene's Properties switch turns Ray Tracing on for its open views (not saved), and
  its target samples, ray budget and reflection and refraction depths are saved in the scene and undoable. A model
  entity's PBR materials can be given Transmission and IOR to render as glass; these overrides apply to that entity only.
  Glass must be a closed solid, and anything Ray Tracing cannot draw falls back to the normal renderer with the reason.
<!-- Plugin description end -->

## Documentation and development

- [User guide](docs/user-guide.md): project tree, properties, scene view, physics and Game UI.
- [Control Line](projects/app-game-control-line/README.md): controls, F2B rules, scoring, packages and tuning.
- [Documentation map](docs/README.md), [architecture](docs/ai/architecture.md) and [file formats](docs/ai/file-formats.md).
- Contributors and coding agents: start at [AGENTS.md](AGENTS.md) for build, test and sandbox commands.

## Frequently asked questions

**What is Abyssus?** An open-source libGDX scene editor that runs as an IntelliJ IDEA plugin. It edits `.abss` projects,
`.scene` files and asset `meta.json` files.

**Is it a game engine?** No. It is an editor for libGDX games. The runtime is plain libGDX with Ashley components, and a
shipped game carries no editor code.

**Which IDEs are supported?** IntelliJ IDEA and other IntelliJ Platform IDEs.

**Is Control Line free?** Yes. Both Abyssus and Control Line are open source under Apache-2.0, apart from a few separately
licensed assets that are listed in the [Control Line notes](projects/app-game-control-line/README.md#assets-and-licences).

## License

Licensed under [Apache-2.0](LICENSE) (SPDX-License-Identifier: Apache-2.0).

Files under `projects/lib-gdx` derived from [libGDX](https://github.com/libgdx/libgdx) retain their original
Apache 2.0 headers.

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template

