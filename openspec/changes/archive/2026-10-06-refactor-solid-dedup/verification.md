# Verification — 2026-10-06

## Baseline continuation

The user instructed continuation after the shared-validation prerequisite reported its failing full check.
Proceed with the refactor's scoped work and focused tests; integration gates remain open. Unrelated airfield work
and existing plugin tests have not been repaired as part of this refactor.

## Completed work

- 1.1: saved/unsaved metadata characterization passes for the Untitled model, malformed JSON, unknown type and
  malformed UUID. Both sources produce equal typed settings, identifiers and metadata, without writing input.
- 1.2: `AssetMetaBinder` owns the settings registration map and binding defaults. Every MetaType and an injected
  settings registration are tested. Core has no platform or GL dependency added.
- 1.3: unsaved metadata uses the binder; its duplicate parser and settings switch are removed.
- 1.4: native admission still precedes binding in saved, unsaved and editor metadata reader paths. Format and project
  asset tests pass.
- 2.1: reproduced the four direct runCatching violations. Baseline output is retained below for the eventual commit
  message; no commit was created in this mixed workspace.
- 2.2: one UUID helper handles missing/malformed identifiers without catching cancellation. Binder, index and
  terrain use it; the unsaved copy is gone. Core tests and `checkNoRunCatching` pass.
- 3.1: prototype behavior tests pass, but its size gate fails (34 code lines versus 15 original lines). The candidate
  and its tests are preserved under `prototype/runtime/schema/`, outside production sources. The existing switches
  remain. See design D3 for counting details. The user subsequently authorized continuation while retaining the switches.

## Task 2.1 baseline / commit-message evidence

`rg -n '\brunCatching\s*\{' src/main/kotlin core/src/main/kotlin` found:

```text
core/.../assets/AssetIndex.kt:25: UUID.fromString inside runCatching
core/.../assets/AssetMetaLoader.kt:67: UUID.fromString inside runCatching
core/.../assets/terrain/TerrainLoader.kt:40: UUID lookup inside runCatching
src/.../AssetLoading.kt:189: UUID.fromString inside runCatching
```

`./gradlew checkNoRunCatching` failed with “Use runCatchingKeepingCancellation instead of runCatching” and all four
paths. Log: `/private/tmp/abyssus-solid-rule-red.log`. After replacement the same check passes.

## Implemented but unchecked

Task 2.3's four duplicate Throwable message expressions now use the core helper; RayBackendService keeps its existing
localized document-display helper for view errors via separate aliases. The only remaining matching expression from
the task's repository search is the intentional helper definition in `core/assets/Throwables.kt`.

`./gradlew :physics-plugin:clean :physics-plugin:test :test` passed all 11 extension tests, but the plugin suite still
reported 69 failures (882 total tests, 40 skipped), matching the previous failure count. The task's full-plugin-suite
verification is not green, so its checkbox remains open. Log: `/private/tmp/abyssus-solid-rule-plugin-suite.log`.

## Review and final verification

A fresh-context code review found no important correctness issues in the binder, UUID parser, message helpers or
prototype disposition. Metadata admission and unsaved-source precedence are preserved.

Final focused command: `./gradlew :core:test :runtime:test :test --tests '*AssetLoadingTest' --tests '*AssetMetaReaderTest' --tests '*ProjectAssetsTest' :physics-plugin:test checkNoRunCatching`.
Passed: 179 core tests (13 skipped), 97 runtime tests, 19 targeted editor tests, 11 extension tests and
`checkNoRunCatching`, with zero failures. Log: `/private/tmp/abyssus-solid-final-focused.log`.

Strict OpenSpec validation of both changes, the docs path check and `git diff --check` pass.
The prerequisite's full integration task and refactor integration/archive tasks remain unchecked.

## Further scoped implementation

- SchemaJson now tests unusable arrays for every FieldType and whole-number limit failure, supplementing the
  existing exact encode/decode, defaults, choice, decimal and vector limit cases. Runtime's full suite passes.
- PlayProtocol's named FrameType keeps literal ids pinned by the all-frame wire/round-trip test. Physics tests pass.
- Shared source checks preserve module exclusions and broaden cancellation scanning. Temporary violations in core,
  runtime and physics singleton checks and raytracing cancellation scanning each failed as intended; removing them
  restored passing checks. Evidence: `/private/tmp/abyssus-solid-injected-checks.log`.
- Lazy core groups and deferred reader/cache lookups compile. Focused AddLightActionTest, PluginBundleTitlesTest,
  ProjectAssetsTest, NativeDocumentReadTest, AssetReadCacheTest, SceneDocumentCacheTest and SceneReaderTest pass.
- The AddLight preset actions failed their new BGT test before the switch and pass afterwards. Tree-reading actions
  remain EDT. Localization keys preserve the five action titles and properties stripe title.
- Ray scene placements now use one identical mapping. FixedStepClock contains the unchanged accumulator arithmetic;
  native resource ownership stays in PhysicsWorld. `:physics:test :physics:checkNoSingletons :raytracing:test` passes.
- Docs now describe the retained schema switches, lazy groups, source-rule scope, service scopes, action threads and
  internal API policy. Docs path checking, strict OpenSpec validation and diff whitespace checking pass.

Fresh-context review of this additional batch reported no actionable introduced correctness issues.
The verifier completed all five IDE targets; it reports binary compatibility but fails the internal-API gate.
See design D9 for exact findings. `manual-verification.md` lists the outstanding sandbox/CI steps.

