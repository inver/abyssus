/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// Asset reading and loading for native projects: asset folders and meta.json, the prepare/upload/build pipeline, and
// the models, terrains and skies it builds. A plain JVM library (no IntelliJ imports) wired by constructors.
plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    // LWJGL, libGDX and JUnit versions from the catalog (build-logic)
    id("abyssus.dependency-platforms")
}

dependencies {
    // the root sets kotlin.stdlib.default.dependency=false for the IDE plugin; a standalone library needs it
    implementation(kotlin("stdlib"))
    api(project(":lib-gdx"))
    api(libs.ashley)
    implementation(libs.lwjgl.tinyexr)

    api(libs.slf4j.api)
    api(libs.jackson.databind)
    api(libs.jackson.module.kotlin)

    testImplementation(libs.junit4)
    // GL tests use gdx-model's TestGl context; HdrFixtures (Radiance files from a pixel function) is shared with the
    // plugin's tests as this module's test fixture
    testImplementation(testFixtures(project(":lib-gdx")))
    testFixturesImplementation(kotlin("stdlib"))
}

tasks.test {
    // GL tests open a window: opt in with -Dabyssus.glTests=true
    System.getProperty("abyssus.glTests")?.let { systemProperty("abyssus.glTests", it) }
    // the native fixture projects are shared with the plugin's tests
    systemProperty("abyssus.testData", rootProject.file("projects/plugin-abyssus/src/test/testData").absolutePath)
}

extra["abyssusSingletonExcludes"] = listOf(
    "net/nevinsky/abyssus/lib/core/io/AbyssusProjectLayout.kt",
    "net/nevinsky/abyssus/lib/core/util/GeometryUtils.kt"
)
