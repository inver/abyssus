---
name: judging-cloud-renders
description: Evaluate, score, and diagnose OpenGL or libGDX cloud shaders and volumetric cloud renders for realistic appearance, with emphasis on the Abyssus (IntelliJ + libGDX) editor and target hardware down to a GTX 750 Ti. Use when the user asks to review, critique, score, QA, compare, or sign off on a cloud rendering, cloud shader, volumetric raymarch implementation, cloud layer, or cloud visual. Also use when diagnosing specific cloud artifacts (flicker, banding, tiling, ghosting, wrong lighting, depth errors, editor slowdown, plastic look, missing silver lining).
license: MIT
---

# Judging Cloud Renders

A repeatable procedure for scoring cloud shaders on visual realism, lighting fidelity, temporal stability, performance,
and integration correctness. Emits a weighted 0–5 report plus a prioritized defect list. Realism is judged against
strong real-time references (e.g. Nubis-style, modern flight-sim approximations) while respecting the GL20 / GTX 750 Ti
budget — offline path-traced quality is never required.

## 1. When to use / not use

**Use when:**

- A cloud shader, cloud layer, or volumetric cloud pass needs review or scoring for realism.
- Two cloud implementations need an A/B comparison.
- A specific cloud artifact needs diagnosis (including plastic look, missing multi-scatter cues, or weak edges).
- A PR or asset needs a sign-off against defined criteria.

**Do not use for:**

- Offline / path-traced cloud renderers (this rubric targets real-time GL).
- Non-cloud atmospheric effects (fog, haze, god rays alone) — use a different skill.

## 2. Required inputs

Ask for these before scoring. If missing, score only what can be observed and mark the report `PARTIAL`.

| Input                                                                                      | Needed for | If missing                                   |
|--------------------------------------------------------------------------------------------|------------|----------------------------------------------|
| Screenshots / video: front-lit, side-lit, back-lit, sunset, horizon, and close edge detail | A, B, E    | Cannot score B2, B3, B6 — mark `N/A`         |
| Animated sequence (≥8 s) with wind / time evolution                                        | B7, D3     | Mark B7 and animation parts of D3 `UNTESTED` |
| Shader source (GLSL)                                                                       | A1, C2, E4 | Score visuals only                           |
| Target GPU + resolution + measured frame time (GPU timer, not CPU FPS)                     | C          | Mark C `UNVERIFIED`                          |
| Static-camera capture (≥5 s)                                                               | C, D       | Mark D3 `UNTESTED`                           |
| Camera fly-through into / through cloud                                                    | D4, B6     | Mark D4 `UNTESTED`                           |
| Context: Abyssus scene, libGDX version, GL20 vs GL30 path, weather presets if any          | A, E3      | Assume libGDX GL20 default                   |

## 3. Procedure

Run the tests in this order. Each test maps to criteria in §4.

1. **Compile & load test** — load through `ShaderProgram` in the Abyssus Sandbox IDE. Record any compile/link warnings.
   (`A1`, `E1`)
2. **Scene integrity test** — enable clouds in a scene containing terrain, water, sky, and gizmos. Look for z-fighting,
   vanishing objects, state leaks. (`A2`, `A5`, `B5`, `E2`)
3. **Sun sweep** — rotate sun elevation 0° → 90° (and a full azimuth if possible) while comparing cloud lighting, silver
   lining, and self-shadowing to the procedural sky. (`A3`, `B3`, `B5`, `B6`, `E3`)
4. **Orbit & edge test** — circle a single cloud at near/mid/far distances. Inspect silhouette, multi-scale fractal
   detail, and high-frequency edge raggedness (no soft blobs or hard cutoffs). (`B1`, `B2`, `B4`)
5. **Horizon flight** — fly a long straight path through the cloud layer at grazing angle. Check tiling, atmospheric
   fade, height stratification, and edge softness. (`B2`, `B4`, `B7`)
6. **Static-frame diff** — hold camera and sun fixed, capture ≥5 s, diff frames. Any non-zero delta outside intended
   animation is a defect. (`D3`, `C1`)
7. **Fly-through** — drive the camera through a cloud volume and out. Check interior looks volumetric with correct
   extinction and multi-scatter darkening, not flat white or milky; no step-size popping. (`D4`, `B6`)
