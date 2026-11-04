/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import java.lang.reflect.Proxy
import java.nio.IntBuffer
import net.nevinsky.abyssus.lib.render.shadow.DepthSubmission
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Headless (a GL that does nothing): design decision D12 of `improve-control-line-performance-stability`. */
class DepthSubmissionTest {
    private var gl: GL20? = null

    @Before
    fun stubGl() {
        gl = Gdx.gl
        Gdx.gl =
            Proxy.newProxyInstance(GL20::class.java.classLoader, arrayOf(GL20::class.java)) { _, m, args ->
                if (m.name == "glGetIntegerv") {
                    (args[1] as IntBuffer).put(0, 16)
                }
                when (m.returnType) {
                    java.lang.Boolean.TYPE -> false
                    Integer.TYPE -> 0
                    else -> null
                }
            } as GL20
    }

    @After
    fun restoreGl() {
        Gdx.gl = gl
    }

    /** A depth shader that keeps what it was asked to do. */
    private class Counting(val name: String, val log: MutableList<String>) : Shader {
        var begins = 0

        override fun init() = Unit

        override fun compareTo(other: Shader?) = 0

        override fun canRender(instance: Renderable?) = true

        override fun begin(camera: Camera?, context: RenderContext?) {
            begins++
            log += "begin $name"
        }

        override fun render(renderable: Renderable) {
            log += "render $name ${renderable.meshPart.id}"
        }

        override fun end() {
            log += "end $name"
        }

        override fun dispose() = Unit
    }

    private fun caster(id: String) = Renderable().also { it.meshPart.id = id }

    @Test
    fun eachShaderIsBegunOncePerSubmissionAndCastersKeepTheirOrderWithinIt() {
        val log = ArrayList<String>()
        val a = Counting("a", log)
        val b = Counting("b", log)
        val casters = listOf(caster("1"), caster("2"), caster("3"), caster("4"), caster("5"), caster("6"))
        // interleaved: a b a b a, and one without a depth shader
        val of = mapOf("1" to a, "2" to b, "3" to a, "4" to b, "5" to a)
        val submission = DepthSubmission { of[it.meshPart.id] }

        submission.submit(OrthographicCamera(), casters)

        assertEquals(1, a.begins)
        assertEquals(1, b.begins)
        assertEquals(2, submission.lastGroups)
        assertEquals(
            listOf(
                "begin a",
                "render a 1",
                "render a 3",
                "render a 5",
                "end a",
                "begin b",
                "render b 2",
                "render b 4",
                "end b",
            ),
            log,
        )
    }

    @Test
    fun theNextSubmissionStartsEmpty() {
        val log = ArrayList<String>()
        val a = Counting("a", log)
        val submission = DepthSubmission { a }
        submission.submit(OrthographicCamera(), listOf(caster("1"), caster("2")))
        log.clear()
        submission.submit(OrthographicCamera(), listOf(caster("3")))
        assertEquals(listOf("begin a", "render a 3", "end a"), log)
    }

    @Test
    fun aFailingShaderLeavesNothingForTheNextSubmission() {
        val log = ArrayList<String>()
        val a = Counting("a", log)
        val broken =
            object : Shader by Counting("x", log) {
                override fun render(renderable: Renderable) = throw IllegalStateException("broken")
            }
        val shaderOf = mutableMapOf<String, Shader>("1" to broken, "2" to a)
        val submission = DepthSubmission { shaderOf[it.meshPart.id] }
        assertThrows(IllegalStateException::class.java) {
            submission.submit(OrthographicCamera(), listOf(caster("1"), caster("2")))
        }
        log.clear()
        submission.submit(OrthographicCamera(), listOf(caster("2")))
        assertEquals(listOf("begin a", "render a 2", "end a"), log)
    }
}
