# Custom component fixture

A native Abyssus project for game-declared components (`add-custom-components`). `scenes/Field.scene` has entity `0`
(`Plane`, with `"PlaneComponent": {"lineLength": 22, "kind": "STUNT"}` and a render component naming the `tree` model)
and entity `1` (`Pilot`, no plane). `assets/tree` is copied from `Untitled`.

`abyssus/components.schema.json` is the exported schema of the test-only `PlaneComponent` in
`projects/lib-runtime/src/test/kotlin/net/nevinsky/abyssus/lib/runtime/schema/PlaneComponent.kt`, one field of each type.
`SchemaFileTest` requires the export to reproduce it byte for byte, so change both together.

Don't open this folder as the `runIde` project: edits there change what tests assert on. Use a copy.
