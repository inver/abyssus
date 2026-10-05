# Design

## Context

See proposal.md for motivation. The previous version of this change is active in the bundled `Field.scene`. It
uses a generated 200 m site terrain with a pale ~52 m pad, a 600 m grass ground, and 92 tree and 12 building
instances of seven stylized Kenney models (entity IDs 100-204), kept 35 m clear of the pilot. Its evidence is in
`verification.md` and `games/control-line/project/environment/`.

FieldScene already enumerates MODEL and TERRAIN renderables, and FieldRenderer draws their transforms with
`core`'s `DefaultShaderProvider` and the game's own terrain shader. The game has no shadow pass. Editor shadows live
in the plugin (`sceneview/shadows`), so the game cannot use them.

Reference inputs: `games/control-line/project/pics/img.png` (north-up satellite screenshot) and `about.txt` (written
brief). The image is the source for layout and scale. The brief is the source for surface appearance and
facilities. Both are estimates, not survey data.

## Goals / Non-Goals

**Goals:** Rebuild the field to the revised spec using existing model and terrain rendering. Add a directional sun
shadow map to the game renderer only. Place scenery in metres with the pilot at the origin. Preserve unrelated
native document contents. Prove that every bundled model parses.

**Non-Goals:** No change to the plugin, `core`, `runtime`, `physics` or the `scene-shadows` capability. No game aerial
camera, importer, UI, gameplay collision or GIS pipeline. Do not repair unrelated plugin test failures.

## Decisions

### Coordinates and scale

Keep the existing convention: metres, pilot at the origin, north along -Z, east along +X, flying surface at y = 0.
The image scale is estimated at about 0.3 m per pixel, from the width of the Pribrezhnaya roadway and the lengths of
the apartment blocks. Record the scale and every derived figure in `placements.json` as estimates. Target placements,
with distances measured from the pilot:

| Feature | Direction | Distance / extent |
|---|---|---|
| Paved circle | centre | 50 m diameter (image: 48-55 m) |
| Utility building (WC + staff), preparation area | E | footprint begins ≥ 28 m; building ~4 x 8 m, single storey |
| Northern access path | N | pad edge to street |
| Pribrezhnaya Street (carriageway, sidewalks, light poles) | N | ~50 m, runs roughly W-E, falling slightly to the ESE |
| DK "Plamya" (brown roof) | NNE | ~110 m, across the street |
| Long low building | ESE | ~55 m |
| Railway, two tracks with catenary | S | ~60-70 m, runs WNW-ESE |
| Blue-roof warehouse | WSW | ~200 m, long axis parallel to the railway |
| Apartment blocks | ENE / E | ~150-250 m |
| Private houses and gardens | SE, and S beyond the railway | ~100 m and beyond |

### Pad and markings

The pad is 50 m in diameter. The brief's 35-42 m estimate is rejected: the Racer starts its takeoff 21 m from the
pilot (`control-line-flight`, "Takeoff on the lines"), so the pad needs at least 2 x 21 + 4 = 46 m, and the image
measures about 50 m. Following the brief, the surface is dark-grey asphalt. White rings, each about 0.1 m wide, lie at
15, 18 and 21 m: the Trainer, Stunter and Racer line lengths. A white pilot circle about 1.5 m in diameter marks the
centre, and a darker border band about 0.3 m wide marks the edge. The pad is a flat decal model (`model_airfield_pad`)
laid 1 cm above the level terrain. It has a disc tiled with a CC0 photo asphalt texture, the raised border annulus,
and the rings and pilot circle as thin annuli with a worn white paint texture. As geometry, the markings stay sharp at
any distance without a 2 cm-per-texel ground texture. The physics surface stays the level terrain beneath it.

### Ground

Regenerate the site terrain as a new asset: 300 m square, 255 x 255 heights, centred on the pilot. Keep it level
(y = 0) to 30 m from the pilot. South of that it descends about 2 m, rises to a ballast embankment under the railway,
and then settles. All edges finish 1 m below the pad, just above the outer ground. The terrain uses the existing splat
shader: dry grass as the base, compacted dirt (R), asphalt (G), railway ballast (B) and concrete sidewalk (A). The
layers come from CC0 ambientCG photo textures and repeat every 3 m. A generated 2048² splat map (about 15 cm per pixel)
paints the street, the sidewalks, the access path, the crossing dirt tracks, the preparation area, the ballast bed and
irregular worn patches. A new 600 m outer ground terrain uses the dry grass texture and sits 1.1 m below the pad, sunk
under the site footprint so it never shows through. The terrain keeps its HEIGHT_FIELD collider. Store the generation
parameters with the asset and identify the image as a layout reference.

