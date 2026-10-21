# Tasks

## 1. Spikes on this Mac (arm64)

- [ ] 1.1 Build a throwaway `app-image` by hand with `jpackage` from `installDist` output, with `--java-options -XstartOnFirstThread -DcontrolLine.project=$APPDIR/project/ControlLine`, and start it. Verify the game window opens, the main menu shows, a round can be flown, and note where `$APPDIR` points. If the window does not open, record the failure and try `StartOnFirstThreadHelper`, and add a game task to this list before going on.
- [ ] 1.2 Derive the runtime module list with `jdeps --print-module-deps --ignore-missing-deps --multi-release 21` over the shipped classpath, build a `jlink` image from it, and run the app-image of 1.1 on it. Verify a full round runs without a `NoClassDefFoundError`/`ModuleNotFound`; extend the list by hand for any module found missing and keep the final list for task 3.2.
- [x] 1.3 Check what the bundled `project/ControlLine` carries for licenses. Verify by listing it: the GPL-2.0 text for the trainer is present, otherwise add a task 3.1 step that stages it.
- [x] 1.4 Record the findings (where `$APPDIR` points on macOS, the module list, the license result, any launcher surprises) in `design.md` under Decisions or Risks. Verify `openspec validate add-control-line-native-packages --strict` passes.

## 2. Icons

- [x] 2.1 Move `control-line-icon.svg` to `projects/app-game-control-line/packaging/icons/` (`git mv`) and add `scripts/generate-control-line-icons.sh`, rendering `png` (512 and 1024), `ico` (16, 32, 48, 64, 128, 256) and `icns` with ImageMagick and `iconutil`, failing with a clear message when a tool is missing. Verify by running it: the three files exist, `file` reports the right types, and the 256 px `ico` entry and the `icns` open in Preview.
- [x] 2.2 Commit the generated icons beside the SVG and note the script in `projects/app-game-control-line/README.md`. Verify `scripts/check-docs.sh` passes.

## 3. Packaging tasks