The thumbnail publication regression using a generated PNG passes together with the action and bundle tests
(`/private/tmp/abyssus-solid-publication-tests.log`). The existing HDR thumbnail test still requires the missing
`Untitled/assets/skybox_hdr/sky.hdr` fixture, so task 10.7 stays open.
The first full check in this batch retained 69 plugin failures (884 tests, 40 skipped); it also caught the removed
`max` import in PhysicsWorld, which was restored and passed the physics suite before the final rerun.

## Historical integration result before prerequisite repairs

`./gradlew check --continue --console=plain` completed after the import fix. All module checks completed; only
root `:test` failed: 885 tests, 69 failures, 40 skipped. Comparing failing test names with the earlier 69-failure
baseline found no new failures and none removed. `test-failures.md` records each observed failure message.
The previously failing Control Line tests do not fail in this final run. No baseline claim is made about the
underlying cause of every assertion without a clean-checkout reproduction.
Log: `/private/tmp/abyssus-solid-final-check.log`; report: `build/reports/tests/test/index.html`.

Passing module counts: runtime 99; physics 39; raytracing 229 (114 skipped, including hardware-gated cases).
Source rules pass. The PNG publication, BGT preset update and localized-title regressions pass in the full suite.
Final docs path check: 191 paths; strict OpenSpec validation and `git diff --check` pass.

Progress is 19/34. Full-suite-gated tasks remain unchecked, as do the already transferred component/panel splits,
the internal-API allowlist policy, remote CI run, manual sandbox checks and archive. The implementation is reviewable
but cannot be archived as completed while these required gates remain open.

## Authorized test-only prerequisite and verifier policy

The user subsequently authorized `repair-native-test-regressions`, without production-logic changes. Its
`verification.md` records the repairs and remaining production/fixture failures. The task 10.5 action suites
now pass, including the off-EDT update regression; progress is 20/34. Full-suite gates remain unchecked.

The internal API policy now checks exact full descriptions in `gradle/plugin-internal-api-allowlist.txt` and
matches each verifier verdict's usage count against its detail report. Injected unknown usage and missing-detail
reports fail; genuine reports pass with configuration-cache reuse. Fresh-context review confirmed the fix.
Root `:verifyPlugin` initially passed all five IDE targets with this policy; log:
`/private/tmp/abyssus-solid-allowlist-verifier.log`. A repeat after root clean failed downloading IDE dependencies
with `No space left on device`; its final internal-API checker passed the reports present, but that repeat is
not a successful verifier run. Log: `/private/tmp/abyssus-solid-allowlist-verifier-final.log`.
Task 10.3 also requires AbyssusViewTest, which remains blocked by shared fixture changes.

The final full check with test-only repairs reports 916 editor tests, 41 failures, 40 skipped, and 99 runtime tests,
6 failures. Other module checks pass. Exact current failures are recorded under the prerequisite; the earlier
69-failure result above is historical, not the current count. Docs check still passes for 198 paths.

## Passing authorized continuation

After the user authorized test-runtime TinyEXR natives and the separate `repair-ray-editor-regressions`
production prerequisite, `./gradlew check --continue --console=plain` passes. Total: 1,644 tests,
zero failures/errors, 177 skipped. Editor 917 (40 skipped), core 216 (13), runtime 99, physics 39,
physics-plugin 11, raytracing 229 (114), gdx-model 27 (9), Control Line 106 (1). Skipped hardware/GL
cases retain their existing gates. Source-rule checks and coverage verification pass.
Log: `/private/tmp/abyssus-solid-continuation-check.log`.

Tasks 2.3, 5.3, 6.2 and 11.1 now meet their full-suite verification gates. The fallback search excludes
only `Throwables.kt`, where the single canonical helper necessarily contains the expression being deduplicated;
no duplicate fallback remains. Injection-failure evidence for shared Gradle rules remains recorded above.
Grouped reader/action tests pass; lazy service grouping and deferred constructors are verified.
The stable Tree fixture resolves the earlier task 10.3 test gate; all HDR chooser cases now pass for task 10.7.
Docs checking passes for 199 paths; all four related changes pass strict OpenSpec validation.

Progress is 27/34. Shared admission, test repairs and the four approved production repairs have all tasks complete.
The seven parent checkboxes still open cover the four splits transferred to `restructure-editor-modules`,
the actual remote CI run, manual sandbox verification and archive. Exact manual steps are in
`manual-verification.md`; this report does not claim those checks were performed.

Fresh `./gradlew :verifyPlugin --console=plain` after all four production repairs passes all five recommended
IDE targets and the exact internal-API allowlist consistency gate. Targets: IC-252.28539.97,
IU-253.33813.55, IU-261.27258.48, IU-262.10968.63 and IU-263.6259.32. Existing deprecated/experimental
API findings remain reported; no new unapproved internal usages occur. Log:
`/private/tmp/abyssus-solid-verifier-continuation.log`; reports: `build/reports/pluginVerifier/`.

Both plugin ZIPs now build with `:buildPlugin :physics-plugin:buildPlugin`. Packaging inspection confirms the
physics extension's two IDE-classpath JARs and absence of test-only TinyEXR native JARs. A disposable native
project copy and the exact sandbox launch command are recorded in `manual-verification.md`. Task 10.9 remains
open: building and inspecting ZIPs does not prove live install/update/unload or disposal behavior.
