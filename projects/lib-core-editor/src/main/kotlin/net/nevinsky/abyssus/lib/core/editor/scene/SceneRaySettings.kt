/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.raytracing.MAX_RAYS_PER_FRAME

/** How the ray view samples a scene. Not saved in the scene: every scene is traced with these limits. */
data class SceneRaySettings(
    val targetSamplesPerPixel: Int = 256,
    val maxRaysPerFrame: Int = 33554432,
    val maxReflectionBounces: Int = 1,
    val maxRefractionBounces: Int = 0,
    /** Most model parts and terrains one ray view may hold; the backends cap it at 4096. */
    val maxInstances: Int = 4096,
) {
    init {
        require(maxInstances in 1..4096)
        require(targetSamplesPerPixel in 1..4096 && maxRaysPerFrame in 1..MAX_RAYS_PER_FRAME)
        require(maxReflectionBounces in 0..16 && maxRefractionBounces in 0..16)
    }
}
