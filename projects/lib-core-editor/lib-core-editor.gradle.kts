/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
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
    api(project(":lib-raytracing"))
    api(project(":lib-gdx-model"))
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":lib-core")))
    testImplementation(testFixtures(project(":lib-gdx-model")))
    // scene and asset helpers for tests (parseScene, testProject, ...), shared with the plugin's tests
    testFixturesImplementation(kotlin("stdlib"))
}

tasks.test {
    // fixture paths in the tests (src/test/testData/...) are relative to the plugin, as in the plugin's tests
    workingDir = rootProject.file("projects/plugin-abyssus")
    systemProperty("abyssus.editorCoreSources", file("src/main/kotlin").absolutePath)
    // GL tests open a window: opt in with -Dabyssus.glTests=true
    System.getProperty("abyssus.glTests")?.let { systemProperty("abyssus.glTests", it) }
    // the native fixture projects are shared with the plugin's tests
    systemProperty("abyssus.testData", rootProject.file("projects/plugin-abyssus/src/test/testData").absolutePath)
    // MakeImportFixtures rewrites the binary model import fixtures here: opt in with -Dabyssus.makeFixtures=true
    systemProperty("abyssus.importFixtures", file("src/test/resources/modelimport").absolutePath)
    System.getProperty("abyssus.makeFixtures")?.let { systemProperty("abyssus.makeFixtures", it) }
}

// Pure constant holders only (design D11); behavior is injected.
extra["abyssusSingletonExcludes"] = listOf(
    "net/nevinsky/abyssus/lib/core/editor/content/Placements.kt",
    "net/nevinsky/abyssus/lib/core/editor/scene/SceneRenderParams.kt",
)
