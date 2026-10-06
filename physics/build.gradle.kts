/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// Physics for native scenes through Jolt (jolt-jni): physics components, the physics world, the play host and its
// protocol. A plain JVM library (no IntelliJ imports) wired by constructors. Jolt natives load only in a game or the
// play host, never in the IDE.
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

repositories { mavenCentral() }

val joltVersion = providers.gradleProperty("joltJniVersion").get()
val joltPlatforms = listOf("Linux64", "Windows64", "MacOSX64", "MacOSX_ARM64")

/** The jolt-jni platform of the machine running the build, for the tests' `DebugSp` natives. */
val buildPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
    when {
        os.contains("mac") -> if (arm) "MacOSX_ARM64" else "MacOSX64"
        os.contains("win") -> "Windows64"
        else -> "Linux64"
    }
}

/** The four platforms' release natives, bundled in the play host (see `:physics-plugin`). */
val playHost by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = true
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":runtime"))
    // the play host's and games' SLF4J binding: messages go to stderr. Abyssus Physics bundles physics.jar without its
    // dependencies, so this never reaches the IDE
    runtimeOnly("org.slf4j:slf4j-simple:2.0.6")
    // The Java API is the same in every platform's plain jar; natives are classifier jars
    api("com.github.stephengold:jolt-jni-Linux64:$joltVersion")
    testRuntimeOnly("com.github.stephengold:jolt-jni-$buildPlatform:$joltVersion:DebugSp")
    joltPlatforms.forEach { playHost("com.github.stephengold:jolt-jni-$it:$joltVersion:ReleaseSp") }
    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":core")))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}
tasks.test {
    systemProperty("abyssus.testData", rootProject.file("src/test/testData").absolutePath)
}

extra["abyssusSingletonExcludes"] = listOf<String>()
apply(from = rootProject.file("gradle/checks.gradle.kts"))
