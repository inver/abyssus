import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    base
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
    // IntelliJ Platform Gradle Plugin
    id("org.jetbrains.intellij.platform") version "2.19.0" apply false
    // Generates the GLTF lexer and parser from Gltf.flex / Gltf.bnf
    id("org.jetbrains.grammarkit") version "2023.3.0.4" apply false
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.5.0" apply false
    // Gradle Kover Plugin
    id("org.jetbrains.kotlinx.kover") version "0.9.11"
    // Shadow / uber-jar (bundles libGDX + LWJGL classes into the plugin jar)
    id("com.gradleup.shadow") version "9.6.1" apply false
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

tasks.named<Wrapper>("wrapper") {
    gradleVersion = properties("gradleVersion").get()
}

// Merge coverage from every module, including library code exercised by another module's tests.
dependencies {
    subprojects.forEach { module ->
        kover(project(module.path))
    }
}

kover {
    reports {
        total {
            html {
                onCheck = true
            }
            xml {
                onCheck = true
            }
        }
    }
}

// Shared by every module: Kotlin on Java 21 and Kover reports. Plugin-specific setup (changelog, the GLTF grammar, the source
// checks and plugin verification) stays in projects/plugin-abyssus.
subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    repositories {
        mavenLocal()
        mavenCentral()
    }


    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    configure<KoverProjectExtension> {
        reports {
            total {
                html {
                    onCheck = true
                }
                xml {
                    onCheck = true
                }
            }
        }
    }

    // every module scans its own sources
    extra["abyssusRunCatchingRoots"] = listOf("src/main/kotlin")
    apply(from = "${project.rootDir}/gradle/checks.gradle.kts")
    apply(from = "${project.rootDir}/gradle/package-cycles.gradle.kts")
}
