/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.terrain.FastNoiseSamplerFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * The exact heights every sculpt tool leaves after one fixed stroke over one fixed bumpy terrain, as a SHA-256 of the
 * delta's float bits. They were taken before the stamp engine was shared with the foliage brush, and a refactor of
 * the brush must reproduce them bit for bit.
 */
class SculptBrushGoldenTest {
    private val resolution = 33
    private val size = 64

    private val image = StampImage(3, 2, floatArrayOf(0f, 0.25f, 1f, 0.5f, 0.75f, 0.1f))

    private fun draft(): SculptDraft {
        val base = FloatArray(resolution * resolution) {
            val x = it % resolution
            val z = it / resolution
            ((x * 7 + z * 13) % 11) * 0.5f + x * 0.1f
        }
        return SculptDraft(resolution, base, base.copyOf(), FloatArray(base.size))
    }

    private fun hashOf(delta: FloatArray): String {
        val bytes = ByteBuffer.allocate(delta.size * 4)
        delta.forEach { bytes.putFloat(it) }
        return MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    }

    private fun stroke(tool: SculptTool, inverted: Boolean = false, world: Matrix4 = Matrix4()): String {
        val d = draft()
        val brush = SculptBrush(d, size, world)
        val a = Vector3(20f, 0f, 20f).mul(world)
        val b = Vector3(34f, 0f, 30f).mul(world)
        val c = Vector3(44f, 0f, 40f).mul(world)
        brush.stroke(a, b, 12f, 0.8f, tool, inverted)
        brush.stroke(b, c, 12f, 0.8f, tool, inverted)
        return hashOf(d.delta)
    }

    private fun check(name: String, actual: String) = assertEquals(name, GOLDEN.getValue(name), actual)

    @Test
    fun raise() = check("raise", stroke(RaiseLowerTool()))

    @Test
    fun lower() = check("lower", stroke(RaiseLowerTool(), inverted = true))

    @Test
    fun smooth() = check("smooth", stroke(SmoothTool()))

    @Test
    fun flatten() = check("flatten", stroke(FlattenTool()))

    @Test
    fun setHeight() = check("setHeight", stroke(SetHeightTool(3f)))

    @Test
    fun noise() = check("noise", stroke(noiseTool(FastNoiseSamplerFactory(), 7, 20f, 4f)))

    @Test
    fun slopeLimit() = check("slopeLimit", stroke(SlopeLimitTool(25f)))

    @Test
    fun erosion() = check("erosion", stroke(ThermalErosionTool(20f, 6)))

    @Test
    fun stampAdd() = check("stampAdd", stroke(StampTool(image, 18f, 6f, 30f, StampBlend.ADD)))

    @Test
    fun stampMax() = check("stampMax", stroke(StampTool(image, 18f, 6f, 0f, StampBlend.MAX)))

    @Test
    fun stampMin() = check("stampMin", stroke(StampTool(image, 18f, 6f, 0f, StampBlend.MIN)))

    @Test
    fun raiseOverAScaledYawRotatedEntity() {
        val world = Matrix4().setToTranslation(100f, 5f, -40f).rotate(Vector3.Y, 33f).scl(2f, 1f, 2f)
        check("raiseTilted", stroke(RaiseLowerTool(), world = world))
    }

    private companion object {
        val GOLDEN = mapOf(
            "raise" to "124fb8de908592de3fc287b0876f0bbf4bbe322c71e88b9ca5e3dba84cd624a9",
            "lower" to "08d7d31f7a0260d60829765db536731be75c0c40d8877ffa30db56a8011511aa",
            "smooth" to "9e70e57616176b6572fae1d0a374de5c9445454e410c384b0dd4ab7837fbe96e",
            "flatten" to "8b1b6474968a40e2160336626735fd05a5d1ca5c0524c600bd70260984dbabbe",
            "setHeight" to "432852908dc939a542242aceb5902d6369d9e774174125e9bab45c6a67f15706",
            "noise" to "9462e51343d143d38b191f7ecaf0fad53d2a71114734c77a4e0b6f39e9cfd165",
            "slopeLimit" to "b2d5f3e4d730e72c9818f50c4004d6aa0b77260248cf0ac543e4e088c26b9f21",
            "erosion" to "a46833ead38b0f2abc96644defd31051135e5c326e81498e48f58e2b40a18e35",
            "stampAdd" to "b642add947af14c0ff5ce7fa795bae625c38b052202ac95c83c382afc54ccfa8",
            "stampMax" to "159a4fe761e72ba84b6af92b6fa473b3ecf2d650775f343444c93885735fe468",
            "stampMin" to "ff9ba76abef8f9a5c49ed7df67225381f56819caf5041a84b8876bf9c1b20194",
            "raiseTilted" to "e060e2ed6d46770c93c40b509758f383997f95739189f362fad8e89c07246990",
        )
    }
}
