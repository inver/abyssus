# Import inputs for the UI tests

Local, deterministic inputs the import and Game UI scenarios read. Nothing is downloaded and no user asset is needed; the
expected output facts are fixed in `ImportInputs.kt` and the source hashes in `import-inputs.sha256` (checked by
`ImportInputsTest` through `:plugin-abyssus:uiHelperTest`).

| File | Provenance | Used for |
|---|---|---|
| `model/crate.obj`, `model/crate.mtl`, `model/wood.png` | copies of `projects/lib-core-editor/src/test/resources/modelimport`, hand-written for Abyssus (a 100-unit crate, one material, one 122-byte texture) | the model import cases (7.2): preview, settings, create, collision |
| `model/broken.obj` | a text file that is not a model | the corrupt-file case |
| FlightGear archive | built at run time by `fixtureArchive(dir, license = true)` of `lib-core-editor`'s test fixtures (a plane with one AC3D file, a skin and a licence file), so there is no binary to keep in step | the FlightGear import cases (7.3) |
| `gameui/button.png` | a copy of `wood.png` | the Game UI image import (8.3) |
| `gameui/ui-font.fnt`, `gameui/ui-font.png` | a hand-written one-character bitmap font (text BMFont) and its page, a copy of `wood.png`; written for Abyssus, no third-party content | the Game UI bitmap-font import (8.3) |

Expected model facts (from `ModelImportTest.anObjStagesTheAssetFolder`): importing `crate.obj` as `model_crate` (cm, Y up)
stages `meta.json`, `model.glb`, `textures/wood.png` and `source.json`, and its `meta.json` `additional` is
`{"file":"model.glb","format":"GLTF","binary":true,"materials":[]}`.
