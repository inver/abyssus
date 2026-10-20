/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// Control Line: a libGDX desktop game (LWJGL3) on `runtime` and `physics`, with its own native Abyssus project in
// `project/ControlLine`. Its flight, scoring and screen flow run without a window in tests; Jolt's natives are the build
// machine's.
plugins {
    application
    id("org.jetbrains.kotlin.jvm")
    // LWJGL, libGDX and JUnit versions from the catalog (build-logic)
    id("abyssus.dependency-platforms")
}

/** Builds one distribution for every OS: Jolt natives of all platforms, `-XstartOnFirstThread` chosen at launch. */
val universalDist: Boolean = providers.gradleProperty("abyssus.universalDist").orNull == "true"

/** The jolt-jni platform of the machine running the build. */
val joltJniBuildPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
    when {
        os.contains("mac") -> if (arm) "MacOSX_ARM64" else "MacOSX64"
        os.contains("win") -> "Windows64"
        else -> "Linux64"
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":lib-physics"))
    implementation(project(":lib-render"))
    implementation(libs.gdx.backend.lwjgl3)
    runtimeOnly(libs.gdx.platform) {
        artifact {
            classifier = "natives-desktop"
        }
    }
    runtimeOnly("com.github.stephengold:jolt-jni-$joltJniBuildPlatform:${libs.versions.jolt.get()}:ReleaseSp")
    // the universal distribution (-Pabyssus.universalDist=true) carries Jolt's natives of every desktop platform
    if (universalDist) {
        listOf("Linux64", "Windows64", "MacOSX64", "MacOSX_ARM64").filter { it != joltJniBuildPlatform }.forEach {
            runtimeOnly("com.github.stephengold:jolt-jni-$it:${libs.versions.jolt.get()}:ReleaseSp")
        }
    }
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":lib-core")))
    testImplementation(testFixtures(project(":lib-gdx")))
}

// Play in Abyssus (src/play): the game's PlayModule, on the play bridge. A source set of its own, so the game's main
// classpath and its distribution never see `lib-game-link`; `exportPlay` writes a play.json whose classpath includes both.
val play by sourceSets.creating {
    compileClasspath += sourceSets["main"].output + sourceSets["main"].runtimeClasspath
    runtimeClasspath += output + compileClasspath
}
dependencies {
    "playImplementation"(kotlin("stdlib"))
    "playImplementation"(project(":lib-game-link"))
    // ControlLinePlayTest runs the play host against the play module
    testImplementation(play.output)
    testImplementation(project(":lib-game-link"))
}

// An opt-in ray-traced field view, mirroring `play`: a source set of its own so the game's main runtime classpath
// and its distribution never see `lib-raytracing` or `lib-core-editor`. `checkShippedClasspath` continues to visit
// only the shipped classpath, and `installDist`/`exportPlay` never include the ray set.
val ray by sourceSets.creating {
    compileClasspath += sourceSets["main"].output + sourceSets["main"].runtimeClasspath
    runtimeClasspath += output + compileClasspath
}
dependencies {
    "rayImplementation"(kotlin("stdlib"))
    "rayImplementation"(project(":lib-raytracing"))
    "rayImplementation"(project(":lib-core-editor"))
    "rayImplementation"(libs.gdx)
    "rayImplementation"(libs.gdx.platform) {
        artifact {
            classifier = "natives-desktop"
        }
    }
    // rayImplementation is a development-only configuration: it must never reach checkShippedClasspath
}

tasks.register<JavaExec>("runRay") {
    group = "application"
    description = "Runs Control Line with the opt-in ray-traced field view (controlLine.fieldView=ray)."
    classpath = ray.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.MainKt")
    val dir = gameProject.asFile.absolutePath
    systemProperty("controlLine.project", dir)
    systemProperty("controlLine.fieldView", "ray")
    // GLFW needs the process's first thread on macOS
    if (System.getProperty("os.name").lowercase().contains("mac")) jvmArgs = listOf("-XstartOnFirstThread")
}

val gameProject = layout.projectDirectory.dir("project/ControlLine")

// Tests of the ray set (src/rayTest), headless: the scene builder and the failure reporting of the ray view. Kept out of
// the game's test source set so its classpath never sees the ray modules.
val rayTest by sourceSets.creating {
    compileClasspath += ray.output + ray.runtimeClasspath
    runtimeClasspath += output + compileClasspath
}
dependencies {
    "rayTestImplementation"(libs.junit4)
    "rayTestImplementation"(testFixtures(project(":lib-core-editor")))
}
tasks.register<Test>("testRay") {
    group = "verification"
    description = "Runs the headless tests of the ray-traced field view."
    testClassesDirs = rayTest.output.classesDirs
    classpath = rayTest.runtimeClasspath
}
tasks.named("check") { dependsOn("testRay") }