### Scenery assets

Replace the Kenney models. A standard-library Python step (`fetch_sources.py`) downloads CC0 sources with checksums:
Poly Haven models (trees, shrubs, tables, street lamps) and ambientCG photo textures. A headless Blender step
(`blender -b --python build_assets.py`, Blender 3.4.1 or newer) reduces the photoscanned trees and shrubs to game
budgets (about 10k triangles per tree, 3k per shrub, 1K textures). It generates the pad, the buildings, the railway
segment (rails, concrete sleepers, catenary poles and wires) and the model stands from CC0 photo materials, and exports
every model as GLB. Both scripts are checked in, so the result is reproducible without manual modelling. Every bundled
model is a GLB parsed by the real `AssimpModelLoader` in a headless check. Take scale and centre offsets from measured
bounds. Each folder keeps `source.json` with the author, source URL, license and checksum, or the generator and its
parameters.

The Poly Haven trees model each leaf as geometry (no alpha cards). The build keeps a seeded share of the leaf clusters,
enlarges each one about its own centre so the crown keeps its coverage, keeps the largest branch pieces, and collapses
the result. Leaves are exported opaque and two-sided, so they cast shadows. The photoscanned pine was dropped: thinning
its needle clusters left a sparse crown with collapse artifacts at more than 60k triangles. Three broadleaf types and
two bushes cover the reference.

### Asset candidates

The user supplied these candidates. The Sketchfab and Meshy downloads need a logged-in account, so the user chose
CC0 substitutes that a script can fetch (the last column). The supplied links remain alternatives. A candidate is bundled only if its license allows redistribution in this public
repository and it converts to a GLB that `AssimpModelLoader` parses. For CC-BY assets, record the author, title, source
URL and license in `source.json` and credit them in `project/environment/README.md`. A generated stand-in, as described
under Scenery assets, replaces any candidate that is rejected or still unverified at implementation time. License
status below was checked on 2026-10-06.

