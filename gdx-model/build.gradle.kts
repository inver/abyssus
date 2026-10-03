/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// libGDX 3D model runtime with 32-bit indices and an Assimp importer (trimmed Mundus fork, see README.md).
// Plain JVM library: no IntelliJ dependency, so other libGDX projects can use it.
plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
}

val lwjglVersion = providers.gradleProperty("lwjglVersion").get()
val gdxVersion = providers.gradleProperty("gdxVersion").get()
val lwjglNatives = listOf("natives-macos-arm64", "natives-macos", "natives-windows", "natives-linux")

dependencies {
    // the root sets kotlin.stdlib.default.dependency=false for the IDE plugin; a standalone library needs it
    implementation(kotlin("stdlib"))
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("org.lwjgl:lwjgl-assimp:$lwjglVersion")
    implementation("org.slf4j:slf4j-api:2.0.6")
    lwjglNatives.forEach {
        runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$it")
        runtimeOnly("org.lwjgl:lwjgl-assimp:$lwjglVersion:$it")
    }

    testImplementation("junit:junit:4.13.2")
    // GL tests (TestGl, shared with :core's tests as a test fixture): an AWT GL canvas (works from the AWT thread on
    // every OS) and libGDX's LWJGL3 GL wrappers
    testFixturesImplementation(kotlin("stdlib"))
    testFixturesApi("org.lwjglx:lwjgl3-awt:0.2.5")
    testFixturesApi("org.lwjgl:lwjgl-opengl:$lwjglVersion")
    testFixturesApi("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion") { isTransitive = false }
    lwjglNatives.forEach { testFixturesRuntimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$it") }
    testFixturesRuntimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
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
