# Tasks

## 1. Spike and rule

- [x] 1.1 Confirm the native contract from `decouple-from-mundus` is in place: short component names are the stable
      identifiers, `AbyssusDocumentFormat` treats an unregistered or custom component payload as opaque, and the loader
      and writer keep an unknown component raw. No spike against another editor is needed. Verify:
      `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.format.AbyssusDocumentFormatTest'` (opaque custom
      payload case) and `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.runtime.ecs.SceneEcsLoaderTest' --tests
      'net.nevinsky.abyssus.runtime.ecs.SceneEcsWriterTest'` (unknown component kept) pass
- [x] 1.2 Record the rule for game components in `docs/ai/file-formats.md` under "Game components": they are native
      extension data keyed by short name, written without class names or identifier tables, and a scene that holds
      `ecs.componentIdentifiers` is rejected. Verify: `scripts/check-docs.sh` passes and `openspec validate
      add-custom-components --strict` still passes

## 2. Fixture

- [x] 2.1 Add `src/test/testData/project/Custom` as design decision 8 (`.abss`, one scene with entities `0` and `1`,
      the `tree` model folder, `abyssus/components.schema.json`). Verify: `SceneLoadingTest` gains
      `customProjectLoadsWithoutItsGame` (the plane is kept raw with one warning) and passes with
      `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.runtime.SceneLoadingTest'`

## 3. Runtime: declaring and encoding

- [x] 3.1 Add `@SceneComponent`, `@Field`, `@EntityRef`, `@AssetRef`, `ComponentSchema` and `ComponentSchemaReader`
      (design decision 1), with a test-only `PlaneComponent` in `runtime`'s test fixtures. Verify:
      `ComponentSchemaReaderTest` (plane schema: every field's type, default, limits, group; failures for a taken name,
      a `List` field and a class without a no-argument constructor, each naming the reason) passes with
      `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.runtime.schema.*'`
- [x] 3.2 Add `SchemaJson` (design decision 2). Verify: `SchemaJsonTest` covers each scenario of "Declared components
      load", "Supported field types", "Defaults are not written" and "Unusable values fall back" in
      `custom-scene-components` (`lineLength` `"long"` and `2` fall back to `18` with one message each naming entity,
      component and field; `25.0` writes `25`; all-default writes `{}`) and passes with the same command as 3.1
- [x] 3.3 Add `ReflectiveCodec` and the registry in `ComponentCodecs` with `ComponentRegistry` passed through
      `SceneLoading` (design decision 3); `SceneEcsWriter` writes the short name only. Verify:
      `SchemaJsonRoundTripTest` (every field type gives identical text through `ReflectiveCodec` and `SchemaJson`),
      `ComponentCodecsTest.aGameCannotTakeABuiltInName`, and `SceneEcsWriterTest.declaredComponentWritesNoIdentifierTable`
      pass with `./gradlew :runtime:test`

## 4. Runtime: schema file

- [x] 4.1 Add `SchemaFile` (write and parse, design decision 4) and `SchemaExportMain`. Verify: `SchemaFileTest`
      (exporting the test `PlaneComponent` into a temp copy of `Custom` reproduces its `components.schema.json` byte
      for byte; a second export is byte-identical; the `abyssus` folder is created when missing; no `.scene` or
      `.abss` changes; unknown `version` and unknown field type are rejected per component with a message) passes with
      `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.runtime.schema.*'`
- [x] 4.2 Document declaring, registering and exporting components in `runtime/README.md`, with the `JavaExec` task a
      game adds. Verify: `scripts/check-docs.sh` passes and the snippet's class and argument names match
      `SchemaExportMain`

## 5. Plugin: schemas and the extension point

- [x] 5.1 Declare the extension point `net.nevinsky.abyssus.componentSchemas` (`ComponentSchemaBean`, `resource`
      attribute, `dynamic="true"`) in `plugin.xml`, and add `ComponentSchemas` with the merge as a pure function
      (design decision 5). Verify: `ComponentSchemasTest` (project wins per name with one conflict message naming the
      plugin; a broken file gives one message naming `components.schema.json`; a removed contribution drops its
      components) passes with `./gradlew :test --tests 'net.nevinsky.abyssus.schema.ComponentSchemasTest'`
- [x] 5.2 Make `ComponentEditor` an instance built from the schemas, with `SchemaCodec` / `SchemaValues`, the new
      `INT` and `BOOLEAN` field kinds, limit and choice checks and typed asset references; update its callers. Verify: `ComponentEditorTest` gains the scenarios of the
      `scene-component-editing` delta that use `Custom` (add to entity `1` writes `{}` and no identifier table;
      `lineLength` `25` written, `2` rejected naming the minimum; `pilot` `99` rejected; the add list offers `PlaneComponent` but never `PickableComponent`) and passes, with all
      its existing cases, using `./gradlew :test --tests 'net.nevinsky.abyssus.ecs.ComponentEditorTest'`

## 6. Plugin: panel

- [x] 6.1 Show schema components in `PanelState` / `EntityDetailsView`: declared labels, group sub-headings, a
      checkbox for `BOOLEAN`, and Add component / Remove for schema kinds. Verify: `EntityPropertiesPanelTest` gains
      `schemaComponentShowsGroupedLabelledFields` and `addPlaneFromThePanel` (entity `1` of `Custom`) and passes with
      `./gradlew :test --tests 'net.nevinsky.abyssus.properties.EntityPropertiesPanelTest'`
- [x] 6.2 Update `docs/ai/file-formats.md` (game components, the schema file),
      `docs/ai/architecture.md` (extension points, the schema flow), `src/main/kotlin/net/nevinsky/abyssus/ecs/README.md`
      and `docs/ai/testing.md` (the `Custom` fixture). Verify: `scripts/check-docs.sh` passes

## 7. Integration

- [ ] 7.1 In a copy of the `Custom` project, run `./gradlew runIde` and check: (1) entity `0`'s Plane section shows
      `lineLength` `22` under `Lines`, a checkbox for `hasTipWeight` and x / y / z for `leadout`; (2) setting
      `lineLength` to `25` writes it and Undo restores the text; (3) Add component > Plane on entity `1` adds `{}`;
      (4) editing `components.schema.json` to add a field shows it without reopening; (5) breaking the schema file
      shows one notification and the plane turns read-only JSON; (6) a test plugin contributing `MarkerComponent`
      (built from `src/test`) offers it on `Untitled`'s `Model 0`, and unloading that plugin turns it read-only
- [ ] 7.2 Run `./gradlew check` and `scripts/check-docs.sh`. Verify: both pass
