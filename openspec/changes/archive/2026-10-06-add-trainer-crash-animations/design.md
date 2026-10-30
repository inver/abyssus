# Design

## 1. Clips are part of the imported asset, made by the importer

`model.glb` is written by `importTrainer`, so hand-made clips would be lost on the next import. A post-step,
`withCrashAnimations`, takes the imported GLB (each part a root node with model-frame vertices) and rewrites it:

- It cuts `Wings` at x = ±0.0615 m (the wing roots) and `Fuselage` and `VTail` at z = -0.30 m, interpolating the
  attributes of the cut triangles. The cut faces are not capped.
- It gives each moving part a pivot (hinge, strut top, break line) by moving its vertices by the pivot and setting the
  node's translation, and hangs every part under one `Trainer` node. World positions at rest are unchanged.
- It samples hand-authored keys (pose relative to rest, Euler angles, linear or eased) at 30 Hz. While sampling it keeps
  every moving part at or above the ground under the parked wheels (y = -0.136) and puts landed debris on it.

Running the import again gives the same bytes.

## 2. Severity from the sink speed

The flight already calls a touch a crash when the plane comes down faster than 3 m/s (its sink speed before the touch).
The severity uses that same speed: below 6 m/s `LITTLE`, below 10 m/s `MEDIUM`, from 10 m/s `FULL`. The thresholds are
estimates: a shallow 10° glide into the ground at 24 m/s comes down at about 4 m/s, a 45° dive at about 17 m/s. An
upside-down touch slower than 3 m/s is a crash too, and gets `LITTLE`. The lines going slack is not a crash into the
ground and has no severity.

## 3. Playing it

`FieldRenderer.draw` takes the clips to play per entity and the frame time. It starts a clip once, the first time an
entity is drawn with it (an `AnimationController`, one loop), advances it every frame and holds the last frame. A model
without the clip is skipped. The controllers belong to the drawn scene and go with it; a new flight loads a new scene.
The physics body keeps moving after the crash, and the clip plays on top of the body's pose.
