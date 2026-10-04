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
            throw GradleException("Singletons are not allowed in :runtime; inject an instance instead:\n" + found.joinToString("\n"))
        }
    }
}

tasks.check {
    dependsOn(checkNoSingletons)
}
