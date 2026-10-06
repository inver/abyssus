package net.nevinsky.abyssus.sceneview.skybox

import net.nevinsky.abyssus.editor.content.LightKind
import net.nevinsky.abyssus.editor.content.LightPlacement
import net.nevinsky.abyssus.editor.content.Rgba
import net.nevinsky.abyssus.editor.content.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class SunDirectionTest {
    private val white = Rgba(1f, 1f, 1f, 1f)

    private fun light(kind: LightKind, intensity: Float, direction: Vec3) =
        LightPlacement("e", kind, white, intensity, Vec3(0f, 0f, 0f), direction)

    @Test
    fun followsTheBrightestLight() {
        val sun = SunDirection.of(
            listOf(
                light(LightKind.DIRECTIONAL, 1f, Vec3(1f, 0f, 0f)),
                light(LightKind.DIRECTIONAL, 5f, Vec3(0f, -1f, 0f)),
            )
        )
        assertEquals(Vec3(0f, 1f, 0f), sun)
    }

    @Test
    fun defaultsWithoutADirectionalLight() {
        assertEquals(SunDirection.DEFAULT, SunDirection.of(emptyList()))
    }

    @Test
    fun ignoresPointLights() {
        assertEquals(SunDirection.DEFAULT, SunDirection.of(listOf(light(LightKind.POINT, 9f, Vec3(0f, -1f, 0f)))))
    }

    @Test
    fun resultIsUnitLength() {
        val s = SunDirection.of(listOf(light(LightKind.DIRECTIONAL, 1f, Vec3(3f, -4f, 12f))))
        assertEquals(1f, sqrt(s.x * s.x + s.y * s.y + s.z * s.z), 1e-5f)
    }

    @Test
    fun unusableLightFallsBackToDefault() {
        assertEquals(SunDirection.DEFAULT, SunDirection.of(listOf(light(LightKind.DIRECTIONAL, 1f, Vec3(0f, 0f, 0f)))))
    }
}
