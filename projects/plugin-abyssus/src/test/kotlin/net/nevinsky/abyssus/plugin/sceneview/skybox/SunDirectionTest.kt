package net.nevinsky.abyssus.plugin.sceneview.skybox

import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class SunDirectionTest {
    private val white = Rgba(1f, 1f, 1f, 1f)

    private fun light(kind: LightKind, intensity: Float, direction: Vector3) =
        LightPlacement("e", kind, white, intensity, Vector3(0f, 0f, 0f), direction)

    @Test
    fun followsTheBrightestLight() {
        val sun = SunDirection.of(
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
        assertEquals(SunDirection.DEFAULT, SunDirection.of(listOf(light(LightKind.DIRECTIONAL, 1f, Vector3(0f, 0f, 0f)))))
    }

    @Test
    fun sunLightIsTheLightTheSkySunFollows() {
        val lights = listOf(
            LightPlacement("7", LightKind.DIRECTIONAL, white, 5f, Vector3(0f, 0f, 0f), Vector3(0f, -1f, 0f)),
            LightPlacement("3", LightKind.DIRECTIONAL, white, 1f, Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f)),
            LightPlacement("9", LightKind.DIRECTIONAL, white, 50f, Vector3(0f, 0f, 0f), Vector3(0f, 0f, 0f)),
            LightPlacement("8", LightKind.SPOT, white, 90f, Vector3(0f, 0f, 0f), Vector3(0f, -1f, 0f)),
        )
        val sun = SunDirection.sunLight(lights)!!
        assertEquals("7", sun.entityId)
        val d = sun.direction
        assertEquals(SunDirection.of(lights), Vector3(0f - d.x, 0f - d.y, 0f - d.z))
        assertEquals(null, SunDirection.sunLight(emptyList()))
    }
}
