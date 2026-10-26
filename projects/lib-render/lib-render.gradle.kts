/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// Shared rendering mechanics of the editor and games: the terrain program's binding and drawing, the shadow depth
// submission and the pure light, projection and binding-plan calculations behind them. A plain JVM library wired by
// constructors; the hosts (the IntelliJ plugin, Control Line) own their scenes, passes and resources and adapt their own
// types at the boundary. A shipped game links it.
plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    // LWJGL, libGDX and JUnit versions from the catalog (build-logic)
    id("abyssus.dependency-platforms")
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":lib-core"))
    api(project(":lib-gdx"))
    implementation(libs.lwjgl)
    implementation(libs.lwjgl.opengl)
    testImplementation(libs.junit4)
    // TestGl: the GL context of the opt-in GL tests
    testImplementation(testFixtures(project(":lib-gdx")))
    testImplementation(testFixtures(project(":lib-core")))
}

tasks.test {
    // the boundary test reads the module's own sources
    systemProperty("abyssus.renderSources", file("src/main/kotlin").absolutePath)
    // GL tests open a window: opt in with -Dabyssus.glTests=true
    System.getProperty("abyssus.glTests")?.let { systemProperty("abyssus.glTests", it) }
}

extra["abyssusSingletonExcludes"] = listOf<String>()

// Games ship this library: `checkNoEditorCode` keeps editor imports and ray-tracing snapshot types out of it.
extra["abyssusShippedLibrary"] = true
