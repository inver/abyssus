/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.g3d.Attribute

/**
 * Marks an environment drawn into a linear HDR target that a host's final pass shows through the [OutputTransform]
 * (OpenSpec `render-output-transform`, "Transform is applied once, in linear light"). With an
 * [OutputTransformAttribute] beside it, the model shaders compile `linearTargetFlag` as well as `outputTransformFlag`:
 * they decode colour inputs from sRGB and light in linear space as usual, but write the lit linear colour, unclamped,
 * instead of ending in the transform. Without an [OutputTransformAttribute] it changes nothing.
 */
class LinearTargetAttribute : Attribute(Type) {
    override fun copy(): Attribute = LinearTargetAttribute()

    override fun hashCode(): Int = super.hashCode()

    override fun equals(other: Any?): Boolean = other is LinearTargetAttribute

    override fun compareTo(other: Attribute): Int = type.compareTo(other.type)

    companion object {
        const val Alias = "linearTarget"

        @JvmField
        val Type: Long = register(Alias)
    }
}
