/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// Abyssus Physics: an IntelliJ plugin that depends on Abyssus (the root project). It bundles only its own code;
// libGDX, `core`, `runtime`, `editor-core` and `gdx-model` come from Abyssus's classloader, and Jolt never loads in the IDE.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    // LWJGL, libGDX and JUnit versions from the catalog (build-logic)
    id("abyssus.dependency-platforms")
}

fun properties(key: String) = providers.gradleProperty(key)

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

/**
 * The bundled play host (`play-host/` in the plugin, outside `lib/`): physics with everything it runs on, and jolt-jni
 * with the four platforms' release natives. Play launches it in its own JVM when a project has no `play.json`.
 */
val playHostLibs by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

/** The classpath that runs `SchemaExportMain` on `PhysicsComponents` at build time. */
val schemaExport by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    intellijPlatform {
        create(properties("platformType"), properties("platformVersion"))
        bundledPlugin("com.intellij.modules.json")
        localPlugin(project(":plugin-abyssus"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation(libs.junit4)
    // The protocol, play.json and the physics components; the plugin's own code bundles physics.jar alone (no
    // jolt-jni, runtime or libGDX: those are the play host's or come from Abyssus)
    implementation(project(":lib-physics")) { isTransitive = false }
    // runtime, core, gdx-model, libGDX, Ashley and Jackson load from Abyssus's classloader at run time
    // the overlay and play types (editor.content placements) come from Abyssus's editor-core, never bundled here
    compileOnly(project(":lib-core-editor"))
    testImplementation(project(":lib-core-editor"))
    schemaExport(project(":lib-physics"))
    playHostLibs(project(":lib-physics"))
    playHostLibs(project(path = ":lib-physics", configuration = "playHost"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

intellijPlatform {
    pluginConfiguration {
        name = "Abyssus Physics"
        version = properties("pluginVersion")
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = properties("pluginUntilBuild")
        }
    }
    buildSearchableOptions = false
}

// Jolt's natives must never load in the IDE: the plugin's own code may not name jolt-jni or physics' Jolt package.
val checkNoJolt by tasks.registering {
    val sources = fileTree("src/main") { include("**/*.kt", "**/*.java", "**/*.xml") }
    val root = layout.projectDirectory.asFile
    inputs.files(sources)
    doLast {
        val forbidden = Regex("""com\.github\.stephengold|net\.nevinsky\.abyssus\.lib\.physics\.jolt""")
        val found = sources.files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if (forbidden.containsMatchIn(line)) "${file.relativeTo(root)}:${i + 1}: ${line.trim()}" else null
            }
        }
        if (found.isNotEmpty()) {
            throw GradleException(
                "Abyssus Physics must not load Jolt in the IDE; remove these references:\n" + found.joinToString(
                    "\n"
                )
            )
        }
    }
}

tasks.named("check") { dependsOn(checkNoJolt) }

tasks.test {
    systemProperty("abyssus.testData", rootProject.file("projects/plugin-abyssus/src/test/testData").absolutePath)
    // BundledPlayHostTest plays a scene with the play host as the plugin ships it
    val pluginDirectory = tasks.prepareSandbox.flatMap { it.pluginDirectory }
    dependsOn(tasks.prepareSandbox)
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf(
            "-Dabyssus.playHost=${
                pluginDirectory.get().asFile.resolve(
                    "play-host"
                )
            }"
        )
    }
}

tasks.prepareSandbox {
    from(playHostLibs) { into(pluginName.map { "$it/play-host" }) }
}

apply(from = "${project.rootDir}/gradle/plugin-verification.gradle.kts")
