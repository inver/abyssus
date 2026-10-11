/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

/** Most colliding foliage copies one physics world admits. */
const val FOLIAGE_COLLIDER_COPY_CAP = 200_000

/** The reason [FoliageAdmission.admit] refused a layer. */
enum class FoliageRefusal { COPY_CAP, BODY_CAPACITY }

/**
 * Decides, layer by layer, whether the colliders of a foliage layer fit the world: at most [copyCap] colliding copies
 * in all and [bodyCapacity] more bodies (one per nonempty chunk) than the world's capacity leaves after its ordinary
 * bodies. A layer is admitted whole or refused whole, in the order the caller offers them (numeric entity id, then
 * metadata layer order), so what fits never depends on timing. Nothing is allocated here; the caller refuses before
 * any native call. Not thread safe.
 */
class FoliageAdmission(private val copyCap: Int = FOLIAGE_COLLIDER_COPY_CAP, private val bodyCapacity: Int) {
    var copies: Int = 0
        private set
    var bodies: Int = 0
        private set

    /** Admits a layer of [layerCopies] copies in [layerBodies] bodies; null when it was admitted, else why it was not. */
    fun admit(layerCopies: Long, layerBodies: Int): FoliageRefusal? {
        if (copies + layerCopies > copyCap) return FoliageRefusal.COPY_CAP
        if (bodies + layerBodies > bodyCapacity) return FoliageRefusal.BODY_CAPACITY
        copies += layerCopies.toInt()
        bodies += layerBodies
        return null
    }
}
