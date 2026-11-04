package net.nevinsky.abyssus.plugin.sceneview.skybox

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import kotlin.math.sqrt
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

class SunDirectionTest {
    private val white = Color(1f, 1f, 1f, 1f)

    private fun light(kind: LightKind, intensity: Float, direction: Vector3) =
        LightPlacement(1L, kind, white, intensity, Vector3(0f, 0f, 0f), direction)

    @Test
    fun followsTheBrightestLight() {
        val sun =
            SunDirection.of(
                listOf(
                    light(LightKind.DIRECTIONAL, 1f, Vector3(1f, 0f, 0f)),
                    light(LightKind.DIRECTIONAL, 5f, Vector3(0f, -1f, 0f)),
                )
            )
        assertEquals(Vector3(0f, 1f, 0f), sun)
    }

    @Test
    fun defaultsWithoutADirectionalLight() {
        assertEquals(SunDirection.DEFAULT, SunDirection.of(emptyList()))
    }

    @Test
    fun ignoresPointLights() {
        assertEquals(SunDirection.DEFAULT, SunDirection.of(listOf(light(LightKind.POINT, 9f, Vector3(0f, -1f, 0f)))))
    }

    @Test
    fun resultIsUnitLength() {
        val s = SunDirection.of(listOf(light(LightKind.DIRECTIONAL, 1f, Vector3(3f, -4f, 12f))))
        assertEquals(1f, sqrt(s.x * s.x + s.y * s.y + s.z * s.z), 1e-5f)
    }

    @Test
    fun unusableLightFallsBackToDefault() {
        assertEquals(
            SunDirection.DEFAULT,
            SunDirection.of(listOf(light(LightKind.DIRECTIONAL, 1f, Vector3(0f, 0f, 0f)))),
        )
    }

    @Test
    fun sunLightIsTheLightTheSkySunFollows() {
        val lights =
            listOf(
                LightPlacement(7L, LightKind.DIRECTIONAL, white, 5f, Vector3(0f, 0f, 0f), Vector3(0f, -1f, 0f)),
                LightPlacement(3L, LightKind.DIRECTIONAL, white, 1f, Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f)),
                LightPlacement(9L, LightKind.DIRECTIONAL, white, 50f, Vector3(0f, 0f, 0f), Vector3(0f, 0f, 0f)),
                LightPlacement(8L, LightKind.SPOT, white, 90f, Vector3(0f, 0f, 0f), Vector3(0f, -1f, 0f)),
            )
        val sun = SunDirection.sunLight(lights)!!
        assertEquals(7L, sun.entityId)
        val d = sun.direction
        assertEquals(SunDirection.of(lights), Vector3(0f - d.x, 0f - d.y, 0f - d.z))
        assertEquals(null, SunDirection.sunLight(emptyList()))
    }
}
