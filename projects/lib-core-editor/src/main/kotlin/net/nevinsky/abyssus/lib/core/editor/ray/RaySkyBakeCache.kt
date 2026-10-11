/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.Completion
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.LightingFrame
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.LightingRefreshPolicy
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import java.io.File

/**
 * The baked picture of a procedural sky for ray tracing, kept until it is stale: a different sky or project, a sun that moved
 * more than a degree, a different or removed cloud asset (its revision), clouds appearing or going, and, while the clouds
 * drift, every two seconds of sky time ([LightingRefreshPolicy] decides). Every bake is a new, immutable
 * [RaySkySnapshot], so a consumer that compares snapshots by identity sees exactly the refreshes, bounded in frequency; a
 * bake that fails is not retried until something changes. Render thread only; the bake itself is the caller's.
 */
class RaySkyBakeCache {
    private val policy = newPolicy()
    private var sky: Pair<String, File?>? = null
    private var snapshot: RaySkySnapshot? = null

    /**
     * The current picture of the sky [name] of [project] for the sun toward [sun] at sky time [timeSeconds]: [bake] runs when
     * it is stale (it may return null or throw for a sky that cannot be baked, and then the picture is null until the next
     * change). [cloudRevision] numbers the displayed cloud asset, [cloudsVisible] says the sky draws clouds and [drifting]
     * that they move.
     */
    fun get(
        name: String, project: File?, sun: Vec3, timeSeconds: Double, cloudRevision: Long, cloudsVisible: Boolean,
        drifting: Boolean, bake: () -> RaySkySnapshot?,
    ): RaySkySnapshot? {
        if (sky != name to project) {
            sky = name to project
            snapshot = null
            policy.reset()
        }
        val step = policy.update(LightingFrame(Vector3(sun.x, sun.y, sun.z), timeSeconds, cloudRevision, cloudsVisible, drifting))
        val ticket = step.build ?: return snapshot
        val baked = try {
            bake()
        } catch (failure: Exception) {
            null
        }
        // a failed bake keeps no stale picture of an older sun: the ray view then falls back as it does for a missing sky
        snapshot = baked
        if (baked == null) policy.failed(ticket) else policy.completed(ticket)
        return snapshot
    }

    /** Forgets the picture (the GL context it was made in is gone, or the view closed). */
    fun clear() {
        sky = null
        snapshot = null
        policy.reset()
    }

    private fun newPolicy() = LightingRefreshPolicy(fadeSeconds = 0.0)
}
