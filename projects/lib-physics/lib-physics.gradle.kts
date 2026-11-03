/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// Physics for native scenes through Jolt (jolt-jni): physics components and the physics world. A plain JVM library (no
// IntelliJ imports) wired by constructors. Jolt natives load only in a game or the play host (`lib-game-link`), never in
// the IDE.
plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    // LWJGL, libGDX and JUnit versions from the catalog (build-logic)
    id("abyssus.dependency-platforms")
}

val joltVersion = libs.versions.jolt.get()

/** The jolt-jni platform of the machine running the build (from `abyssus.dependency-platforms`). */
val joltJniBuildPlatform: String by extra

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":lib-core"))
    // the play host's and games' SLF4J binding: messages go to stderr. Abyssus bundles lib-physics without its
    // dependencies, so this never reaches the IDE
    runtimeOnly(libs.slf4j.simple)
    // The Java API is the same in every platform's plain jar; natives are classifier jars
    api("com.github.stephengold:jolt-jni-Linux64:$joltVersion")
    testRuntimeOnly("com.github.stephengold:jolt-jni-$joltJniBuildPlatform:$joltVersion:DebugSp")
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":lib-core")))
    testFixturesImplementation(kotlin("stdlib"))
    // FoliageProject writes a native project with the loaders of lib-core
    testFixturesApi(project(":lib-core"))
}

extra["abyssusSingletonExcludes"] = listOf<String>()

// Games ship this library: `checkNoEditorCode` keeps editor imports and ray-tracing snapshot types out of it.
extra["abyssusShippedLibrary"] = true

// No Jackson tree types (JsonNode, ObjectNode, ...) here: reading JSON lives in lib-core and writing it in lib-core-editor (see gradle/checks.gradle.kts).
extra["abyssusJacksonTreeAllowed"] = emptyList<String>()
