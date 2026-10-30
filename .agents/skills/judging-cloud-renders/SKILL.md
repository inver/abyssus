---
name: judging-cloud-renders
description: Evaluate, score, and diagnose OpenGL or libGDX cloud shaders and volumetric cloud renders, with emphasis on the Abyssus (IntelliJ + libGDX) editor and target hardware down to a GTX 750 Ti. Use when the user asks to review, critique, score, QA, compare, or sign off on a cloud rendering, cloud shader, volumetric raymarch implementation, cloud layer, or cloud visual. Also use when diagnosing specific cloud artifacts (flicker, banding, tiling, ghosting, wrong lighting, depth errors, editor slowdown).
license: MIT
---

# Judging Cloud Renders

A repeatable procedure for scoring cloud shaders on visual quality, lighting fidelity, temporal stability, performance,
and integration correctness. Emits a weighted 0–5 report plus a prioritized defect list.

## 1. When to use / not use

**Use when:**

- A cloud shader, cloud layer, or volumetric cloud pass needs review or scoring.
- Two cloud implementations need an A/B comparison.
- A specific cloud artifact needs diagnosis.
- A PR or asset needs a sign-off against defined criteria.

**Do not use for:**

- Offline / path-traced cloud renderers (this rubric targets real-time GL).
- Non-cloud atmospheric effects (fog, haze, god rays alone) — use a different skill.

## 2. Required inputs

Ask for these before scoring. If missing, score only what can be observed and mark the report `PARTIAL`.

| Input                                                                  | Needed for | If missing                       |
|------------------------------------------------------------------------|------------|----------------------------------|
| Screenshots: front-lit, side-lit, back-lit, sunset, horizon view       | A, B, E    | Cannot score B2, B3 — mark `N/A` |
| Shader source (GLSL)                                                   | A1, C2, E4 | Score visuals only               |
| Target GPU + resolution + measured frame time (GPU timer, not CPU FPS) | C          | Mark C `UNVERIFIED`              |
| Static-camera capture (≥5 s)                                           | C, D       | Mark D3 `UNTESTED`               |
| Camera fly-through into cloud                                          | D4         | Mark D4 `UNTESTED`               |
| Context: Abyssus scene, libGDX version, GL20 vs GL30 path              | A          | Assume libGDX GL20 default       |

## 3. Procedure

Run the tests in this order. Each test maps to criteria in §4.

1. **Compile & load test** — load through `ShaderProgram` in the Abyssus Sandbox IDE. Record any compile/link warnings.
   (`A1`, `E1`)
2. **Scene integrity test** — enable clouds in a scene containing terrain, water, sky, and gizmos. Look for z-fighting,
   vanishing objects, state leaks. (`A2`, `A5`, `B5`, `E2`)
3. **Sun sweep** — rotate sun elevation 0° → 90° in Abyssus properties while comparing cloud lighting to the procedural
   sky. (`A3`, `B3`, `B5`, `E3`)
4. **Orbit test** — circle a single cloud at near/mid/far distances. Check silhouette, fractal detail, and absence of
   blobs. (`B1`, `B4`)
5. **Horizon flight** — fly a long straight path through the cloud layer at grazing angle. Check tiling, atmospheric
   fade, edge softness. (`B2`, `B4`)
6. **Static-frame diff** — hold camera and sun fixed, capture ≥5 s, diff frames. Any non-zero delta outside intended
   animation is a defect. (`D3`, `C1`)
7. **Fly-through** — drive the camera through a cloud volume and out. Check interior looks volumetric, not flat white;
   no step-size popping. (`D4`)
8. **Perf profile** — GPU timer queries at target resolution; then repeat with no cloud in view (sky only), then at
   half/quarter render scale. (`C1`–`C4`)
9. **Low-end test** — run on GTX 750 Ti–class GPU or forced low profile. Record frame time and any fallback behavior.
   (`C5`, `D5`, `E1`)
10. **Source review** — inspect for unbounded loops, per-step noise fetches, unnecessary branches, and missing precision
    handling. (`C2`, `E4`)

## 4. Rubric

Weighted 0–5. Anchors are defined once in §5; each criterion lists what a `5` looks like.

### A. Integration & libGDX compatibility — 25%

| #  | Criterion                   | What a 5 looks like                                                                                            |
|----|-----------------------------|----------------------------------------------------------------------------------------------------------------|
| A1 | libGDX shader compatibility | Compiles clean as `ShaderProgram` on GL20; no desktop-only extensions; no warnings                             |
| A2 | Scene-view integration      | Coexists with terrain, water, skybox, and gizmos; correct depth ordering; zero state leaks                     |
| A3 | Sky & lighting consistency  | Sun direction and ambient colors come from the same source as Abyssus's procedural sky; changes propagate live |
| A4 | Cloud-shadow compatibility  | Volumetric visibility and terrain cloud-shadows agree; toggling one does not contradict the other              |
| A5 | Resource hygiene            | All GPU resources follow `Disposable`; no leaks over a 30-min editor session; `KHR_debug` clean                |

### B. Visual quality — 25%

