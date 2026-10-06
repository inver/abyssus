/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// libGDX 3D model runtime with 32-bit indices and an Assimp importer (an inherited fork; see README.md and docs/third-party/gdx-model-origin.md).
// Plain JVM library: no IntelliJ dependency, so other libGDX projects can use it.
plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    // LWJGL, libGDX and JUnit versions from the catalog (build-logic)
    id("abyssus.dependency-platforms")
}

repositories {
    mavenCentral()
}

val lwjglNatives = listOf("natives-macos-arm64", "natives-macos", "natives-windows", "natives-linux")

dependencies {
    // the root sets kotlin.stdlib.default.dependency=false for the IDE plugin; a standalone library needs it
    implementation(kotlin("stdlib"))
    api(libs.slf4j.api)
    api(libs.gdx)
    api(libs.lwjgl.assimp)
    lwjglNatives.forEach { classifier ->
        runtimeOnly(libs.lwjgl) {
            artifact {
                this.classifier = classifier
            }
        }
        runtimeOnly(libs.lwjgl.assimp) {
            artifact {
                this.classifier = classifier
            }
        }
    }

    testImplementation(libs.junit4)
    // GL tests (TestGl, shared with :core's tests as a test fixture): an AWT GL canvas (works from the AWT thread on
    // every OS) and libGDX's LWJGL3 GL wrappers
    testFixturesImplementation(kotlin("stdlib"))
    testFixturesApi(libs.lwjgl.awt)
    testFixturesApi(libs.lwjgl.opengl)
    testFixturesApi(libs.gdx.backend.lwjgl3) { isTransitive = false }
    lwjglNatives.forEach { classifier ->
        testFixturesRuntimeOnly(libs.lwjgl.opengl) {
            artifact {
                this.classifier = classifier
            }
        }
    }
    testFixturesRuntimeOnly(libs.gdx.platform) {
        artifact {
            this.classifier = "natives-desktop"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.test {
    // GL tests open a window: opt in with -Dabyssus.glTests=true
    System.getProperty("abyssus.glTests")?.let { systemProperty("abyssus.glTests", it) }
}
