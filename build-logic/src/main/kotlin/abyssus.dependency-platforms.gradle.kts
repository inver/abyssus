/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// One version for every LWJGL, libGDX and JUnit module, taken from gradle/libs.versions.toml. Modules then declare
// these dependencies without a version (for example `libs.lwjgl.assimp`).
plugins {
    java
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    // gdx-backend-lwjgl3 and lwjgl3-awt ask for older LWJGL than lwjgl-assimp: the BOM keeps every module on one version
    implementation(platform(libs.findLibrary("lwjgl-bom").get()))
    testImplementation(platform(libs.findLibrary("junit-platform").get()))
}

// A library's consumers resolve its unversioned `api` dependencies too
pluginManager.withPlugin("java-library") {
    dependencies {
        "api"(platform(libs.findLibrary("lwjgl-bom").get()))
    }
}
pluginManager.withPlugin("java-test-fixtures") {
    dependencies {
        "testFixturesApi"(platform(libs.findLibrary("lwjgl-bom").get()))
        "testFixturesImplementation"(platform(libs.findLibrary("junit-platform").get()))
    }
}

// libGDX publishes no BOM: give unversioned libGDX modules the catalog version, and align the versioned ones.
// jnigen shares the group but has its own release cycle (e.g. gdx 1.14.2 uses gdx-jnigen-loader 2.5.2).
// Including it in the platform makes Gradle probe nonexistent gdx 2.5.2 artifacts across every repository.
val gdxVersion = libs.findVersion("gdx").get().requiredVersion
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.badlogicgames.gdx" && !requested.name.startsWith("gdx-jnigen") && requested.version.isNullOrEmpty()) {
            useVersion(gdxVersion)
            because("gdx version from gradle/libs.versions.toml")
        }
    }
}
dependencies.components.all {
    if (id.group == "com.badlogicgames.gdx" && !id.name.startsWith("gdx-jnigen")) {
        belongsTo("com.badlogicgames.gdx:gdx-virtual-platform:${id.version}")
    }
}
