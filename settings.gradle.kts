/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
pluginManagement {
    // convention plugins (abyssus.dependency-platforms)
    includeBuild("build-logic")
}

include(":lib-gdx-model")
include(":lib-core")
include(":lib-raytracing")
include(":lib-core-editor")
include(":lib-runtime")
include(":lib-physics")
include(":plugin-abyssus-physics")
include(":plugin-abyssus")
include(":app-game-control-line")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
        gradlePluginPortal()
    }
}

rootProject.name = "abyssus"
rootProject.children.forEach { project ->
    val fileBaseName = project.name.replace(Regex("\\p{Upper}")) { "-${it.value.lowercase()}" }
    val projectDirName = "projects/$fileBaseName"

    project.projectDir = File(settingsDir, projectDirName)
    project.buildFileName = "$fileBaseName.gradle.kts"
    project.name = fileBaseName
    assert(project.projectDir.isDirectory)
    assert(project.buildFile.isFile)
}