8. **Animation / wind test** — advance time or wind parameters; verify coherent advection, no swimming noise, no density
   popping, and stable large-scale shapes. (`B7`, `D3`)
9. **Perf profile** — GPU timer queries at target resolution; then repeat with no cloud in view (sky only), then at
   half/quarter render scale. (`C1`–`C4`)
10. **Low-end test** — run on GTX 750 Ti–class GPU or forced low profile. Record frame time and any fallback behavior.
    (`C5`, `D5`, `E1`)
11. **Source review** — inspect for unbounded loops, per-step noise fetches, missing phase/HG term, lack of empty-space
    skipping or adaptive steps, and missing precision handling. (`C2`, `E4`)

## 4. Rubric

Weighted 0–5. Anchors are defined once in §5; each criterion lists what a `5` looks like. Realism language is calibrated
for real-time GL, not offline.

### A. Integration & libGDX compatibility — 20%

| #  | Criterion                   | What a 5 looks like                                                                                                  |
|----|-----------------------------|----------------------------------------------------------------------------------------------------------------------|
| A1 | libGDX shader compatibility | Compiles clean as `ShaderProgram` on GL20; no desktop-only extensions; no warnings                                   |
| A2 | Scene-view integration      | Coexists with terrain, water, skybox, and gizmos; correct depth ordering; zero state leaks                           |
| A3 | Sky & lighting consistency  | Sun direction, ambient, and sky colors come from the same source as Abyssus's procedural sky; changes propagate live |
| A4 | Cloud-shadow compatibility  | Volumetric visibility and terrain cloud-shadows agree; toggling one does not contradict the other                    |
| A5 | Resource hygiene            | All GPU resources follow `Disposable`; no leaks over a 30-min editor session; `KHR_debug` clean                      |

### B. Visual realism — 30%

| #  | Criterion                      | What a 5 looks like                                                                                                                                                                                                        |
|----|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| B1 | Plausible multi-scale shape    | Billowy / cauliflower / layered silhouettes with clear low-, mid-, and high-frequency detail; no sphere blobs or uniform noise; convincing at game scale and close-up                                                      |
| B2 | Soft edges & atmospheric blend | Ragged but soft high-frequency edges; distant clouds desaturate and fade into horizon color; no aliasing or hard cutoffs                                                                                                   |
| B3 | Sun-direction response         | Strong silver lining when back-lit; deeper, cooler interiors away from sun; exact match to sky sun position                                                                                                                |
| B4 | No tiling / repetition         | Domain-warped or multi-octave noise + weather map or large-scale control; no visible grid or period over long flight paths                                                                                                 |
| B5 | Depth-aware compositing        | Correct occlusion against terrain/water/objects; soft, believable intersection at opaque geometry                                                                                                                          |
| B6 | Scattering & lighting fidelity | Visible self-shadowing + approximate multi-scatter darkening inside dense regions; phase function response (forward scatter / HG or equivalent) produces realistic silver lining and limb brightening; not flat or plastic |
| B7 | Coherent animation / wind      | Wind or time evolution advects density coherently; large shapes persist while small detail evolves; no swimming, popping, or discontinuous noise                                                                           |

### C. Performance — 20%

| #  | Criterion             | What a 5 looks like                                                                                         |
|----|-----------------------|-------------------------------------------------------------------------------------------------------------|
| C1 | Frame time            | Holds target budget on GTX 750 Ti–class GPU at stated resolution, measured via GPU timer                    |
| C2 | Step discipline       | Bounded loops (≤64 steps typical), empty-space skipping or adaptive steps, no per-step branches on uniforms |
| C3 | Resolution scaling    | Half/quarter-res cloud target upscales without obvious blur, crawling, or temporal instability              |
| C4 | Texture memory        | Noise volumes ≤128³, filtered and mipmapped; no multi-MB uncompressed 3D textures                           |
| C5 | No compute dependency | Fully functional on the GL20 path; compute path optional, not required                                      |

### D. Robustness & usability — 15%

