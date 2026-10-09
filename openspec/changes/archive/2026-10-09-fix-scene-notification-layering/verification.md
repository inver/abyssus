# Notification layering verification

The focused command `./gradlew :plugin-abyssus:test --tests '*CanvasOverlayHost*Test' --tests '*SceneViewPanelTest' -Dabyssus.glTests=true` passed all nine tests, including the real macOS GL test (not skipped). The tests verify snapshot pixels, overlay hit routing, context retention, live rendering restoration, non-overlapping/lower layers, and stopping monitoring without revealing the native surface before disposal.

`scripts/check-docs.sh`, `git diff --check`, and strict OpenSpec validation passed. Code review found a cleanup visibility safety edge; the correction was verified by a failing regression followed by a passing run.

The final sequential `./gradlew check --continue` failed in the existing cloud/weather work, outside the notification changes. Cloud binding rejects canonical lowercase enum names and malformed bands; wind binding does not retain the expected values. The weather draft and creation suites fail while binding these cloud settings. None of those library or weather-action files were changed for this fix.

The earlier full GL run was invalidated by concurrent builds rewriting test results and jars. Its class-loading failures are not treated as a reliable regression result; verification above comes from subsequent sequential runs.

Final sequential check: 17 failed tests:

- `CloudSettingsReaderTest.canonicalTypesInferTheirLevelAndDefaults`
- `CloudSettingsReaderTest.canonicalWindAndCompleteSettingsSurviveBinding`
- `CloudSettingsReaderTest.malformedBandIsSkippedWithoutChangingItsTree`
- `CloudSettingsReaderTest.invalidBandsDoNotDiscardValidNeighbours`
- `CloudsLoaderTest.aMalformedBandIsSkippedAndLoggedOnceWhileValidNeighboursLoad`
- `WeatherPresetDraftTest.keepsStoredTechniqueBandsExtensionsAndNumberTextWithoutMutatingSource`
- `WeatherPresetDraftTest.invalidBandIsOmittedAndAcceptedRuntimeFieldsBecomeCanonical`
- `WeatherPresetDraftTest.snapshotDefaultsAndIdentityRoundTripThroughRuntime`
- `NewWeatherPresetActionTest.testNamesAndStagingCollisionsNeverOverwrite`
- `NewWeatherPresetActionTest.testUndoRedoRestoreTheSameIdentityTimestampAndBytes`
- `NewWeatherPresetActionTest.testSourceIsRevalidatedAfterTheDialogAndFailuresDoNotSelect`
- `NewWeatherPresetActionTest.testFreshUuidAvoidsUnsavedAssetIdentities`
- `NewWeatherPresetActionTest.testFailedMetadataWriteRollsBackCreation`
- `NewWeatherPresetActionTest.testUndoGuardSeesSavedAndUnsavedReferencesAndNewFiles`
- `NewWeatherPresetActionTest.testCreationListsAnUnusedCloudAssetAndKeepsAllSourcesUnchanged`
- `NewWeatherPresetActionTest.testEditedSnapshotAndRedoCollisionAreRefusedWithoutWrites`
- `NewWeatherPresetActionTest.testUnsavedSourceAndSkyReferenceAreReadOnCreate`
