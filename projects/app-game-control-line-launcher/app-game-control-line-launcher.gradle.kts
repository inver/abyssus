/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// The installed Control Line's entry point (the jpackage main jar): picks the newest verified game version, checks for an
// update on a daemon thread and runs the game from its own class loader. Depends on `lib-updater` and the Kotlin stdlib only.
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("abyssus.dependency-platforms")
}

val launcherVersion = providers.gradleProperty("controlLineVersion").orElse("0.0.0")

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":lib-updater"))
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":lib-updater")))
}

// the seed's version is the launcher's: the installer that carries it carries the seed game
tasks.processResources {
    val version = launcherVersion
    inputs.property("controlLineVersion", version)
    filesMatching("launcher.properties") { expand("version" to version.get()) }
}

extra["abyssusSingletonExcludes"] = listOf<String>()
extra["abyssusShippedLibrary"] = true

// The launcher ships with the game: nothing on its runtime classpath may reach an editor-only module.
val checkShippedClasspath by tasks.registering {
    group = "verification"
    description = "Fails when the launcher's runtime classpath holds an editor-only module."
    val root = configurations.runtimeClasspath.flatMap { it.incoming.resolutionResult.rootComponent }
    doLast {
        val editorOnly = setOf(":lib-core-editor", ":lib-game-link", ":lib-raytracing")
        val found = sortedSetOf<String>()
        val seen = mutableSetOf<ResolvedComponentResult>()
        fun visit(component: ResolvedComponentResult) {
            if (!seen.add(component)) return
            val id = component.id
            if (id is ProjectComponentIdentifier && (id.projectPath in editorOnly || id.projectPath.startsWith(":plugin-"))) {
                found += id.projectPath
            }
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { visit(it.selected) }
        }
        visit(root.get())
        if (found.isNotEmpty()) {
            throw GradleException("The launcher's runtime classpath must not hold editor code, but it reaches: " + found.joinToString())
        }
    }
}
tasks.named("check") { dependsOn(checkShippedClasspath) }
