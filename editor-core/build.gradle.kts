/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// The editing engine without the IDE: scene documents, component and asset-meta edits, placements, picking, terrain
// generation and the ray tracing bridge. A plain JVM library (no IntelliJ, Swing or AWT) wired by constructors; the
// plugin bundles it and keeps only IDE glue. See editor-core/README.md.
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

repositories { mavenCentral() }

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":core"))
    api(project(":runtime"))
    api(project(":raytracing"))
    api(project(":gdx-model"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":gdx-model")))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}
tasks.test {
    // fixture paths in the tests are relative to the repository root, as in the plugin's tests
    workingDir = rootProject.projectDir
    systemProperty("abyssus.editorCoreSources", file("src/main/kotlin").absolutePath)
    // GL tests open a window: opt in with -Dabyssus.glTests=true
    System.getProperty("abyssus.glTests")?.let { systemProperty("abyssus.glTests", it) }
    // the native fixture projects are shared with the plugin's tests
    systemProperty("abyssus.testData", rootProject.file("src/test/testData").absolutePath)
}

// Pure constant holders only (design D11); behavior is injected.
extra["abyssusSingletonExcludes"] = listOf<String>("net/nevinsky/abyssus/editor/content/Placements.kt")
apply(from = rootProject.file("gradle/checks.gradle.kts"))
