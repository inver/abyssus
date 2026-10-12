# Design

## Context

The `screens` asset (see `migrate-control-line-ui`) defines ten screens at a 1280x720 reference rectangle with a handful of named styles over the bundled default theme. The renderer supports colour, font size, font resource, background, fill, tint, padding, border colour and width; it has no rounded corners, letter-spacing, text transform, gradients or vector art. The design (a canvas of five boards, 1280x720) uses `#0E1A23` ground, `#14293A` raised panel, `#1E3342` rule, `#2B4558` outline, `#FF6A2B` accent, `#E8F1F5` text, `#A9C2D0` secondary, `#8FB3C7` and `#6F8D9E` muted, Barlow Condensed (500, 700) and IBM Plex Mono (400, 500).

The screens are not yet confirmed visible in the real HiDPI window: viewport and scissor are scaled by libGDX's logical HiDPI mode, and `GameUiHost` was sizing the UI in pixels. A change to logical sizes is in place but unverified, and the main menu's root column does not centre.

## Goals / Non-Goals

**Goals:**
- Verified, pixel-checked rendering at scale 1 and 2.
- All ten screens in one palette and type system, expressed as named styles so a designer can retheme by editing styles.

**Non-Goals:**
- Rounded corners, letter-spacing, gradients, animation, a vector renderer.
- Changing flow, published values or keys; the 3D plane select view; new screens.

## Decisions

- **Fix rendering first, with a pixel test.** A GL test boots the host at scale 1 and 2 (an offscreen target with a back buffer larger than the logical size) and asserts that the menu's button area differs from the background and that a click at its logical position presses it. Alternative: trust the layout tests; rejected, they did not catch this bug.
- **Logical-size sizing stays.** The presenter receives `Gdx.graphics.width/height`; libGDX scales viewport and scissor itself. Alternative: switch the window to pixel HiDPI mode; rejected, it changes camera and input maths for the whole game.
- **Fix centring in the layout engine, not the asset.** A root widget's own anchor and pivot place its box in the reference rectangle (sized to its content or fixed size); a regression test in `lib-core` covers a centred column. Alternative: author every screen with absolute offsets; rejected, brittle and unlike how the editor shows it.
- **Fonts as generated bitmap fonts.** A tool in `projects/app-game-control-line/tools/` renders the OFL sources (Barlow Condensed 500 and 700, IBM Plex Mono 400 and 500) with Java2D into `.fnt` plus PNG pages at the sizes the styles use (about 5 to 7 sizes per family, each its own resource since the renderer scales one font by `fontSize`); the output and the OFL licence texts are committed so builds need no network or fonts on the machine. Using one size per resource and scaling by at most about 1.25x keeps text crisp. Alternative: generate at run time from TTF (needs a new dependency and loader); rejected for this change.
- **`uppercase` style key.** Painter and measurer upper-case the string they draw and measure; bindings, editor values and the characterization dump stay unchanged. Alternative: bake upper case into texts; rejected, it would change the published strings and the golden file.
- **Illustrations as committed PNGs from the same tool.** The menu track (three rings, dashed flight circle, handle line, plane silhouette) and the plane top view are drawn with Java2D from the design's geometry and tinted via the `tint` style where useful. Alternative: SVG; rejected, no vector support.
- **Styles, not per-widget overrides.** Named styles: `bg`, `panel`, `hudPanel`, `title`, `display`, `numberBig`, `body`, `muted`, `accent`, `buttonPrimary`, `buttonOutline`, `tab`, `tabSelected`, `rowHighlight`, `fail`, `selected`, `ideal`, `flown`; the existing `highlight`, `muted`, `fail`, `selected`, `ideal`, `flown` names keep their meaning so the published style bindings (`item.style`) need no change. Focus is shown by a 2 px white border (the design's focus ring) plus the existing state hook.
- **Design copy that the game does not publish stays out.** The design's decorative text (a tagline, "TOP VIEW", fake line length and fuel readout) is dropped or only kept where it is static and harmless; the main menu keeps Free flight and F2B pattern buttons. The golden file is re-recorded once, and its diff reviewed line by line to contain only intended static additions.
- **HUD panels** use `#0E1A23` at about 88% alpha (the design's value) rather than a solid ground.

## Risks / Trade-offs

- [Bitmap fonts blur at other scales] -> generate the sizes the styles use, and re-run the tool when a style size changes; add a test that every `fontSize` used maps to a resource within 25% of its native size.
- [Binary assets bloat the repo] -> only needed sizes and one page per font; record the size in the tasks verification.
- [Without letter-spacing the headings look tighter than the design] -> accepted; compensate with the display font's own spacing and size.
- [Pixel test needs GL] -> opt-in with the other GL tests; the layout and style tests run in the plain `check`.
- [Overlap with the open `migrate-control-line-ui` and F2B changes] -> this change only touches look; it starts after the migrate change's manual check, and the characterization golden is the guard.

## Open Questions

- Which fonts' source files to commit (download once from the upstream OFL releases) is for the implementer to fetch; if a file cannot be obtained, fall back to the nearest open condensed and mono faces and record it.
