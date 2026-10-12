# Tasks

## 1. Make the screens render

- [ ] 1.1 Add a GL test that boots `GameUiHost` with a back buffer twice the logical size, draws the main menu, asserts that pixels under the first button differ from the background, and that a click at the button's logical centre presses it (verify: `./gradlew :app-game-control-line:test --tests '*GameUiHiDpiGlTest' -Dabyssus.glTests=true`, watched to fail on the pre-fix sizing and pass now). Fix `GameUiHost` until it does.
- [ ] 1.2 Reproduce the root-column centring bug in a `lib-core` layout test (a root column with `anchor: center` in a 1280x720 screen is centred) and fix it in the layout engine without changing other layouts (verify: `./gradlew :lib-core:test --tests '*UiLayout*'`, plus the editor and plugin UI tests still pass).
- [ ] 1.3 Look at the running game: the main menu is visible, centred and clickable on this machine's HiDPI display (verify by hand with `./gradlew :app-game-control-line:run`; record the result).

## 2. Style support in the renderer

- [ ] 2.1 Add the `uppercase` style key to the painter and measurer, and to the editor's style key list and docs (verify: a `lib-core` test draws and measures "Free flight" as upper case while `view.text` is unchanged; `docs/ai/file-formats.md` lists the key and `scripts/check-docs.sh` passes).

## 3. Bundled fonts and art

- [ ] 3.1 Write the generator tool that renders Barlow Condensed (500, 700) and IBM Plex Mono (400, 500) from their OFL sources into `.fnt` plus PNG at the sizes the styles use, with the licence texts beside them; run it and commit the output into `assets/screens/` (verify: `./gradlew :app-game-control-line:generateUiFonts` reproduces the committed files byte for byte, and `UiResourcePreparer` reports no issues for the asset).
- [ ] 3.2 Add the illustration generator (menu track with plane, plane top view) to the same tool and commit its PNGs (verify: the generated images exist, are referenced by resource ids in the asset, and the editor preview shows them).

## 4. Styles and screens

- [ ] 4.1 Define the palette and the named styles in `screens/meta.json` (ground, panels, HUD panel with alpha, display / body / muted / accent text, primary and outline buttons with hover, pressed, focused and disabled states, tabs, table row highlight, the existing role styles) and register the fonts and images as resources (verify: `ControlLineUiAssetTest` admits the asset with no warnings and a test checks every used `fontSize` is within 25% of an available font size).
- [ ] 4.2 Restyle main menu, assist select and plane select to the design (title block, track art, button stack, plane card with stat bars over the 3D view, key hints), keeping bindings, actions and keys (verify: `GameUiGlTest` and `ControlLineUiAssetTest` pass; screenshots of each at scale 1 and 2 saved for review).
- [ ] 4.3 Restyle the free and pattern HUD (translucent panels, tension bar, score and combo block, toast, hint) and the pause overlay (verify: GL test shows every HUD value inside a panel; visual check over bright sky).
- [ ] 4.4 Restyle name entry and game over, the scores screen with its tabs and table, and the debrief (verify: GL tests for each; the score-table highlight and "No flights yet" still show).
- [ ] 4.5 Re-record `characterization/screens.golden` once and review the diff: only intended static text changes (verify: `./gradlew :app-game-control-line:test -Dabyssus.glTests=true`).

## 5. Docs and verification

- [ ] 5.1 Update the Control Line README "Screens" section (style names, how to regenerate fonts and art, the style-key limits) and `docs/ai/testing.md` (verify: `scripts/check-docs.sh`).
- [ ] 5.2 Run `./gradlew check`, the GL tests of `lib-core`, `plugin-abyssus` and `app-game-control-line` with `-Dabyssus.glTests=true`, and record results, repo size added by binaries, and any check that could not run.
- [ ] 5.3 Hand check in the running game: menu to flight, pause, game over with name entry, scores and a debrief on a HiDPI display, against the design artifact; record results separately from automated tests.
