# Tasks

The previous version of this change (Kenney scenery, 92 trees, 12 buildings) was implemented and is recorded in
`verification.md`. The tasks below implement the revised spec.

## 1. Reference and placement

- [x] 1.1 Record the image scale (~0.3 m/px, method stated), pad diameter (50 m), ring radii (15/18/21 m) and the landmark placement table in `placements.json` and `project/environment/README.md` as estimates. Record the brief-versus-image decisions. Verify: every placement row has direction, distance and source.
- [x] 1.2 Update `layout.svg` to the new placements with the 28 m clear radius drawn. Verify: north-up, matches `placements.json`.

## 2. Ground

- [x] 2.1 Generate the ~300 m site terrain: dark asphalt pad with rings, pilot circle and border; level to 28 m, then a gentle southern descent and railway embankment; access path, dirt tracks, street and preparation area from CC0 photo materials; ≥ 2 cm/texel on the pad. Verify: native metas validate, pad centre at the origin, heights at y = 0 within 28 m. Evidence: `AirfieldEnvironmentTest` validates every airfield meta; heights are exactly 0 within 30 m (`site_plan.height`). The pad markings are decal geometry (`model_airfield_pad`), so ring sharpness doesn't depend on ground texel density.
- [ ] 2.2 Retexture the 600 m outer ground to dry grass. Verify: no visible seam at the site edge from the pilot camera.

## 3. Scenery assets

- [x] 3.1 Re-check the license of each candidate in design.md "Asset candidates" on its asset page, and find the PolyScan asset page. Bundle only redistributable candidates that fit (blue warehouse, railway track, light poles; trees only as fallback). Record title, author, URL, license, checksum and any FBX-to-GLB conversion in `source.json`, and credit CC-BY assets in the README. Verify: each parses with `AssimpModelLoader` headless, no personal-use asset is in the repo, and foliage alpha works or the opaque fallback is recorded. Evidence: licences re-checked 2026-10-06 (design table). The Sketchfab and Meshy downloads need a login, so CC0 substitutes were used by user decision; the PolyScan link is only a home page. Leaves are opaque two-sided geometry (no alpha cards). `AirfieldEnvironmentTest.everyAirfieldModelParsesWithItsTextures` passes.
- [x] 3.2 Generate stand-ins for everything not covered by 3.1: utility building, DK "Plamya", long low building, houses, apartment blocks, street with sidewalks, catenary poles and wires, worktables and model stands, plus any rejected candidate. Verify: each parses headless, and the generator parameters are recorded.
- [x] 3.3 Prepare placements and the scene patch (remove IDs 100-204, replace the field/outer ground references, append the new range, expected SHA-256). Verify: the tool rejects any footprint or crown within 28 m, and IDs are unique.

## 4. Game shadows

- [x] 4.1 Add a sun shadow map to `games/control-line` render code: game `ShaderProvider` and terrain shader sampling, 2048² map, ~160 m frustum-fit ortho light camera, PCF and bias. Verify: no `core`, plugin or `scene-shadows` changes, and `./gradlew :games:control-line:test` passes.
- [ ] 4.2 Check from the pilot camera: trees, buildings, the pilot and the plane cast soft shadows on the terrain and other models, with no visible acne or peter-panning. Verify: screenshot recorded in `verification.md`.

## 5. Scene application

- [x] 5.1 Validate the current scene with `AbyssusDocumentFormat` and apply the patch through `editSceneJson` (a direct edit needs a new explicit approval, recorded here). Verify: the pilot, the planes, colliders, light, sky and unrelated number text are unchanged, and the asset graph resolves. Evidence: applied through `editSceneJson` by the env-gated `AirfieldPatchApplicationTest`. Compared with the pre-edit copy, only entity 0's asset and position changed, entities 100-204 were removed, and 333 entities were appended; every other entity and top-level setting is identical.
- [x] 5.2 Add a `games/control-line` test asserting that every scenery entity's transformed bounds lie ≥ 28 m from the pilot, that the pilot position and plane settings are unchanged, and that no new colliders exist.
- [x] 5.3 Remove the unreferenced Kenney asset folders. Update `games/control-line/README.md` and `project/environment/README.md` (sources, licenses, estimates, WC and staff use of the utility building). Verify: links resolve. The unreferenced previous asset folders were untracked in git, so they were moved to `~/.cache/abyssus-airfield/previous-assets` instead of being deleted.

## 6. Integrated verification

- [x] 6.1 Run `./gradlew :games:control-line:test`. Verify: all three planes take off on the pavement and flight and game-flow tests pass.
- [ ] 6.2 In a copy of ControlLine, run `runIde`: compare a top-down and an oblique view with `img.png` (pad, rings, landmark directions and distances, vegetation density), then check undo/redo of a scene edit.
- [ ] 6.3 Run `./gradlew :games:control-line:run` and check the midday look and shadows from the pilot camera, plane select and takeoff.
- [x] 6.4 Run `./gradlew check` and `scripts/check-docs.sh`, and report pre-existing failures separately. Rewrite `verification.md` for the new version and keep the previous evidence under a "Previous version" heading.