- [x] 3.1 Add the `abyssus.control-line-packaging` convention plugin in `build-logic` (applied by the game's build) with `controlLineVersion` (default `1.0.0` in `gradle.properties`) and `stagePackageInput`, copying `installDist`'s jars and `project/ControlLine` into `build/packaging/input/`. Verify with `./gradlew :app-game-control-line:stagePackageInput`: the folder holds the jars and `project/ControlLine`, and no editor-only jar.
- [x] 3.2 Add `packaging/modules.txt` (from task 1.2) and `jlinkRuntime`, failing when `jdeps` reports a module missing from the list. Verify by running it: `build/packaging/runtime/bin/java --list-modules` matches the file, and removing one module from the file makes the task fail naming it.
- [x] 3.3 Add `packageApplication` (`jpackage --type app-image` with the launcher options of the design and the icon for the host OS). Verify by running it on this Mac and starting the result as in 1.1.
- [x] 3.4 Add `checkPackagePayload`: fails on an editor-only module, a ray source-set class, or a jar list differing from the Gradle distribution's `lib/`, and names the offender. Wire it into `packageApplication`. Verify with the clean payload (passes) and with a copy of `lib-core-editor`'s jar dropped into `build/packaging/input/` (fails naming it); remove the copy afterwards.
- [x] 3.5 Document `packageApplication`, `jlinkRuntime`, `checkPackagePayload` and `controlLineVersion` in `projects/app-game-control-line/README.md` and add the build commands to the `AGENTS.md` table. Verify `scripts/check-docs.sh` passes.

## 4. macOS dmg

- [x] 4.1 Add `packageDmg` (`jpackage --type dmg --app-image` with the macOS options of the design, the `icns` icon and the license file). Verify on this Mac: the `.dmg` mounts, the app copies to a temp folder, starts, and shows the Control Line icon; file name is `control-line-<version>-macos-arm64.dmg`.
- [x] 4.2 Verify the first-launch warning path for the unsigned app (right-click Open, or `xattr -dr com.apple.quarantine`), and write the exact steps in the README's unsigned notice. Verify the steps work on a copy downloaded through a browser or marked with `xattr -w com.apple.quarantine`.

## 5. Linux deb, rpm and AppImage

- [ ] 5.1 Add `packageDeb` and `packageRpm` (`jpackage --type deb|rpm --app-image` with package name `control-line`, menu group, shortcut, license, `png` icon). Verify in an `ubuntu-22.04` container or the CI job: `dpkg-deb -I` and `rpm -qpi` show the name and version, and a Linux install launches the entry.
- [ ] 5.2 Add `packaging/appimage/` templates (`AppRun`, `control-line.desktop`) and `assembleAppDir`. Verify the AppDir holds `AppRun` (executable), the `.desktop` file, `control-line.png`, `.DirIcon` and `ControlLine/bin/ControlLine`, and that `desktop-file-validate` accepts the `.desktop` file.
- [ ] 5.3 Add `appimageTool` (download of pinned `appimagetool` and runtime file, SHA-256 check from the repository, `--appimage-extract-and-run`) and `packageAppImage`. Verify in the Linux job: the `.AppImage` is produced, a wrong checksum fails the task, and `./Control*.AppImage --appimage-extract-and-run` starts the game.
- [ ] 5.4 Add the Linux startup smoke script (`scripts/smoke-control-line-linux.sh`): unpacks the `.deb` with `dpkg-deb -x`, runs the payload check on its jars (`checkExtractedPackage`), then starts the unpacked launcher and the `.AppImage` under `xvfb-run` with Mesa software GL for 20 seconds each and fails on early exit or an exception in the log. Verify in the Linux job with a good package (passes) and with one whose project folder is removed (fails).
- [ ] 5.5 Document the Linux packages in the README: install commands, the `.AppImage` run steps, the FUSE 2 note with `--appimage-extract-and-run`, and the glibc floor. Verify `scripts/check-docs.sh` passes.

## 6. Windows msi

- [ ] 6.1 Add `packageMsi` (`jpackage --type msi --app-image` with `--win-menu`, `--win-shortcut`, `--win-dir-chooser`, a fixed `--win-upgrade-uuid` committed to the repository, the `ico` icon and the license file). Verify in the Windows job: the `.msi` is produced and named `control-line-<version>-windows-x64.msi`, and installing a second, higher version upgrades in place.
- [ ] 6.2 Run the installed game on a real Windows machine and record the result against the checklist in section 8. Verify the Start menu entry shows the icon, a round can be flown, and scores are saved under the user profile; write the SmartScreen "More info / Run anyway" steps in the README's unsigned notice.

## 7. CI

- [ ] 7.1 Rework `.github/workflows/build-control-line.yml` into a `build` matrix (`ubuntu-22.04`, `windows-latest`, `macos-latest`, the Intel macOS runner), each job running its OS's `package*` tasks with `-PcontrolLineVersion=1.0.${{ github.run_number }}`, installing the pinned WiX on Windows, and uploading its packages; the Linux job also builds the universal zip. Verify with `workflow_dispatch` on the branch that all four jobs are green and the artifacts have the expected names, and read the Intel runner label and the WiX version off the log and pin them in the workflow.
- [ ] 7.2 Add the `publish` job (`needs: build`, `main` only) that downloads all artifacts and replaces the canary release with them, the universal zip and notes naming the commit. Verify on `main` that the canary holds `.deb`, `.rpm`, `.AppImage`, `.msi`, both `.dmg` files and the zip, and that failing one matrix entry on a throwaway branch leaves the previous canary untouched.
- [ ] 7.3 Confirm a pull request builds every package but creates and changes no release. Verify by opening a draft pull request and checking the `publish` job is skipped.

## 8. Final checks

- [ ] 8.1 Run the manual startup checklist on macOS arm64, macOS Intel, Windows and a Linux desktop (or the `.AppImage` in a VM): install, start from another working folder, main menu, fly a round, score survives a restart. Verify and record each result in the change's notes; any failure becomes a fix task before archive.
- [ ] 8.2 Run `./gradlew check` and `scripts/check-docs.sh` and `openspec validate add-control-line-native-packages --strict`. Verify all pass, then archive the change so its delta merges into `openspec/specs/game-distribution/`.
