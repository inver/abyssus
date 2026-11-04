import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
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

apply(plugin = "checkstyle")

configure<CheckstyleExtension> {
    configFile = layout.projectDirectory.file("gradle/checkstyle/checkstyle.xml").asFile
    toolVersion = "14.3.0"
}

// Checkstyle cannot parse Kotlin, but its file-level rules (line endings, whitespace, and the regex rules in
// gradle/checkstyle/checkstyle.xml) work on any text file. Feed every Kotlin source and build script to one task
// instead of relying on the Java source sets, which only contain .java files.
val checkstyleKotlin by tasks.registering(Checkstyle::class) {
    group = "verification"
    description = "Runs the project Checkstyle rules over all Kotlin sources and build scripts."
    source = fileTree(rootDir) {
        include("**/*.kt", "**/*.kts")
        exclude(
            "**/build/**",
            "**/.gradle/**",
            "**/.git/**",
            "**/.idea/**",
            "**/.kotlin/**",
            "**/.intellijPlatform/**",
            "**/.agents/**",
            "**/.claude/**",
            "**/.kilo/**",
            "**/.run/**",
            "**/.vscode/**",
            "**/src/main/gen/**",
            "**/src/uiTest/**",
            "openspec/**",
            "docs/**",
        )
    }
    configFile = layout.projectDirectory.file("gradle/checkstyle/checkstyle.xml").asFile
    classpath = configurations["checkstyle"]
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

// Shared by every module: Kotlin on Java 21 and Kover reports. Plugin-specific setup (changelog, the GLTF grammar, the
// source
// checks and plugin verification) stays in projects/plugin-abyssus.
subprojects {
    apply(plugin = "java")
//    apply(plugin = "checkstyle")
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

//    configure<CheckstyleExtension> {
//        configFile = rootProject.layout.projectDirectory.file("gradle/checkstyle/checkstyle.xml").asFile
//        toolVersion = "14.3.0"
//        sourceSets = listOf(project.the<SourceSetContainer>()["main"])
//    }
//
//    tasks.named("check") {
//        dependsOn("checkstyle")
//    }

    tasks.named("check") {
        dependsOn(rootProject.tasks.named("checkstyleKotlin"))
    }

    // the native fixture projects are shared by every module's tests; see testProject() in lib-core's testFixtures
    tasks.withType<Test>().configureEach {
        systemProperty("abyssus.testData", rootProject.file("projects/plugin-abyssus/src/test/testData").absolutePath)
    }

    // every module scans its own sources
    extra["abyssusRunCatchingRoots"] = listOf("src/main/kotlin")
    apply(from = "${project.rootDir}/gradle/checks.gradle.kts")
    apply(from = "${project.rootDir}/gradle/package-cycles.gradle.kts")
}
