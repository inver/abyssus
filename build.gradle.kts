/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    // IntelliJ Platform Gradle Plugin
    id("org.jetbrains.intellij.platform") version "2.19.0"
    // Generates the GLTF lexer and parser from Gltf.flex / Gltf.bnf
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.5.0"
    // Gradle Kover Plugin
    id("org.jetbrains.kotlinx.kover") version "0.9.11"
    // Shadow / uber-jar (bundles libGDX + LWJGL classes into the plugin jar)
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(properties("platformType"), properties("platformVersion"))
        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
        plugins(properties("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) })
        // JSON language support: .scene files are JSON
        bundledPlugin("com.intellij.modules.json")
        pluginVerifier()
        zipSigner()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    // HdrFixtures: Radiance test images from a pixel function
    testImplementation(testFixtures(project(":core")))

    // JSON reading/writing for asset files; the platform does not ship jackson-databind, so it is bundled
    implementation("com.fasterxml.jackson.core:jackson-databind:${properties("jacksonVersion").get()}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${properties("jacksonVersion").get()}")

    // LWJGL 3 + AWT bridge for the OpenGL scene panel
    val lwjglVersion = properties("lwjglVersion").get()
    val lwjglNatives = listOf("natives-macos-arm64", "natives-macos", "natives-windows", "natives-linux")
    implementation("org.lwjglx:lwjgl3-awt:0.2.5")
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")
    lwjglNatives.forEach {
        runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$it")
        runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$it")
        // HDR preview regressions decode EXR files without altering the plugin's packaged runtime.
        testRuntimeOnly("org.lwjgl:lwjgl-tinyexr:$lwjglVersion:$it")
    }

    // libGDX core (g3d, math) hosted on the AWT GL canvas; only the backend's GL wrapper classes are used
    val gdxVersion = properties("gdxVersion").get()
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion") { isTransitive = false }
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

    // Entity-component engine behind the scene model (net.nevinsky.abyssus.ecs)
    implementation(project(":runtime")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }

    // Model runtime + Assimp importer (brings lwjgl-assimp and its natives); the IDE provides Kotlin and the SLF4J API
    implementation(project(":gdx-model")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    // Asset reading and loading (plain JVM, see core/README.md); the IDE provides Kotlin and SLF4J here too
    implementation(project(":core")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    implementation(project(":raytracing")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    // The editing engine without the IDE (see editor-core/README.md); the plugin keeps the IDE glue
    implementation(project(":editor-core")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
}

// Set the JVM language level used to build the project. IntelliJ 2025.2+ requires Java 21.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginVerification {
        ides { recommended() }
        // Internal usages are checked against exact descriptions by checkPluginInternalApis below.
        failureLevel = listOf(VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES)
    }
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = properties("pluginUntilBuild")
        }
    }
    signing {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = environment("PUBLISH_TOKEN")
        channels = properties("pluginVersion").map { listOf(it.split('-').getOrElse(1) { "default" }.split('.').first()) }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl.set(properties("pluginRepositoryUrl"))
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    test {
        // GL render tests open a real window: opt in with -Dabyssus.glTests=true
        System.getProperty("abyssus.glTests")?.let { systemProperty("abyssus.glTests", it) }
        System.getProperty("abyssus.rayTimingTests")?.let { systemProperty("abyssus.rayTimingTests", it) }
        // device tests that render through the real Metal backend: opt in with -Dabyssus.metalTests=true
        System.getProperty("abyssus.metalTests")?.let { systemProperty("abyssus.metalTests", it) }
        // and through the real Vulkan backend: -Dabyssus.vulkanTests=true (add -Dabyssus.raytracing.validation=true for the validation layer)
        System.getProperty("abyssus.vulkanTests")?.let { systemProperty("abyssus.vulkanTests", it) }
        System.getProperty("abyssus.raytracing.validation")?.let { systemProperty("abyssus.raytracing.validation", it) }
    }
    runIde {
        // Open a project on startup: -PideProject=/path/to/project
        providers.gradleProperty("ideProject").orNull?.let { args(it) }
        providers.gradleProperty("rayExperiment").orNull?.let { jvmArgs("-Dabyssus.raytracing.experiment=$it") }
        // The sandbox IDE trusts every project (no "Trust project?" dialog) and opens the Abyssus view.
        jvmArgs("-Didea.trust.all.projects=true", "-Dabyssus.openView=true")
    }
    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    patchPluginXml {

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with (it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        })

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes.set(properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        })
    }

}

// Sandbox IDE with the robot-server plugin for UI tests (run configuration "Run IDE for UI Tests", run-ui-tests.yml)
val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
                "-Didea.trust.all.projects=true",
            )
        }
    }
    plugins {
        robotServerPlugin()
    }
}

sourceSets["main"].java {
    srcDirs("src/main/gen")
}

val gltfGrammarDir = "src/main/java/net/nevinsky/abyssus/language/psi"

val generateGltfParser by tasks.registering(org.jetbrains.grammarkit.tasks.GenerateParserTask::class) {
    sourceFile.set(file("$gltfGrammarDir/Gltf.bnf"))
    targetRootOutputDir.set(file("src/main/gen"))
    pathToParser.set("/net/nevinsky/abyssus/language/parser/GltfParser.java")
    pathToPsiRoot.set("/net/nevinsky/abyssus/language/psi")
    purgeOldFiles.set(true)
}

val generateGltfLexer by tasks.registering(org.jetbrains.grammarkit.tasks.GenerateLexerTask::class) {
    sourceFile.set(file("$gltfGrammarDir/Gltf.flex"))
    targetOutputDir.set(file("src/main/gen/net/nevinsky/abyssus/language/lexer"))
    purgeOldFiles.set(true)
}

tasks.named("compileKotlin") { dependsOn(generateGltfParser, generateGltfLexer) }
tasks.named("compileJava") { dependsOn(generateGltfParser, generateGltfLexer) }

extra["abyssusRunCatchingRoots"] = listOf(
    "src/main/kotlin", "core/src/main/kotlin", "runtime/src/main/kotlin", "physics/src/main/kotlin", "editor-core/src/main/kotlin",
    "raytracing/src/main/kotlin", "physics-plugin/src/main/kotlin", "games/control-line/src/main/kotlin",
)
apply(from = "gradle/checks.gradle.kts")
apply(from = "gradle/package-cycles.gradle.kts")
apply(from = "gradle/plugin-verification.gradle.kts")
