# Proposal

## Why

The current bundled airfield provides a basic ground reconstruction and stylized scenery, but it does not match
the user's detailed reference for the control-line track at approximately 53.4829 N, 49.8472 E
(`games/control-line/project/pics/img.png` and `about.txt`). Refine the existing environment into a realistic, worn
paved circle with recognizable support facilities and surrounding landmarks.

## What Changes

- Replace the current pale pad with a near-perfect circular dark-grey asphalt surface about 50 m in diameter.
  The brief's 35-42 m estimate is not used: the satellite image measures about 48-55 m, and the Racer's 21 m
  line puts its takeoff roll outside a 42 m pad. Paint three concentric white rings at the bundled planes'
  line-length radii (15, 18 and 21 m), a central white pilot circle, worn pavement and a thin darker outer
  border. Keep the site open, without a fence or net.
- Reconstruct the worn triangular plot between the street and the railway: dusty compacted soil around the pad,
  sparse dry grass and weeds, irregular bushes and trees, crisscross dirt tracks west and south, a northern access
  path and a gentle descent toward the southern railway. Keep the flying surface level.
- Add the eastern support area with a small light-coloured, single-storey utility building designated for WC
  and staff use, a light or grey roof, packed-dirt preparation space, wooden worktables and model stands.
- Include the west-southwest long blue-metal-roof warehouse, Pribrezhnaya Street with sidewalks and lighting poles
  to the north, the brown-roofed DK "Plamya" approximation to the north-northeast, a double-track railway with
  electrification poles and wires to the south, and residential houses, gardens and apartment blocks to the east.
  Place them at the approximate distances measured from the reference image.
- Replace the stylized Kenney scenery with models and ground materials that use photo-sourced textures, and size and
  place them from the reference. Asset and instance counts follow the reference rather than being fixed at the
  current seven models, 92 trees and 12 buildings. Preserve redistribution licenses and source records for any
  downloaded or adapted assets.
- Add soft cast shadows from the scene's sun to the game renderer (one directional shadow map, game module only),
  so that the field shows bright midday lighting. The editor already renders shadows (`scene-shadows`), and this
  change leaves the editor, `core` and that capability unchanged. Top-down and oblique reference views are inspected
  in the Abyssus editor on a copy of the project. The game's cameras are unchanged.
- Preserve the pilot, plane choices and flight parameters. Replace the blanket 35 m scenery exclusion with a
  measured clearance: no scenery within 28 m horizontally of the pilot (the Racer's 21 m line, plane and handle
  reach, a safety margin, and a 3 m buffer beyond the pad edge). Scenery remains visual, without new physics
  colliders.
- Update generation instructions, the placement record and diagram, project documentation and verification
  tasks. Keep the previous implementation's evidence (`verification.md`) as history. The revised appearance
  needs new verification.

## Capabilities

### New Capabilities

- `control-line-field-environment`: Reference-based flying-circle appearance, support facilities, surrounding
  landmarks, daylight presentation, flight clearance and licensed asset provenance for the bundled field.

### Modified Capabilities

None. Existing flight, scoring, game-flow and editor `scene-shadows` requirements are retained.

## Impact

Affected areas are Control Line project assets, scene data, environment preparation, the game's renderer and
shaders (`games/control-line` only), and associated documentation. No plugin, `core`, `runtime` or `physics` code
changes are planned. No document format version change, gameplay rule change or editor change is proposed.
Read/write fields remain native `format`, integral `formatVersion`, asset `type`, `uuid`, `additional`, and
`ecs.entities` entries with Name, Type, Position, asset Render and existing Light components. Validate native
documents before binding or editing. Use `editSceneJson` for scene edits and preserve unrelated keys, number text and
omitted defaults. The previously approved offline application is historical evidence, not a standing exception.

The supplied description and screenshot define the artistic target. Dimensions, elevations, species and building
identities remain estimates rather than surveyed facts. Where the written brief contradicts gameplay constraints
(the pad diameter), the gameplay constraint and the image win. Out of scope: exact architectural replicas, surveyed
GIS data, fences, safety nets, crowds, interactive facility gameplay, scenery collision and a game aerial camera.
The street, railway and support-zone details listed above are in scope, unlike in the previous plan.
