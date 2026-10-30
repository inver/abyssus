# Custom component fixture

A native Abyssus project for game-declared components (`add-custom-components`). `scenes/Field.scene` has entity `0`
(`Plane`, with `"PlaneComponent": {"lineLength": 22, "kind": "STUNT"}` and a render component naming the `tree` model)
and entity `1` (`Pilot`, no plane). `assets/tree` is copied from `Untitled`.

`abyssus/components.schema.json` is retained data from the earlier schema export workflow. The current source set
has no schema exporter and does not use this file to extend the built-in component editor. Tests use this fixture
to verify that game components remain read-only and survive edits to built-in components.

Don't open this folder as the `runIde` project: edits there change what tests assert on. Use a copy.
