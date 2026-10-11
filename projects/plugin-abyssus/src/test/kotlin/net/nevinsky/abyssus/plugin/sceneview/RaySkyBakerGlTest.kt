/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.ray.RaySkyBaker
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.content.Vec3

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.assets.sky.RAY_SKY_MAX_WIDTH
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lwjgl.opengl.GL32C.GL_NO_ERROR
import org.lwjgl.opengl.GL32C.glGetError

/** Opt-in (`-Dabyssus.glTests=true`): the procedural-sky bake lands each world direction where the raster sky shows it. */
class RaySkyBakerGlTest {
    /** Paints each pixel with its own world direction, built like a procedural sky does, from the inverse view-projection. */
    private class DirectionSky : SkyRenderer {
        private val program = ShaderProgram(
            "attribute vec2 a_position;\nuniform mat4 u_invViewProj;\nvarying vec3 v_dir;\n" +
                "void main() { vec4 p = u_invViewProj * vec4(a_position, 1.0, 1.0); v_dir = p.xyz / p.w; gl_Position = vec4(a_position, 1.0, 1.0); }",
            "#ifdef GL_ES\nprecision highp float;\n#endif\nvarying vec3 v_dir;\nvoid main() { gl_FragColor = vec4(normalize(v_dir) * 0.5 + 0.5, 1.0); }",
        ).also { check(it.isCompiled) { it.log } }
        private val mesh = Mesh(true, 3, 0, VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)).also {
            it.setVertices(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
        }
        private val inverse = Matrix4()
        override fun draw(camera: Camera, frame: SkyFrame) {
            val sun = frame.sun
            inverse.set(camera.view); inverse.setTranslation(0f, 0f, 0f); inverse.mulLeft(camera.projection); inverse.inv()
            program.bind(); program.setUniformMatrix("u_invViewProj", inverse); mesh.render(program, GL20.GL_TRIANGLES)
        }
        override fun dispose() { mesh.dispose(); program.dispose() }
    }

    @Test fun bakedSkyPutsEveryWorldDirectionWhereTheRasterSkyShowsIt() {
        assumeTrue(GlHarness.enabled)
        var checked = false
        val rendered = GlHarness.render(SceneRenderParams.DEFAULT, 2) { _, index ->
            if (index != 0) return@render
            val sky = DirectionSky()
            try {
                val snapshot = RaySkyBaker(faceSize = 128).bake(sky, Vec3(0f, 1f, 0f))
                assertEquals(RAY_SKY_MAX_WIDTH, snapshot.width); assertFalse(snapshot.hdr)
                val pixels = snapshot.rgba()
                fun at(u: Float, v: Float): FloatArray {
                    val x = (u * snapshot.width).toInt().coerceIn(0, snapshot.width - 1); val y = (v * snapshot.height).toInt().coerceIn(0, snapshot.height - 1)
                    return pixels.copyOfRange((y * snapshot.width + x) * 4, (y * snapshot.width + x) * 4 + 3)
                }
                // a direction d is painted (d / 2 + 1 / 2): the centre column faces -Z and the top row is +Y
                assertArrayEquals(floatArrayOf(.5f, .5f, 0f), at(.5f, .5f), .03f)   // -Z
                assertArrayEquals(floatArrayOf(1f, .5f, .5f), at(.75f, .5f), .03f)  // +X
                assertArrayEquals(floatArrayOf(0f, .5f, .5f), at(.25f, .5f), .03f)  // -X
                assertArrayEquals(floatArrayOf(.5f, .5f, 1f), at(0f, .5f), .03f)    // +Z across the seam
                assertArrayEquals(floatArrayOf(.5f, 1f, .5f), at(.5f, .004f), .05f) // +Y is the top row
                assertArrayEquals(floatArrayOf(.5f, 0f, .5f), at(.5f, .996f), .05f) // -Y is the bottom row
                // an oblique direction: azimuth 45 degrees toward +X from -Z, 30 degrees above the horizon
                val d = floatArrayOf(Math.sin(Math.PI / 4).toFloat() * Math.cos(Math.PI / 6).toFloat(), Math.sin(Math.PI / 6).toFloat(),
                    -Math.cos(Math.PI / 4).toFloat() * Math.cos(Math.PI / 6).toFloat())
                val u = .5f + Math.atan2(d[0].toDouble(), -d[2].toDouble()).toFloat() / (2f * Math.PI.toFloat())
                val v = Math.acos(d[1].toDouble()).toFloat() / Math.PI.toFloat()
                assertArrayEquals(floatArrayOf(d[0] / 2 + .5f, d[1] / 2 + .5f, d[2] / 2 + .5f), at(u, v), .03f)
                assertEquals("baking leaves the GL state clean", GL_NO_ERROR, glGetError())
                checked = true
            } finally { sky.dispose() }
        }
        rendered.error?.let { throw it }
        assertTrue("the bake ran inside the GL context", checked)
    }

    @Test fun bakedCloudsAppearDriftAndAreAbsentWhenNotAsked() {
        assumeTrue(GlHarness.enabled)
        var checked = false
        val rendered = GlHarness.render(SceneRenderParams.DEFAULT, 2) { _, index ->
            if (index != 0) return@render
            val dir = java.io.File("src/test/testData/project/Untitled/assets/skybox_physical")
            val scattered = net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds(
                "clouds",
                net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta(
                    low = net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand(
                        net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType.CUMULUS,
                        net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudLevel.LOW, coverage = .5f,
                    ),
                ),
                null,
            )
            val sky = net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky(
                net.nevinsky.abyssus.lib.core.assets.sky.procedural.PreparedProceduralSky(
                    net.nevinsky.abyssus.lib.core.assets.sky.procedural.AtmosphereParams(), java.io.File(dir, "sky.vert").readText(),
                    java.io.File(dir, "sky.frag").readText(), "skybox_physical", "clouds",
                ),
                net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets { scattered },
                net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage(),
            )
            try {
                val baker = RaySkyBaker(faceSize = 64)
                val noon = Vec3(0f, 1f, 0f)
                val clear = baker.bake(sky, noon).rgba()
                val clearLater = baker.bake(sky, noon, 900.0).rgba()
                val cloudy = baker.bake(sky, noon, 0.0, clouds = true).rgba()
                val cloudyLater = baker.bake(sky, noon, 900.0, clouds = true).rgba()
                assertArrayEquals("without clouds the picture does not depend on time", clear, clearLater, 0f)
                fun difference(a: FloatArray, b: FloatArray) = a.indices.count { Math.abs(a[it] - b[it]) > .02f }
                assertTrue("clouds are in the picture", difference(clear, cloudy) > clear.size / 50)
                assertTrue("drifting clouds change the picture with time", difference(cloudy, cloudyLater) > 0)
                assertEquals("baking leaves the GL state clean", GL_NO_ERROR, glGetError())
                checked = true
            } finally {
                sky.dispose(); scattered.dispose()
            }
        }
        rendered.error?.let { throw it }
        assertTrue("the bake ran inside the GL context", checked)
    }
}