// A shipped game carries no editor code: nothing on the main runtime classpath may reach an editor-only module.
val checkShippedClasspath by tasks.registering {
    group = "verification"
    description = "Fails when the game's main runtime classpath holds an editor-only module."
    val root = configurations.runtimeClasspath.flatMap { it.incoming.resolutionResult.rootComponent }
    doLast {
        val editorOnly = setOf(":lib-core-editor", ":lib-game-link", ":lib-raytracing")
        val found = sortedSetOf<String>()
        val seen = mutableSetOf<ResolvedComponentResult>()
        fun visit(component: ResolvedComponentResult) {
            if (!seen.add(component)) return
            val id = component.id
            if (id is ProjectComponentIdentifier && (id.projectPath in editorOnly || id.projectPath.startsWith(":plugin-"))) {
                found += id.projectPath
            }
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { visit(it.selected) }
        }
        visit(root.get())
        if (found.isNotEmpty()) {
            throw GradleException(
                "The shipped game's runtime classpath must not hold editor code, but it reaches: " + found.joinToString()
            )
        }
    }
}
tasks.named("check") { dependsOn(checkShippedClasspath) }

application {
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.MainKt")
    // GLFW needs the process's first thread on macOS; the universal distribution's start script adds it on Darwin only
    if (!universalDist && System.getProperty("os.name").lowercase().contains("mac")) applicationDefaultJvmArgs =
        listOf("-XstartOnFirstThread")
}

if (universalDist) {
    tasks.named<CreateStartScripts>("startScripts") {
        doLast {
            unixScript.writeText(
                unixScript.readText().replace(
                    "\nDEFAULT_JVM_OPTS=\"\"\n",
                    "\nDEFAULT_JVM_OPTS=\"\"\n\$darwin && DEFAULT_JVM_OPTS='\"-XstartOnFirstThread\"'\n"
                )
            )
        }
    }
}

// the distribution carries the game's project, which the game finds beside its lib folder
distributions.main {
    contents {
        from(gameProject) { into("project/ControlLine") }
    }
}

tasks.named<JavaExec>("run") {
    val dir = gameProject.asFile.absolutePath
    systemProperty("controlLine.project", dir)
}

val playProject = providers.gradleProperty("playProject")
tasks.register<JavaExec>("exportPlay") {
    group = "control line"
    description = "Writes abyssus/play.json (the game's play module and its classpath) into the project given as " +
        "-PplayProject=<folder>; use a copy of project/ControlLine, as the file holds machine-local paths."
    classpath = play.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.lib.gamelink.play.PlayExportMain")
    val folder = playProject
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "net.nevinsky.abyssus.app.game.controlline.play.ControlLinePlay",
            folder.orNull ?: throw GradleException("Pass the project folder: -PplayProject=<folder> (a copy first)"),
        )
    })
}

tasks.test {
    systemProperty("controlLine.project", gameProject.asFile.absolutePath)
    // the tests read the bundled project (screens, scenes) from disk: a change to it reruns them
    inputs.dir(gameProject).withPropertyName("controlLineProject")
    // GL tests open a window: opt in with -Dabyssus.glTests=true
    System.getProperty("abyssus.glTests")?.let { systemProperty("abyssus.glTests", it) }
    // the opt-in graphics workload runner (docs/ai/testing.md, "Graphics performance evidence")
    System.getProperties().stringPropertyNames().filter { it.startsWith("abyssus.perf.") }
        .forEach { systemProperty(it, System.getProperty(it)) }
    // re-records the pinned screens (screens.golden) from the screens under test: only when a screen is meant to change
    System.getProperty("abyssus.recordScreens")?.let { systemProperty("abyssus.recordScreens", it) }
}

