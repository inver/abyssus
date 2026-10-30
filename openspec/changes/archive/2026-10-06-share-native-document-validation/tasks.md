# Tasks

## 1. Shared admission and JVM readers

- [x] 1.1 Add headless rejection and unchanged-input tests, move the validator into core, and guard project, scene and saved metadata binding. Verify `NativeDocumentAdmissionTest`, `AbyssusDocumentFormatTest` and `AssetMetaLoaderTest` with `./gradlew :core:test`; update core/file-format docs and run `scripts/check-docs.sh`.
- [x] 1.2 Guard raw ECS load/write and direct render serialization; test rejection before engine mutation and opaque extension round trips. Verify `NativeEcsAdmissionTest` and `./gradlew :runtime:test`; update runtime docs.

## 2. Editor integration and dependency

- [x] 2.1 Reuse shared validation in editor saved/unsaved asset loading, retaining localized errors and source aliases. Verify `AssetLoadingTest`, `NativeDocumentReadTest`, `NativeDocumentWriteGuardTest`, `AssetMetaReaderTest` and `ProjectAssetsTest` with `./gradlew :test` filters; update architecture and link the prerequisite in refactor-solid-dedup.

## 3. Integration

- [x] 3.1 Run `./gradlew check`, `scripts/check-docs.sh` and `openspec validate share-native-document-validation --strict`; record pre-existing failures separately and leave this gate open if check cannot pass. Do not silently broaden scope.
