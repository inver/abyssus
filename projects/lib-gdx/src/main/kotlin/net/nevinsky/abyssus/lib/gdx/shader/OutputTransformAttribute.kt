/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.g3d.Attribute

/**
 * Turns the [OutputTransform] on for the renderables of an [com.badlogic.gdx.graphics.g3d.Environment]: the model
 * shaders then compile `outputTransformFlag`, decode colour textures and fog from sRGB, and end in the transform. An
 * environment without it renders exactly as before, so a host opts in by setting it.
 */
class OutputTransformAttribute(val transform: OutputTransform) : Attribute(Type) {
    constructor(exposure: Float) : this(OutputTransform(exposure))

    override fun copy(): Attribute = OutputTransformAttribute(transform)

    override fun hashCode(): Int = 31 * super.hashCode() + transform.hashCode()

    override fun equals(other: Any?): Boolean = other is OutputTransformAttribute && other.transform == transform

    override fun compareTo(other: Attribute): Int {
        if (type != other.type) return type.compareTo(other.type)
        return transform.exposure.compareTo((other as OutputTransformAttribute).transform.exposure)
    }

    companion object {
        const val Alias = "outputTransform"

        @JvmField
        val Type: Long = register(Alias)
    }
}
