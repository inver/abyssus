# Physics fixture

A copy of `Untitled` for `add-jolt-physics`. `scenes/Main Scene.scene` differs from `Untitled`'s only in:

- entity `0` (`Model 0`): at Y `3.086434`, with `"RigidBodyComponent": {}` (dynamic, 1 kg) and
  `"ColliderComponent": {}` (a box with half extents `0.5`);
- entity `1` (`Terrain`): `"ColliderComponent": {"shape": "HEIGHT_FIELD"}`. Its heights are all `0`;
- entity `2` (`Model 2`): a static sphere (`"ColliderComponent": {"shape": "SPHERE"}`) and a rope to `Model 0`,
  `"ConstraintComponent": {"other": 0, "maxDistance": 3}`.

The project has no `abyssus/play.json`, so Play runs physics only. `physics` tests (`PhysicsFixtureTest`,
`PhysicsWorldTest`) and the overlay tests assert on these values.

Don't open this folder as the `runIde` project: edits there change what tests assert on. Use a copy.
