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

// Asset reading and loading for Mundus projects: asset folders and meta.json, the prepare/upload/build pipeline, and
// the models, terrains and skies it builds. A plain JVM library (no IntelliJ imports) wired by constructors.
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
val jacksonVersion = providers.gradleProperty("jacksonVersion").get()
val lwjglNatives = listOf("natives-macos-arm64", "natives-macos", "natives-windows", "natives-linux")

dependencies {
    // the root sets kotlin.stdlib.default.dependency=false for the IDE plugin; a standalone library needs it
    implementation(kotlin("stdlib"))
    api(project(":gdx-model"))
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    testImplementation("junit:junit:4.13.2")
    // GL tests use gdx-model's TestGl context; HdrFixtures (Radiance files from a pixel function) is shared with the
    // plugin's tests as this module's test fixture
    testImplementation(testFixtures(project(":gdx-model")))
    testFixturesImplementation(kotlin("stdlib"))
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
    // the Mundus fixture projects are shared with the plugin's tests
    systemProperty("abyssus.testData", rootProject.file("src/test/testData").absolutePath)
}

// Code in this module is wired by constructors: no `object` declarations and no `companion object` (a `data object`
// case of a sealed type is a value and is allowed; object expressions such as `object : Runnable` are fine).
val checkNoSingletons by tasks.registering {
    val sources = fileTree("src/main/kotlin") { include("**/*.kt") }
    val root = layout.projectDirectory.asFile
    inputs.files(sources)
    doLast {
        val declaration = Regex("""^\s*(?:(?:private|internal|public|protected)\s+)*(companion\s+object\b|object\s+[A-Za-z_])""")
        val found = sources.files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if (declaration.containsMatchIn(line)) "${file.relativeTo(root)}:${i + 1}: ${line.trim()}" else null
            }
        }
        if (found.isNotEmpty()) {
            throw GradleException("Singletons are not allowed in :core; inject an instance instead:\n" + found.joinToString("\n"))
        }
    }
}

tasks.check {
    dependsOn(checkNoSingletons)
}
