/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    // LWJGL, libGDX and JUnit versions from the catalog (build-logic)
    id("abyssus.dependency-platforms")
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":lib-core"))
    api(libs.ashley)
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":lib-core")))
}

tasks.test {
    systemProperty("abyssus.testData", rootProject.file("projects/plugin-abyssus/src/test/testData").absolutePath)
}

extra["abyssusSingletonExcludes"] = listOf(
    "net/nevinsky/abyssus/lib/runtime/ecs/EcsUtils.kt"
)
