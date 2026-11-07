/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.math.Vector3

/**
 * Corrects the reflection of an [EnvironmentLightAttribute] captured inside a room (a reflection probe) to the room's
 * box [min]..[max], the probe captured at [probe] (parallax-corrected, "box-projected" reflection). With it, the PBR
 * shader compiles `envBoxFlag` and looks the specular cube up along the direction from [probe] to where the reflected
 * ray leaves the box ([boxProjected] is the reference), so a reflection on a large floor stays put as the camera moves.
 * Without it the cube is looked up along the reflected ray itself, as for a sky at infinity.
 */
class ProbeBoxAttribute(val min: Vector3, val max: Vector3, val probe: Vector3) : Attribute(Type) {
    override fun copy(): Attribute = ProbeBoxAttribute(Vector3(min), Vector3(max), Vector3(probe))

    override fun hashCode(): Int = 31 * (31 * (31 * super.hashCode() + min.hashCode()) + max.hashCode()) + probe.hashCode()

    override fun equals(other: Any?): Boolean =
        other is ProbeBoxAttribute && other.min == min && other.max == max && other.probe == probe

    override fun compareTo(other: Attribute): Int = type.compareTo(other.type)

    companion object {
        const val Alias = "probeBox"

        @JvmField
        val Type: Long = register(Alias)
    }
}

/**
 * The direction to look a box-projected cube up along (the reference of `envDirection` in `pbr.fragment.glsl`): from
 * [probe] to where the ray from [position] along [reflection] leaves the box [min]..[max]. [position] is inside the
 * box. [out] receives the (not normalised) direction and is returned.
 */
fun boxProjected(
    position: Vector3,
    reflection: Vector3,
    min: Vector3,
    max: Vector3,
    probe: Vector3,
    out: Vector3 = Vector3(),
): Vector3 {
    val r = Vector3(reflection).nor()
    fun safe(v: Float) = if (v == 0f) 1e-6f else v
    fun far(lo: Float, hi: Float, p: Float, d: Float) = kotlin.math.max((hi - p) / safe(d), (lo - p) / safe(d))
    val t =
        minOf(
            far(min.x, max.x, position.x, r.x),
            far(min.y, max.y, position.y, r.y),
            far(min.z, max.z, position.z, r.z),
        )
    return out.set(position).mulAdd(r, t).sub(probe)
}