// Generators of the bundled project's assets (tools/): their output is committed, so the game needs none of them.
val tools by sourceSets.creating {
    kotlin.srcDir("tools")
    // FieldFoliage.kt has its own source set (it needs the editing library): the older tools compile without it
    kotlin.exclude("FieldFoliage.kt", "UiFonts.kt", "UiArt.kt")
    compileClasspath += sourceSets["main"].runtimeClasspath
    runtimeClasspath += sourceSets["main"].runtimeClasspath
}
dependencies {
    "toolsImplementation"(kotlin("stdlib"))
}
tasks.register<JavaExec>("generatePlaneModels") {
    group = "control line"
    description = "Writes the planes' and the pilot's glTF models into the bundled project."
    classpath = tools.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.tools.PlaneModels")
    args(gameProject.asFile.absolutePath)
}
// The screens' fonts and art (tools/UiFonts.kt, tools/UiArt.kt): a source set of their own, plain JDK drawing
val uiTools by sourceSets.creating {
    kotlin.srcDir("tools")
    kotlin.include("UiFonts.kt", "UiArt.kt")
    compileClasspath += sourceSets["main"].runtimeClasspath
    runtimeClasspath += sourceSets["main"].runtimeClasspath
}
dependencies {
    "uiToolsImplementation"(kotlin("stdlib"))
}
tasks.register<JavaExec>("generateUiFonts") {
    group = "control line"
    description = "Writes the screens' bitmap fonts from the OFL fonts in tools/fonts into the bundled project."
    classpath = uiTools.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.tools.UiFonts")
    args(gameProject.asFile.absolutePath, layout.projectDirectory.dir("tools/fonts").asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true")
}
tasks.register<JavaExec>("generateUiArt") {
    group = "control line"
    description = "Draws the screens' illustrations (menu track, plane top view) into the bundled project."
    classpath = uiTools.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.tools.UiArt")
    args(gameProject.asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true")
}
// Importers of bundled assets from outside sources (importers/): kept apart from tools/ so each builds on its own.
val importers by sourceSets.creating {
    kotlin.srcDir("importers")
    compileClasspath += sourceSets["main"].runtimeClasspath
    runtimeClasspath += sourceSets["main"].runtimeClasspath
}
dependencies {
    "importersImplementation"(kotlin("stdlib"))
    // the FlightGear import lives in the editing library
    "importersImplementation"(project(":lib-core-editor"))
}
tasks.register<JavaExec>("importTrainer") {
    group = "control line"
    description = "Re-imports the trainer from FlightGear's Cessna 172R archive (downloaded into build/flightgear)."
    classpath = importers.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.tools.TrainerModel")
    args(
        gameProject.asFile.absolutePath, layout.buildDirectory.dir("flightgear").get().asFile.absolutePath,
        file("importers/GPL-2.0.txt").absolutePath
    )
}
tasks.register<JavaExec>("animateTrainer") {
    group = "control line"
    description = "Adds the crash clips to a freshly imported trainer model (importTrainer already does)."
    classpath = importers.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.tools.TrainerCrashAnimations")
    args(gameProject.asFile.absolutePath)
}
tasks.register<JavaExec>("generateField") {
    group = "control line"
    description = "Writes the field's terrain and textures into the bundled project."
    classpath = tools.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.tools.FieldAssets")
    args(gameProject.asFile.absolutePath)
}

// The field foliage migration (tools/FieldFoliage.kt): a source set of its own that compiles just that file, with the
// editing library on its tool classpath only. The game's runtime classpath never sees lib-core-editor or the tool.
val fieldFoliageTools by sourceSets.creating {
    kotlin.srcDir("tools")
    kotlin.include("FieldFoliage.kt")
    compileClasspath += sourceSets["main"].runtimeClasspath
    runtimeClasspath += sourceSets["main"].runtimeClasspath
}
dependencies {
    "fieldFoliageToolsImplementation"(kotlin("stdlib"))
    "fieldFoliageToolsImplementation"(project(":lib-core-editor"))
}
val fieldFoliageProject = providers.gradleProperty("fieldFoliageProject")
val fieldFoliageManifest = gameProject.dir("../environment").file("foliage-manifest.json").asFile.absolutePath

tasks.register<JavaExec>("generateFieldFoliage") {
    group = "control line"
    description = "Migrates the field's trees and bushes to foliage assets and adds grass, editing the project given as " +
        "-PfieldFoliageProject=<folder> in place from environment/foliage-manifest.json."
    classpath = fieldFoliageTools.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.tools.FieldFoliage")
    val folder = fieldFoliageProject
    val manifest = fieldFoliageManifest
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("generate", folder.orNull ?: throw GradleException("Pass the project folder: -PfieldFoliageProject=<folder> (a copy first)"), manifest)
    })
}
tasks.register<JavaExec>("recordFieldFoliage") {
    group = "control line"
    description = "Records environment/foliage-manifest.json from the trees and bushes of the original scene of " +
        "-PfieldFoliageProject=<folder>; refuses to replace a manifest that records something else."
    classpath = fieldFoliageTools.runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.tools.FieldFoliage")
    val folder = fieldFoliageProject
    val manifest = fieldFoliageManifest
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("record", folder.orNull ?: throw GradleException("Pass the project folder: -PfieldFoliageProject=<folder> (a copy first)"), manifest)
    })
}
// Tests of the migration, kept out of the game's test source set so the game's classpath stays free of the editing library
val fieldFoliageToolsTest by sourceSets.creating {
    kotlin.srcDir("toolsTest")
    compileClasspath += fieldFoliageTools.output + fieldFoliageTools.runtimeClasspath
    runtimeClasspath += output + compileClasspath
}
dependencies {
    "fieldFoliageToolsTestImplementation"(libs.junit4)
    "fieldFoliageToolsTestImplementation"(testFixtures(project(":lib-gdx")))
}
tasks.register<Test>("testFieldFoliageTools") {
    group = "verification"
    description = "Runs the field foliage migration tests."
    testClassesDirs = fieldFoliageToolsTest.output.classesDirs
    classpath = fieldFoliageToolsTest.runtimeClasspath
    systemProperty("controlLine.project", gameProject.asFile.absolutePath)
}
tasks.named("check") { dependsOn("testFieldFoliageTools") }

// tools/ and importers/ are asset generators run by hand, not game code: keep them out of coverage, so `check`
// does not compile them
kover {
    currentProject {
        sources {
            excludedSourceSets.addAll("tools", "importers", "fieldFoliageTools", "fieldFoliageToolsTest", "uiTools")
        }
    }
}
