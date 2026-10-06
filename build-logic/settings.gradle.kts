/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// Convention plugins shared by the modules under projects/. Included from the root settings as a plugin build.
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "build-logic"
