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
    implementation(libs.gdx.backend.lwjgl3)
    runtimeOnly(libs.gdx.platform) {
        artifact {
            classifier = "natives-desktop"
        }
    }
    runtimeOnly("com.github.stephengold:jolt-jni-$joltJniBuildPlatform:${libs.versions.jolt.get()}:ReleaseSp")
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":lib-core")))
    testImplementation(testFixtures(project(":lib-gdx")))
}

val gameProject = layout.projectDirectory.dir("project/ControlLine")

application {
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.MainKt")
    // GLFW needs the process's first thread on macOS
    if (System.getProperty("os.name").lowercase().contains("mac")) applicationDefaultJvmArgs =
        listOf("-XstartOnFirstThread")
}

tasks.named<JavaExec>("run") {
    val dir = gameProject.asFile.absolutePath
    systemProperty("controlLine.project", dir)
}

tasks.test {
    systemProperty("controlLine.project", gameProject.asFile.absolutePath)
    // GL tests open a window: opt in with -Dabyssus.glTests=true
    System.getProperty("abyssus.glTests")?.let { systemProperty("abyssus.glTests", it) }
    // re-records the pinned screens (screens.golden) from the screens under test: only when a screen is meant to change
    System.getProperty("abyssus.recordScreens")?.let { systemProperty("abyssus.recordScreens", it) }
}

// Generators of the bundled project's assets (tools/): their output is committed, so the game needs none of them.
val tools by sourceSets.creating {
    kotlin.srcDir("tools")
    // FieldFoliage.kt has its own source set (it needs the editing library): the older tools compile without it
    kotlin.exclude("FieldFoliage.kt")
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
            excludedSourceSets.addAll("tools", "importers", "fieldFoliageTools", "fieldFoliageToolsTest")
        }
    }
}
