# Design

## Context

Control Line is built into one universal zip by `distZip -Pabyssus.universalDist=true` on `ubuntu-latest` and JDK 21
(`.github/workflows/build-control-line.yml`). The zip needs a system Java and a start script. See `proposal.md` for why
this changes and `specs/game-distribution/spec.md` for the required behavior.

Facts that shape the approach:

- `Main.defaultProjectFolder()` looks for `project/ControlLine` two folders above the game jar, which matches the Gradle
  `installDist` layout only. It already honours the `controlLine.project` system property first.
- `jpackage` puts the application jars in a different place per OS (`lib/app` on Linux, `Contents/app` in a macOS
  app, `app` on Windows) and exposes that folder to launcher options as `$APPDIR`.
- `jpackage` cannot cross-build. `.deb`/`.rpm` need Linux (`dpkg-deb`, `fakeroot`, `rpmbuild`), `.msi` needs Windows with
  WiX, `.dmg` needs macOS. This machine is macOS arm64, so only the arm64 `.dmg` can be built and run here.
- `jpackage` cannot make an AppImage. It can make an `app-image` folder, which `appimagetool` wraps.
- The game's data that changes at run time (scores) lives under the player's home folder, so a read-only
  installation works.
- The trainer model bundled in `project/ControlLine` is imported from FlightGear and is GPL-2.0 (`importers/GPL-2.0.txt`).
- The change touches build and CI files only. No game code, no native Abyssus document (`.scene`, `.abss`, asset
  `meta.json`) and no module boundary of `AGENTS.md` changes; the shipped-classpath rules keep holding.

## Goals / Non-Goals

**Goals:**
- One staged payload per OS that every package of that OS is built from, so one check covers them all.
- Everything reproducible from the repository: pinned tools, committed icons, no hand steps.

**Non-Goals:**
- Signing, notarization, auto-update, a package repository (apt/yum), Linux or Windows arm64.
- Changing how the game finds its project when run from Gradle or the universal zip.
- A game-code smoke flag. Package startup is checked from outside.

## Decisions

- **`jpackage` driven by typed Gradle tasks in a `build-logic` convention plugin, not a third-party plugin.** The task classes
  (`JlinkRuntime`, `JPackage`, `CheckPackagePayload`) are plain Kotlin in `build-logic/src/main/kotlin/net/nevinsky/abyssus/buildlogic/packaging/`,
  registered by the precompiled script plugin `abyssus.control-line-packaging`, which the game's build applies after
  `application`; the game's `packaging/` folder holds only its data (modules list, icons, AppImage templates). Task classes
  cannot live in an applied `packaging.gradle.kts`: Kotlin scripts compile them as inner classes, which Gradle cannot
  instantiate, and an applied script does not see `build-logic`'s classes. Alternatives: the `org.beryx.runtime` plugin (another
  dependency whose `jpackage` coverage lags the JDK's) and shell scripts in the workflow (not runnable locally). The tasks call the
  `jpackage` of the JDK running Gradle; CI uses Temurin 21 like the other workflows, and a local JDK 25 works too.
- **One payload, three stages.** `stagePackageInput` copies `installDist`'s `lib/*.jar` and
  `project/ControlLine` into `build/packaging/input/` (jars at the top, the project under `project/ControlLine`).
  `packageApplication` runs `jpackage --type app-image` on it, with `--runtime-image` from the next decision. Each
  installer type then runs `jpackage --type <t> --app-image <that folder>`, so deb, rpm, msi, dmg and the AppImage all
  hold byte-identical payload. This is why one payload check is enough (see "Package check").
- **The bundled project is found through `$APPDIR`.** The launcher gets
  `--java-options -DcontrolLine.project=$APPDIR/project/ControlLine`, because `$APPDIR` is substituted by the launcher on
  every OS. `defaultProjectFolder()` stays as the fallback for Gradle runs. Alternative: teach `defaultProjectFolder()`
  each package layout, which puts platform code in the game and needs a game change.
- **The runtime is a `jlink` image made by us.** `jlinkRuntime` runs `jlink --add-modules <list> --strip-debug
  --no-header-files --no-man-pages --compress zip-6` into `build/packaging/runtime/` and passes it to `jpackage` as
  `--runtime-image`. The list is committed in `packaging/modules.txt`, produced once with `jdeps
  --print-module-deps --ignore-missing-deps --multi-release 21` on the shipped classpath and extended by hand for
  modules loaded reflectively (the spike finds them). Alternatives: letting `jpackage` run `jlink` itself (the list is
  then implicit and unreviewable) and a full JDK image (200 MB or more per package). A task fails when the committed list
  misses a module that `jdeps` now reports.
