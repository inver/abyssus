# Tasks

## 1. Make the screens render

- [x] 1.1 Add a GL test that boots `GameUiHost` with a back buffer twice the logical size, draws the main menu, asserts that pixels under the first button differ from the background, and that a click at the button's logical centre presses it (verify: `./gradlew :app-game-control-line:test --tests '*GameUiHiDpiGlTest' -Dabyssus.glTests=true`, watched to fail on the pre-fix sizing and pass now). Fix `GameUiHost` until it does.
- [x] 1.2 Pin the centring pattern in a `lib-core` layout test (a root panel holding a column anchored at the centre puts the column in the middle of a 1280x720 screen; a root column's own anchor does not move it) and author every screen's root that way in `screens/meta.json` (verify: `./gradlew :lib-core:test --tests '*UiLayout*'`; `GameUiGlTest` and the main-menu pixel test show the button column centred).
- [x] 1.3 Look at the running game: the main menu is visible and centred on this machine's HiDPI display (verified 2026-10-12 from a frame read back from `./gradlew :app-game-control-line:run`: menu column centred, buttons and title drawn, first button focused). Clicking is covered by `GameUiHiDpiGlTest`, not yet tried by hand; that is part of 5.3.

## 2. Style support in the renderer

- [x] 2.1 Add the `uppercase` style key to the painter and measurer, and to the editor's style key list and docs (verify: a `lib-core` test draws and measures "Free flight" as upper case while `view.text` is unchanged; `docs/ai/file-formats.md` lists the key and `scripts/check-docs.sh` passes).

## 3. Bundled fonts and art

- [x] 3.1 Write the generator tool that renders Barlow Condensed (500, 700) and IBM Plex Mono (400, 500) from their OFL sources into `.fnt` plus PNG at the sizes the styles use, with the licence texts beside them; run it and commit the output into `assets/screens/` (verify: `./gradlew :app-game-control-line:generateUiFonts` reproduces the committed files byte for byte, and `UiResourcePreparer` reports no issues for the asset). (Done 2026-10-12: sources and OFL texts in `tools/fonts`, `./gradlew :app-game-control-line:generateUiFonts` reproduces the files byte for byte, 14 fonts, about 700 KB; `ControlLineUiAssetTest` shows the preparer reports no issues. Sizes: display 28 32 44 64 96, displayMedium 24 28, mono 12 14 15 18; IBM Plex Mono Medium was dropped as unused.)
- [x] 3.2 Add the illustration generator (menu track with plane, plane top view) to the same tool and commit its PNGs (verify: the generated images exist, are referenced by resource ids in the asset, and the editor preview shows them). (Done: `generateUiArt` writes `art/menu-track.png`, used by the main menu. The plane top view was dropped: plane select keeps the 3D plane and the contract has no numbers for stat bars. Not yet opened in the editor preview; that is part of 5.3.)

## 4. Styles and screens

- [x] 4.1 Define the palette and the named styles in `screens/meta.json` (ground, panels, HUD panel with alpha, display / body / muted / accent text, primary and outline buttons with hover, pressed, focused and disabled states, tabs, table row highlight, the existing role styles) and register the fonts and images as resources (verify: `ControlLineUiAssetTest` admits the asset with no warnings and a test checks every used `fontSize` is within 25% of an available font size). (Done: 26 named styles, 12 fonts and one image registered; `ControlLineUiAssetTest` also checks every styled `fontSize` equals its font's line height and every widget style is defined.)
- [x] 4.2 Restyle main menu, assist select and plane select to the design (title block, track art, button stack, plane card with stat bars over the 3D view, key hints), keeping bindings, actions and keys (verify: `GameUiGlTest` and `ControlLineUiAssetTest` pass; screenshots of each at scale 1 and 2 saved for review). (Done: pictures of every screen at scale 1 are written by `GameScreenshotsGlTest` to `build/screenshots`; scale 2 is covered by `GameUiHiDpiGlTest`. Plane select sits on a translucent panel.)
- [x] 4.3 Restyle the free and pattern HUD (translucent panels, tension bar, score and combo block, toast, hint) and the pause overlay (verify: GL test shows every HUD value inside a panel; visual check over bright sky). (Done: HUD panels, hint and toast on panels, pause over a dimmed HUD, checked in the pictures over a sky-blue stand-in, not yet over the real field.)
- [x] 4.4 Restyle name entry and game over, the scores screen with its tabs and table, and the debrief (verify: GL tests for each; the score-table highlight and "No flights yet" still show). (Done: game over is a two-column layout so the 15-row sheet fits 720 px; tabs are centred columns; checked in the pictures.)
- [x] 4.5 Re-record `characterization/screens.golden` once and review the diff: only intended static text changes (verify: `./gradlew :app-game-control-line:test -Dabyssus.glTests=true`). (No re-record was needed: the restyle changed no text, so `screens.golden` is unchanged and passes.)

## 5. Docs and verification

- [x] 5.1 Update the Control Line README "Screens" section (style names, how to regenerate fonts and art, the style-key limits) and `docs/ai/testing.md` (verify: `scripts/check-docs.sh`).
- [x] 5.2 Run `./gradlew check`, the GL tests of `lib-core`, `plugin-abyssus` and `app-game-control-line` with `-Dabyssus.glTests=true`, and record results, repo size added by binaries, and any check that could not run.
- [ ] 5.3 Hand check in the running game: menu to flight, pause, game over with name entry, scores and a debrief on a HiDPI display, against the design artifact; record results separately from automated tests.

## Notes

- 5.2 (2026-10-12): `./gradlew check --continue` passes. The earlier `gameui -> projectView -> gameui` package cycle came from uncommitted work on tree navigation to UI screens; it is fixed by a `UiScreenOpener` interface in `projectView` that `gameui` implements as a project service (registered in `plugin.xml`). With `-Dabyssus.glTests=true`: `lib-core` (73 suites), `app-game-control-line` (43) and the `plugin-abyssus` `gameui` tests (9) pass. The full `plugin-abyssus` GL run did not finish: it ran over 40 minutes and was stopped, with no failure in the result files written up to then, so it counts as a check that could not run. `buildPlugin` succeeds; the fonts and art (about 750 KB) live in the game's project folder and are not in the plugin zip.
- Side finding: the game's `test` task did not treat the bundled project folder as an input, so a change to `screens/meta.json` alone could leave the tests up to date; it does now.
- 5.3 and the real-field look of the HUD are still to be checked by hand.