| Element | Candidate | License (checked) | Fit / notes | Decision |
|---|---|---|---|---|
| Pad asphalt | Circular Pavement Tile Tex, PolyScan — [polyscann.com](https://polyscann.com) | stated CC0, unverified | Link is the site's home page, not the asset. Use as the asphalt base only; the rings stay baked at 15/18/21 m. | Not used; ambientCG CC0 asphalt instead (no asset page) |
| Painted rings | Concentric Circle Paving Tiles, AITextured — [aitextured.com](https://aitextured.com) | unknown | Link is the home page. A tiled concentric pattern cannot place rings at the line radii. | Not used; rings are pad decal geometry |
| Utility building | Rustic Wooden Shed, AIPRINTGEN — [link](https://aiprintgen.com/3d-models/rustic-wooden-shed-3d-model-700e37ad3da28347) | unverified (page returned 403) | Weathered wood; the spec needs light walls and a grey or light roof. | Not used; generated building |
| Worktables | Wooden outdoor table with benches, CGTrader — [link](https://www.cgtrader.com/free-3d-models/architectural/other/wooden-outdoor-table-with-benches) | unverified (page not readable); CGTrader's Royalty Free license normally forbids redistributing the files | Formats `.blend` / `.fbx` / `.spp`; convert to GLB. Picnic table, not a workbench. | Not used; Poly Haven `wooden_picnic_table` and `painted_wooden_table` (CC0) |
| Blue-roof warehouse | Blue Warehouse Courtyard, samp91784 (Meshy 6) — [link](https://www.meshy.ai/id/3d-models/Blue-Warehouse-Courtyard-019fbe66-d459-7ecd-892a-b5bbb5c3ce58) | CC0 | AI-generated courtyard scene with a blue facade; the reference needs a long building with a blue roof parallel to the railway, about 200 m away. | Not used (login needed); generated warehouse |
| Railway track | Gleisabschnitt — WWII Railway Track, Alexandr Kovbasa — [link](https://sketchfab.com/3d-models/gleisabschnitt-wwii-railway-track-b8a23bf1330e4741be2e3732b9bd9372) | CC-BY 4.0 | Single-track modular section with wooden sleepers. Instance it along two parallel lines for the double track; catenary poles and wires remain generated. | Not used (login needed); generated track segment |
| Street light poles | Street Light Pole – Low Poly / Game Ready, marcosnegrao — [link](https://sketchfab.com/3d-models/street-light-pole-low-poly-game-ready-free-df766545c1af4184b7f0291147b21b5d) | CC-BY 4.0 | Low-poly lamppost. | Not used (login needed); Poly Haven `street_lamp_01` (CC0) |
| Trees and bushes | 7 Trees + 3 Bushes Stylized Nature Pack, Yoo Game Art — [Sketchfab](https://sketchfab.com/3d-models/7-trees-3-bushes-stylized-nature-pack-free-6d751600371a4011a8945a0256488728), [itch.io GLB](https://yoogameart.itch.io/low-poly-3d-trees-pack) | CC0 (itch.io) | Single `trees.glb`. Stylized cartoon style conflicts with the photo-sourced material requirement. | Not used; Poly Haven CC0 trees and shrubs reduced in Blender |
| Dirt ground | Dirt Ground SBSAR, RenderHub — [link](https://www.renderhub.com/ihsupplies/dirt-ground-sbsar) | Personal Use Only, no sharing | SBSAR needs Substance to bake. | Rejected: not redistributable |

If a supplied candidate is adopted later, prefer its glTF/GLB download. Otherwise convert FBX to GLB in the Blender
step, and record the conversion and any CC-BY credit in `source.json`.

### Clearance

The clear area is a vertical cylinder 28 m in radius around the pilot. The Racer flies at about 21 m of line plus
about 1 m of handle and plane reach, everywhere on a hemisphere of that radius. Add a 3 m safety margin, then round up
to 3 m beyond the pad edge. A cylinder of that radius contains the whole flight hemisphere. The preparation tool rejects
any placement whose transformed footprint or crown comes within 28 m, and a test asserts the same against the bundled
scene. The pilot, parked planes and ground texture are exempt.

### Game shadows

Add a single directional shadow map to `games/control-line` render code. The light comes from `FieldScene.sunDirection`.
An orthographic light camera covers about 160 m around the pilot, which includes the field, the facilities, the street
and the railway. The map is 2048² depth, with slope-scaled bias and PCF filtering for soft edges. The game supplies its
own `ShaderProvider` (the interface is in `core`) and extends its terrain shader to sample the map. Models and terrain
cast and receive shadows. Sky, lines and HUD do not. Render the shadow pass on the existing LWJGL thread once per frame,
before the main pass. Nothing changes in `core`. If `ModelBatch` cannot render a depth-only pass through a custom
provider without a `core` change, stop and raise it as a separate change instead of editing `core` here.

### Aerial inspection

Top-down and oblique inspection uses the Abyssus editor's orbit camera on a copy of `ControlLine`, which also shows
editor shadows. The game's cameras stay as they are.

### Scene application

Prepare a patch that removes the previous scenery entities (IDs 100-204), replaces the field and outer ground asset
references, and appends the new entities from a fresh ID range. Check the expected scene SHA-256 and validate with
`AbyssusDocumentFormat` first. Apply the patch through `editSceneJson`, from a plugin platform test
(`AirfieldPatchApplicationTest`). It does nothing unless `ABYSSUS_AIRFIELD_PATCH` names the patch file, so normal test
runs never touch the bundled project. The earlier offline direct edit is not a
standing exception: if the writer still cannot be invoked, get a new explicit approval before any direct edit and
record it in tasks.md.

## Risks / Trade-offs

- **Photo-textured, low-poly scenery is not true photorealism.** Specify measurable material criteria (photo-sourced
  textures, texel density) and inspect from the pilot camera.
- **Shadow acne or peter-panning on the large terrain.** Tune the bias. Fit the light camera tightly to the 160 m area.
- **The model shader may lack alpha testing for foliage.** Check the first tree asset early and fall back to opaque crowns.
- **Shadow-pass cost.** One map, frustum-fit, and casters limited to within 160 m.
- **The image scale is approximate (±20%).** Record it as an estimate. Clearance is computed from flight geometry, not
  from the image.
- **The writer depends on the platform harness.** See Scene application. Stop rather than silently writing directly.

## Migration Plan

No format migration. Bundle the new asset folders, apply the scene patch, then verify gameplay, game shadows and a
project copy in the editor. Delete the Kenney asset folders only after the new scene no longer references them. To roll
back, remove the new entity range and restore the previous field references and entities 100-204 from the recorded
patch through the same writer. Revert the game render commits. The previous asset folders were never committed, so
the build moved them to `~/.cache/abyssus-airfield/previous-assets` instead of deleting them.
