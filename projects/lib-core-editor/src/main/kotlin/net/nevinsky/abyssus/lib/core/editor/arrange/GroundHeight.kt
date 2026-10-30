/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

/** Where the ground is under a world X/Z. */
fun interface GroundHeight {
    /** The ground height at [x], [z]; [fallbackY], the height of the line there, over no ground. */
    fun heightAt(x: Float, z: Float, fallbackY: Float): Float
}

/** The ground of a scene without terrain data: every copy takes the height of the line. */
class LineHeights : GroundHeight {
    override fun heightAt(x: Float, z: Float, fallbackY: Float): Float = fallbackY
}