| #  | Criterion                      | What a 5 looks like                                                                                 |
|----|--------------------------------|-----------------------------------------------------------------------------------------------------|
| B1 | Plausible shape                | Billowy, cauliflower silhouettes with multi-scale detail; no sphere blobs; convincing at game scale |
| B2 | Soft edges & atmospheric blend | Ragged but soft edges; distant clouds desaturate into horizon color; no aliasing                    |
| B3 | Sun-direction response         | Silver lining back-lit; deeper interiors away from sun; matches sky sun position exactly            |
| B4 | No tiling                      | Domain-warped or multi-octave noise; no visible grid or repetition over a long flight path          |
| B5 | Depth-aware compositing        | Correct occlusion against terrain/water/objects; soft intersection at opaque geometry               |

### C. Performance — 20%

| #  | Criterion             | What a 5 looks like                                                                       |
|----|-----------------------|-------------------------------------------------------------------------------------------|
| C1 | Frame time            | Holds target budget on GTX 750 Ti–class GPU at stated resolution, measured via GPU timer  |
| C2 | Step discipline       | Bounded loops (≤64 steps typical), empty-space skipping, no per-step branches on uniforms |
| C3 | Resolution scaling    | Half/quarter-res cloud target upscales without obvious blur or crawling                   |
| C4 | Texture memory        | Noise volumes ≤128³, filtered and mipmapped; no multi-MB uncompressed 3D textures         |
| C5 | No compute dependency | Fully functional on the GL20 path; compute path optional, not required                    |

### D. Robustness & usability — 15%

| #  | Criterion            | What a 5 looks like                                                                               |
|----|----------------------|---------------------------------------------------------------------------------------------------|
| D1 | Parameter exposure   | Coverage, density, wind, sun response, detail scale editable in Abyssus Properties, live-updating |
| D2 | Undo/redo            | Parameter edits behave like other scene edits under undo/redo                                     |
| D3 | Static stability     | Zero per-frame flicker with static camera and sun                                                 |
| D4 | Fly-through          | Interior reads as volumetric, not flat white; no pop or crash                                     |
| D5 | Graceful degradation | Falls back to a cheaper path or disables with a clear message on low-end GPUs                     |

### E. Reference comparison & source quality — 15%

| #  | Criterion                       | What a 5 looks like                                                                                          |
|----|---------------------------------|--------------------------------------------------------------------------------------------------------------|
| E1 | Fit to libGDX visual language   | Convincing at game scale without pretending to be offline-photoreal                                          |
| E2 | Consistency with Abyssus assets | Sits naturally beside Abyssus water and sky; not a mismatched asset                                          |
| E3 | Art-direction range             | Tunable across fair-weather, overcast, and sunset without shader edits                                       |
| E4 | Source quality                  | Bounded loops, empty-space skip, precision qualifiers, no desktop-only extensions; readable and maintainable |

## 5. Score anchors

Apply to every criterion:

| Score | Meaning                                                           |
|-------|-------------------------------------------------------------------|
| 0     | Absent or broken — feature does not exist or crashes the editor   |
| 1     | Placeholder — visibly wrong; blocks use                           |
| 2     | Weak — present but obviously deficient                            |
| 3     | Acceptable — functional, ships at a push                          |
| 4     | Good — clearly above baseline, minor defects                      |
| 5     | Reference-quality — matches or beats a strong real-time reference |

Use `N/A` only when the required input is missing; do not score `N/A` as `0`.
When any criterion is `N/A`, mark the report `PARTIAL` and state which inputs would complete it.

## 6. Red flags (automatic cap)

Any red flag caps the final weighted score at **2.0**, regardless of other categories. List every red flag found in the
report.

1. Hard tiling visible as a repeating grid across the cloud layer.
2. Depth-order errors — clouds drawn over foreground geometry, or vice versa.
3. Per-frame flicker with static camera and sun.
4. Sun direction inconsistent between sky, cloud shading, and shadows.
5. No volumetric self-shadowing — brightness independent of sun angle.
6. Shader compile failure or GL errors on load in libGDX.
7. Unbounded raymarch loop that locks the editor on target hardware.
8. Frame time > 2× budget with no documented quality trade-off.

## 7. Scoring

1. Score every applicable criterion 0–5 (or `N/A` / `UNTESTED` / `UNVERIFIED`).
2. For each category, compute the simple average of its scored criteria (ignore `N/A`).
3. Weighted total =  
   `0.25 × A_avg + 0.25 × B_avg + 0.20 × C_avg + 0.15 × D_avg + 0.15 × E_avg`
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
| A Integration | 25% | x.x | ... |
| B Visual quality | 25% | x.x | ... |
| C Performance | 20% | x.x | ... |
| D Robustness | 15% | x.x | ... |
| E Reference & source | 15% | x.x | ... |

## Criterion detail
[table or bullet list of every criterion with score + one-line justification]

## Prioritized defects
1. [severity] description — suggested fix
2. ...

## Sign-off recommendation
- Ship / Ship with fixes / Needs rework / Blocked
- Missing inputs that would complete a full score (if PARTIAL)