- **Version.** `controlLineVersion` in `gradle.properties` (default `1.0.0`). `.msi` needs numeric `x.y.z` and `.dmg`
  rejects a major version of 0, so the canary passes `-PcontrolLineVersion=1.0.<github.run_number>`, which also lets a newer
  canary upgrade an installed one. The SHA stays in the release title and notes, not in the package version. File names
  are `control-line-<version>-<os>-<arch>.<ext>`.
- **Per-type options.**
  - Linux: package name `control-line`, menu group `Game`, deb maintainer from the repository owner, rpm license type
    `Apache-2.0`, `--linux-shortcut`.
  - Windows: `--win-menu`, `--win-shortcut`, `--win-dir-chooser` and a fixed `--win-upgrade-uuid` committed to the
    repository, so reinstalling upgrades in place.
  - macOS: `--java-options -XstartOnFirstThread` for the macOS builds only, plus `--mac-package-identifier`
    `net.nevinsky.abyssus.controlline`.
  - All: `--license-file` with the repository's `LICENSE`; the GPL text for the trainer travels inside the bundled project
    folder (verified in the spike, otherwise added to the staged input).
- **AppImage assembly.** `assembleAppDir` copies the app-image folder to `AppDir/app/` and writes `AppRun`
  (`exec "$HERE/app/bin/Control Line" "$@"`), `control-line.desktop` and
  `control-line.png` (also `.DirIcon`) at the AppDir root, from templates in `packaging/appimage/`. `appimageTool` fetches
  `appimagetool` and its runtime file from a pinned release URL and verifies a SHA-256 kept in the repository, then runs
  `ARCH=x86_64 appimagetool --appimage-extract-and-run --runtime-file <pinned> AppDir out.AppImage`. Extract-and-run is
  needed because hosted runners have no FUSE. Alternative: building the AppImage by hand with `mksquashfs` and the
  runtime, which reimplements `appimagetool` for no gain.
- **Icons are rendered by a script, committed, and not part of CI.** `scripts/generate-control-line-icons.sh` renders
  `control-line-icon.svg` with ImageMagick to `png` (512 and 1024), `ico` (16 to 256) and, with `iconutil`, `icns`. It
  needs a Mac, like the other asset generators needing their own tools. The SVG moves to
  `projects/app-game-control-line/packaging/icons/` with the outputs. Alternative: a JVM task with an SVG library (Batik),
  a new dependency for a one-off render. The `generateIcons` wording in the proposal means this script.
- **Package check on the payload.** `checkPackagePayload` lists the jars in the app-image folder and fails when one is an
  editor-only module (`lib-core-editor`, `lib-game-link`, `lib-raytracing`, anything `plugin-*`) or the ray source
  set's output, and when the set differs from the Gradle distribution's `lib/`. It does not reject foreign-platform natives:
  the distribution's `lib/` holds the natives of every OS (see "Spike findings"). Because every installer is built `--app-image` from that folder, a clean payload means clean
  packages; that is how the spec's "every package type" is met without unpacking `.msi` and `.dmg` files. The check is
  wired into `package*` tasks, and the Linux job also unpacks the `.deb` once (`dpkg-deb -x`) and checks the unpacked jars with the
  same code, which proves the premise.
- **CI.** `build-control-line.yml` becomes two stages:
  - `build` matrix: `ubuntu-22.04` (deb, rpm, AppImage; the older glibc widens compatibility), `windows-latest` (msi),
    `macos-latest` (arm64 dmg) and an Intel macOS runner (x64 dmg). Each job runs the `package*` tasks of its OS, uploads its
    packages as an artifact, and the Linux job also builds the universal zip.
  - `publish`: `needs: build`, only for `main`, downloads every artifact and replaces the canary release. A failed platform
    job therefore leaves the previous canary in place, which the single-job workflow could not guarantee (it deleted the
    release before building was known to be complete). Pull requests run `build` only.
- **Startup smoke on Linux only.** After packaging, `scripts/smoke-control-line-linux.sh` unpacks the `.deb` (`dpkg-deb -x`, so no
  root and nothing installed), runs the payload check on the unpacked jars (`checkExtractedPackage -PpackageDir=...`, the same
  code as `checkPackagePayload`, which proves the premise that an installer holds the checked payload), then starts the unpacked
  launcher and the `.AppImage` each under `xvfb-run` with Mesa software GL for 20 seconds and fails if one exits early or logs an
  exception. Windows and macOS hosted runners have no usable GL 3.3 context, so their startup is a manual checklist (below) run
  on a real machine.
