/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

repositories { mavenCentral() }

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":core"))
    api("com.badlogicgames.ashley:ashley:1.7.4")
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

extra["abyssusSingletonExcludes"] = listOf<String>("net/nevinsky/abyssus/runtime/ecs/EcsUtils.kt")
apply(from = rootProject.file("gradle/checks.gradle.kts"))
