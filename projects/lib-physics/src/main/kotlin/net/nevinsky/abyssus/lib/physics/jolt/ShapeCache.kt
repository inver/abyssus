/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import com.github.stephengold.joltjni.ShapeRefC
import com.github.stephengold.joltjni.readonly.ConstJoltPhysicsObject
import net.nevinsky.abyssus.lib.physics.ColliderShape

/**
 * Collider shapes built from assets (convex hulls and height fields), kept for a game run (design decision D11 of
 * `improve-control-line-performance-stability`): a flight start no longer builds every plane's hull again. A shape is
 * kept by its kind, its asset and the entity's scale; an offset is applied per world around the kept shape. Jolt shapes
 * are immutable and reference counted, so the worlds that use one share it and a body keeps its own reference.
 *
 * Wired by constructor, not shared globally: whoever runs the flights makes one, passes it to each [PhysicsWorld] and
 * [close]s it after the last world. One thread, like the worlds.
 */
class ShapeCache : AutoCloseable {
    internal data class Key(
        val shape: ColliderShape,
        val asset: String,
        val scaleX: Float,
        val scaleY: Float,
        val scaleZ: Float,
    )

    private val shapes = HashMap<Key, ShapeRefC>()
    private val owned = ArrayList<AutoCloseable>()
    private var closed = false

    /** How many shapes were built through this cache, of [hullsBuilt] convex hulls: a hit builds none. */
    var built = 0
        private set

    var hullsBuilt = 0
        private set

    /** The shape kept under [key], or the one [build] makes (kept from now on); null when [build] refused. */
    internal fun shape(key: Key, build: (own: (ConstJoltPhysicsObject) -> Unit) -> ShapeRefC?): ShapeRefC? {
        check(!closed) { "the shape cache is closed" }
        shapes[key]?.let {
            return it
        }
        val made = build { owned += it as AutoCloseable } ?: return null
        built++
        if (key.shape == ColliderShape.CONVEX_HULL) {
            hullsBuilt++
        }
        shapes[key] = made
        return made
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        shapes.clear()
        for (o in owned.asReversed()) o.close()
        owned.clear()
    }
}
