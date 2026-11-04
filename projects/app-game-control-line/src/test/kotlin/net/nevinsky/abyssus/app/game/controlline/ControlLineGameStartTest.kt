/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.graphics.GL20
import java.lang.reflect.Proxy
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.event.Level

/** Spec `control-line-runtime-stability`, "The environment is logged at start". */
class ControlLineGameStartTest {
    /** A GL that answers only the two strings the start line reads. */
    private val gl =
        Proxy.newProxyInstance(GL20::class.java.classLoader, arrayOf(GL20::class.java)) { _, method, args ->
            when {
                method.name == "glGetString" && args[0] == GL20.GL_RENDERER -> "Test GPU 750"
                method.name == "glGetString" && args[0] == GL20.GL_VERSION -> "3.3.0 Test"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as GL20

    private val base = GameEnvironment("1.0.0", "TestOS 1 x64", "Vendor 21.0.9", 2048)

    @Test
    fun theStartLogHoldsOneEnvironmentLine() {
        val log = RecordingLogger()
        val environment = startEnvironment(base, gl, null, log)

        val lines = log.messages(Level.INFO)
        assertEquals(1, lines.size)
        val line = lines.single()
        listOf(
                "1.0.0",
                "TestOS 1 x64",
                "Vendor 21.0.9",
                "max heap 2048 MB",
                "Test GPU 750",
                "3.3.0 Test",
                "field view raster",
            )
            .forEach { assertTrue("'$it' in: $line", line.contains(it)) }
        assertEquals("the crash reporter gets the GL strings", "Test GPU 750", environment.glRenderer)
        assertEquals("raster", environment.fieldView)
    }

    @Test
    fun theOptInViewIsNamed() {
        val log = RecordingLogger()
        val ray =
            object : net.nevinsky.abyssus.app.game.controlline.render.FieldView {
                override val kind = "ray"

                override fun draw(
                    field: net.nevinsky.abyssus.app.game.controlline.render.FieldScene,
                    camera: com.badlogic.gdx.graphics.Camera,
                    lines: List<net.nevinsky.abyssus.app.game.controlline.render.LineSegment>,
                    hidden: com.badlogic.ashley.core.Entity?,
                    clips: Map<com.badlogic.ashley.core.Entity, String>,
                    seconds: Float,
                    cables: List<net.nevinsky.abyssus.app.game.controlline.render.Cable>,
                    poses: Map<com.badlogic.ashley.core.Entity, com.badlogic.gdx.math.Matrix4>,
                ) = Unit

                override fun resize(width: Float, height: Float) = Unit

                override fun dispose() = Unit
            }
        startEnvironment(base, gl, ray, log)
        assertTrue(log.messages(Level.INFO).single().contains("field view ray"))
    }
}
