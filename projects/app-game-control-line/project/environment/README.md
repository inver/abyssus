# Airfield environment

The bundled `Field.scene` reconstructs the control-line circle ("Кордодром") at about 53.4829 N, 49.8472 E in Samara,
south of Pribrezhnaya Street. The layout reference is the satellite screenshot `projects/app-game-control-line/project/pics/img.png`. The artistic brief is
`projects/app-game-control-line/project/pics/about.txt`. Neither is survey data: every size, height and position below is an estimate, and the buildings
are intended representations, not architectural replicas.

## Estimates and decisions

Axes are metres with the pilot at the origin, north along -Z and east along +X. The flying surface is at y = 0. The
image scale is about 0.3 m per pixel, measured from the Pribrezhnaya roadway width and the apartment block lengths.
`site_plan.py` holds every figure. `placements.json` records every instance, and [layout.svg](layout.svg) is a
north-up placement diagram (not a render).

| Feature | Placement (estimate) |
|---|---|
| Paved circle | 50 m diameter, centred on the pilot (image: 48-55 m) |
| Rings | white, at 15, 18 and 21 m: the Trainer, Stunter and Racer line lengths; 1.5 m pilot circle |
| Clear area | nothing within 28 m of the pilot: Racer line 21 m + ~1 m handle and plane + 3 m margin, rounded to 3 m past the pad edge |
| Utility building | east, 4 x 8 m, footprint from x = 32 m; **WC and staff room** |
| Preparation area | packed dirt east of the circle with two picnic worktables, two worktables and five model stands |
| Pribrezhnaya Street | north, ~50 m, carriageway 7 m, sidewalks both sides, concrete lighting poles every 34 m |
| DK "Plamya" | north-north-east, ~108 m, across the street, brown hipped roof, portico facing the street |
| Long building | south-east, centre ~53 m (nearest edge 31 m) |
| Railway | south, ~66 m at x = 0, west-north-west to east-south-east; two tracks 4.1 m apart on an embankment, catenary |
| Blue-roof warehouse | west-south-west, ~200 m, parallel to the railway |
| Apartment blocks | east and north-east, 165-265 m |
| Houses and gardens | south-east between the lane and the railway, and south beyond the railway |
| Trees and bushes | dense to the north-east and east, sparse on the worn plot (125 trees, 90 bushes); foliage assets since the foliage migration (below) |
| Grass | generated clumps on the site terrain wherever the base ground shows, never within 25 m of the pilot |

Where the brief and the image disagree:

- **Pad size.** The brief's 35-42 m is too small: the Racer starts its takeoff 21 m from the pilot, so the pad needs
  at least 46 m. The image measures about 50 m, so 50 m is used.
- **Pad surface.** The image shows a pale pad without visible rings. The brief's dark asphalt with rings was chosen.

## Assets

Everything is CC0. Each asset folder has a `source.json` with its author, source page, licence and checksum, or the
generator and the materials it used.

- Ground: `terrain_airfield_site` (300 m, 255² heights, level within 30 m, a gentle southern descent and the railway
  embankment) with five ambientCG photo layers blended by a generated 2048² splat map (`texture_airfield_site_splat`:
  R compacted dirt, G asphalt, B ballast, A sidewalk concrete). `terrain_airfield_outer` (600 m) is the horizon ground,
  sunk under the site. The site terrain keeps the field's height-field collider.