- **Native-access flag.** The launcher also gets `--java-options --enable-native-access=ALL-UNNAMED`, which silences the
  JDK 25 restricted-method warnings that libGDX's native loader and LWJGL trigger on every start.
- **Docs.** `projects/app-game-control-line/README.md` gets a Packages section (formats, how to build one per OS, the
  unsigned notice with how to open the app on macOS and Windows), `AGENTS.md` gets one command-table row, and
  `scripts/check-docs.sh` must pass.

## Spike findings

Run on this Mac (macOS arm64, JDK 25.0.4 Temurin) from `installDist` output, in a throwaway folder.

- **`$APPDIR` on macOS is `Contents/app`**, so `-DcontrolLine.project=$APPDIR/project/ControlLine` resolves to
  `Contents/app/project/ControlLine`; `jcmd VM.system_properties` shows the expanded path. The launcher's `.cfg` also adds
  `-Djpackage.app-version`.
- **`-XstartOnFirstThread` works through the `jpackage` launcher.** The process stayed up for 60 seconds, and `jcmd Thread.print`
  shows the `main` thread inside `Lwjgl3Application.loop`, so GLFW initialised and the render loop ran. The main menu and a
  flown round were not confirmed from here (no screen access); that check stays open in tasks 1.1 and 1.2. No
  `StartOnFirstThreadHelper` fallback is needed so far.
- **Module list.** `jdeps --print-module-deps --ignore-missing-deps --multi-release 21` gives `java.base,java.desktop,java.sql,
  jdk.unsupported`, which resolves to nine modules (`java.sql` brings `java.xml`, `java.logging`, `java.datatransfer`,
  `java.prefs`, `java.transaction.xa`). `javax.xml.stream` in `ColladaAsset` is covered through `java.xml`. The `jlink` image is
  50 MB and the app-image 130 MB, against 214 MB with the full JDK image. The sources use no `java.net.http`, TLS,
  management or zip-filesystem API.
- **The payload is large and carries every OS's natives.** The distribution's `lib/` (80 MB) holds LWJGL natives for Linux,
  Linux arm, Windows and macOS, and `jolt-jni-Linux64` beside the macOS Jolt jar. Packages keep the full set so that they match the
  Gradle distribution exactly; trimming foreign natives per package is a possible later size saving, not part of this change.
- **License text.** The GPL-2.0 text for the trainer ships inside the project (`assets/model_trainer/COPYING`), so no extra
  staging is needed.
- **Log noise.** JDK 25 prints restricted-method and `Unsafe` deprecation warnings at start; `--enable-native-access` removes the
  first kind (the `Unsafe` one comes from LWJGL and stays).

## Risks / Trade-offs

- [macOS launcher may not honour `-XstartOnFirstThread`] → the spike shows the render loop running; if a real round or another
  Mac fails, the fallback is libGDX's `StartOnFirstThreadHelper`, a small game change that would add a task.
- [jlink image misses a reflectively loaded module and crashes at run time] → the spike runs a full round from the main
  menu; the module list is reviewed and committed; the fallback is the full JDK image, trading size for safety.
- [The AppImage needs FUSE 2 on the player's machine] → the README documents `--appimage-extract-and-run`, which needs
  no FUSE.
- [An AppImage built on 22.04 needs its glibc or newer] → state the floor in the README.
- [WiX is missing or a different major version on the Windows runner] → the Windows job installs a pinned WiX before packaging
  and fails with its version if it cannot.
- [An unsigned package triggers Gatekeeper and SmartScreen] → accepted and documented; no signing in this change.
- [The Intel macOS runner label changes or the runner is retired] → one matrix entry to update; a note in the workflow.
- [Canary versions grow without bound] → the run number only increases; only the latest canary is kept.

## Migration Plan

1. Merge the packaging tasks and the workflow together; the universal zip and its canary asset stay, so existing
   links keep working.
2. The first `main` run publishes the new set to the canary. If a platform fails, the canary stays as before.
3. Rollback is reverting the workflow; nothing in the game or in any project file changes.

## Open Questions

- The exact label of the Intel macOS runner and the WiX version preinstalled on `windows-latest` are read from the first
  CI run and pinned then; neither changes the specs or the tasks.