| #  | Criterion                   | What a 5 looks like                                                                                                       |
|----|-----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| D1 | Parameter exposure          | Coverage, density, wind, sun response, detail scale, and scatter parameters editable in Abyssus Properties, live-updating |
| D2 | Undo/redo                   | Parameter edits behave like other scene edits under undo/redo                                                             |
| D3 | Static & temporal stability | Zero per-frame flicker with static camera/sun; animation remains stable under wind                                        |
| D4 | Fly-through                 | Interior reads as volumetric with correct extinction and darkening, not flat white or milky; no pop or crash              |
| D5 | Graceful degradation        | Falls back to a cheaper path or disables with a clear message on low-end GPUs                                             |

### E. Reference comparison & source quality — 15%

| #  | Criterion                       | What a 5 looks like                                                                                                                                         |
|----|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| E1 | Fit to libGDX visual language   | Convincing at game scale without pretending to be offline-photoreal; matches the overall fidelity of Abyssus assets                                         |
| E2 | Consistency with Abyssus assets | Sits naturally beside Abyssus water and sky; not a mismatched high-end asset                                                                                |
| E3 | Art-direction & weather range   | Tunable across fair-weather cumulus, overcast, and sunset without shader edits; height stratification visible when expected                                 |
| E4 | Source quality                  | Bounded loops, empty-space skip or adaptive steps, phase/HG term or equivalent, precision qualifiers, no desktop-only extensions; readable and maintainable |

## 5. Score anchors

Apply to every criterion:

| Score | Meaning                                                                                                                                                |
|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0     | Absent or broken — feature does not exist or crashes the editor                                                                                        |
| 1     | Placeholder — visibly wrong; blocks use                                                                                                                |
| 2     | Weak — present but obviously deficient (plastic, flat, tiled, or milky)                                                                                |
| 3     | Acceptable — functional, ships at a push; limited realism                                                                                              |
| 4     | Good — clearly above baseline, minor defects; believable at game distance                                                                              |
| 5     | Reference-quality — matches or beats a strong real-time reference (Nubis-class approximations, modern flight-sim style) under the same hardware budget |

Use `N/A` only when the required input is missing; do not score `N/A` as `0`.
When any criterion is `N/A`, mark the report `PARTIAL` and state which inputs would complete it.

## 6. Red flags (automatic cap)

Any red flag caps the final weighted score at **2.0**, regardless of other categories. List every red flag found in the
report.

1. Hard tiling visible as a repeating grid across the cloud layer.
2. Depth-order errors — clouds drawn over foreground geometry, or vice versa.
3. Per-frame flicker with static camera and sun.
4. Sun direction inconsistent between sky, cloud shading, and shadows.
5. No volumetric self-shadowing — brightness independent of sun angle (flat/plastic lighting).
6. Shader compile failure or GL errors on load in libGDX.
7. Unbounded raymarch loop that locks the editor on target hardware.
8. Frame time > 2× budget with no documented quality trade-off.
9. Obvious step-size banding or wood-grain artifacts that survive any temporal filter.
10. Completely missing phase / forward-scatter response (no silver lining even when strongly back-lit).

## 7. Scoring

1. Score every applicable criterion 0–5 (or `N/A` / `UNTESTED` / `UNVERIFIED`).
2. For each category, compute the simple average of its scored criteria (ignore `N/A`).
3. Weighted total =  
   `0.20 × A_avg + 0.30 × B_avg + 0.20 × C_avg + 0.15 × D_avg + 0.15 × E_avg`
4. Round to one decimal place.
5. If any red flag is present, cap the final score at **2.0**.
6. If any category is entirely missing inputs, mark the whole report `PARTIAL`.

## 8. Report template

Emit a Markdown report in this structure:

```markdown
# Cloud Render Review — [name or PR]

**Score:** X.X / 5.0  [PARTIAL if applicable]
**Red flags:** none | list

## Category scores
| Category | Weight | Avg | Notes |
| -------- | ------ | --- | ----- |
| A Integration | 20% | x.x | ... |
| B Visual realism | 30% | x.x | ... |
| C Performance | 20% | x.x | ... |
| D Robustness | 15% | x.x | ... |
| E Reference & source | 15% | x.x | ... |

## Criterion detail
[table or bullet list of every criterion with score + one-line justification]

## Prioritized defects
1. [severity] description — suggested fix (prefer concrete technique: add HG phase, adaptive step, weather map, etc.)
2. ...

## Sign-off recommendation
- Ship / Ship with fixes / Needs rework / Blocked
- Missing inputs that would complete a full score (if PARTIAL)