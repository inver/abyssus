/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// Everything a game links only so that Abyssus can talk to it: the play host, the play protocol, `PlayModule` and the
// play export entry point. A plain JVM library (no IntelliJ imports) wired by constructors. A shipped game never
// depends on it from its main code; it sits on the game's separate `play` source set.
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    // LWJGL, libGDX and JUnit versions from the catalog (build-logic)
    id("abyssus.dependency-platforms")
}

val joltVersion = libs.versions.jolt.get()
val joltPlatforms = listOf("Linux64", "Windows64", "MacOSX64", "MacOSX_ARM64")

/** The jolt-jni platform of the machine running the build (from `abyssus.dependency-platforms`). */
val joltJniBuildPlatform: String by extra

/** The four platforms' release natives, bundled in the play host (see `:plugin-abyssus`). */
val playHost by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = true
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":lib-physics"))
    api(project(":lib-core"))
    joltPlatforms.forEach { playHost("com.github.stephengold:jolt-jni-$it:$joltVersion:ReleaseSp") }
    testRuntimeOnly("com.github.stephengold:jolt-jni-$joltJniBuildPlatform:$joltVersion:DebugSp")
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":lib-core")))
    testImplementation(testFixtures(project(":lib-physics")))
}

extra["abyssusSingletonExcludes"] = listOf<String>()