- Generated models (`build_environment.py`, with ambientCG textures): `model_airfield_pad` (a decal 1 cm above the level
  terrain), `_utility`, `_dk_plamya`, `_warehouse`, `_long_building`, `_apartment_block`, `_house_a`, `_house_b`,
  `_railway` (a 50 m segment, placed as one `Railway` path arrangement of 12 linked copies by `layout.py`, as the editor's Add Path Arrangement writes it), `_street_lamp`, `_model_stand`.
- Poly Haven models, reduced in Blender: `model_airfield_tree_broadleaf` (Island Tree 01), `_tree_acacia` (Jacaranda
  Tree), `_tree_young` (Tree Small 02), `_bush_tall` (Island Tree 02), `_bush_low` (Island Tree 03), `_table_picnic`
  (Wooden Picnic Table), `_table_work` (Painted Wooden Table). For the trees, a share of the leaf clusters is kept and
  enlarged, the largest branches are kept, everything is collapsed to game budgets (about 27-42k triangles per tree,
  14k per bush), and the leaves are opaque two-sided geometry. The ARM maps are dropped.
- Foliage (`foliage_airfield_site` on the site terrain, entity `0`, and `foliage_airfield_outer` on the outer ground, entity
  `300`) and the grass model `model_airfield_grass`: made by `tools/FieldFoliage.kt` from `foliage-manifest.json` (see
  *Foliage migration*). The trees and bushes are no longer scene entities; the scene keeps one `FoliageComponent` on each
  terrain entity. The grass model is three crossed alpha-tested quads with a procedurally drawn blade texture
  (`textures/grass.png`), generated with fixed seeds and no outside material (CC0).
- The links supplied with the brief were checked but not used. The Sketchfab and Meshy downloads need a login, the
  RenderHub ground is personal-use only, and the CGTrader and AIPRINTGEN licences could not be verified (see the change
  `add-control-line-airfield-environment`, design "Asset candidates").

Models are GLB files with their textures as external files in `textures/`, as the model loader expects.

## Foliage migration

The scene used to hold 215 separate tree and bush entities (ids 333 to 632 among the facilities). They are now two native
foliage assets, so Abyssus draws and bakes them as layers and the game draws them instanced.

**The manifest.** `foliage-manifest.json` records the vegetation of the original scene: for each of the 215 entities its
model, world position, yaw and scale, and the terrain it belongs to (inside the site square, `-150..150` on both axes,
the site terrain; otherwise the outer ground), the species totals (52 broadleaf, 40 young, 33 acacia, 42 tall bushes,
48 low bushes), the pilot's position, the SHA-256 of the original scene, of the vegetation and of the rest of the scene
(everything but the vegetation and the foliage components), the hashes of the inputs (both terrains' `meta.json` and
`terrain.data`, the site's splat map and the species' model files), the generator id (`field-foliage-v1`), the seed and the
fixed `lastModified` of every generated asset. Asset UUIDs are derived from their folder names. The manifest is immutable:
the tool records it only from an original scene and refuses to replace a different one. Nothing at runtime reads it.

**Splat map.** `texture_airfield_site_splat` holds R compacted dirt (the paths too), G asphalt, B ballast and A concrete.
The terrain shader mixes the layers over the base grass one after another, so what remains of the base at a point is
`(1-R)(1-G)(1-B)(1-A)`. These are blend weights, not a grass label: grass stands where at least 0.6 of the base remains
(a density ramp from 0.6 to 0.9), judged at the worst of each mask texel's neighbourhood so the mask never leaks onto
asphalt, ballast, concrete or a dirt path. The threshold is an estimate.

**What the tool makes** (`./gradlew :app-game-control-line:generateFieldFoliage -PfieldFoliageProject=<project folder>`;
`recordFieldFoliage` records the manifest). Run it on a copy first: the folder is edited in place. For each terrain it
paints one density mask per species from soft blobs (sigma 9 m on the site, 22 m outside) around the recorded positions,
which keeps the north-east and east weighting. A mask is zero inside the crown clearance of the pilot (28 m plus the
model's horizontal reach times its largest scale, and a margin of at least 1.5 mask texels), and on the outer ground
under the site square with the texels that touch it. It then searches for the density (as a whole number of grid cells)
and seed whose scatter lands closest to the recorded count, over a bounded set of trials in a fixed order, with ties
going to the smaller density and then the smaller seed; if no trial is within 25% of the count it fails and writes
nothing. The grass layer (id 6, DETAIL) has a fixed 150 by 150 candidate grid (about 0.25 per square metre, draw distance
60 m) and its mask is zero inside 25 m plus its clump reach. Everything is staged in a temporary folder and read back
through the runtime loaders (matching bake, no warnings, counts, formats, clear areas) before any file is published; the
scene is then edited through `HeadlessEditing`: the vegetation entities are removed and each terrain entity gets its
`FoliageComponent`, and nothing else in the scene changes.

A project whose vegetation, rest of the scene or inputs differ from the manifest is refused with nothing written. A
migrated project is accepted too, and a run on it writes the same bytes as a run on the original: tests in
`toolsTest/` (`./gradlew :app-game-control-line:testFieldFoliageTools`, part of `check`) start from the original scene
kept in `toolsTest/fixtures/Field.original.scene` and check that the committed project is exactly what the tool makes
of it.

**Limits.** The copies are a statistical match, not the old positions: they follow the old weighting but can stand where
the old placements did not (nothing keeps them off the facilities, only off the clear areas). Species totals hold on each
terrain within 25%, not exactly. Foliage adds no colliders (`collider` is left out of every layer).

## Rebuilding

1. `python3 fetch_sources.py` downloads the sources (`sources.json`) into `~/.cache/abyssus-airfield` and checks them
   against `sources.lock.json`.
2. `blender -b --factory-startup --python build_environment.py [-- ground pad buildings props vegetation]` rebuilds
   the asset folders (Blender 3.4 or newer).
3. `python3 layout.py` places the scenery, rejects anything within the clear radius, and writes `placements.json`,
   `layout.svg` and `field-environment.patch.json`. The patch carries the current scene's SHA-256.
4. Apply the patch through the editor's scene writer:
   `ABYSSUS_SCENE_PATCH=$PWD/projects/app-game-control-line/project/environment/field-environment.patch.json ./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.filetype.ScenePatchApplicationTest'`.
   It points the field entity at the site terrain, replaces every entity drawn with an airfield asset, and leaves
   everything else, including number text, as it was. That patch still lists the old tree and bush entities: they are
   foliage now, so do not apply it to the migrated scene (see *Foliage migration*).

`./gradlew :app-game-control-line:test` (`AirfieldEnvironmentTest`) checks that every model parses with its textures,
that every asset is native and records its source, and that the scenery keeps the 28 m clear area.

`previous/` holds the first version's tools and records (Kenney scenery). Its asset folders were moved out of the
project, to `~/.cache/abyssus-airfield/previous-assets` on the machine that built this version.
