/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import java.lang.reflect.Proxy
import java.nio.FloatBuffer
import java.nio.IntBuffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Headless (a stub GL that compiles anything and records uniform calls): design decision D14 of
 * `improve-control-line-performance-stability`. The same atlas bound twice uploads once.
 */
class ShadowAtlasBindingsTest {
    private val names = listOf("u_shadowAtlas", "u_shadowData", "u_shadowEnabled", "u_shadowTexel")

    /** Uniform calls by location, in order: (location, the floats or ints given). */
    private val uniforms = ArrayList<Pair<Int, List<Float>>>()
    private var gl: GL20? = null
    private var app: Application? = null

    @Before
    fun stubGl() {
        gl = Gdx.gl20
        app = Gdx.app
        val stub =
            Proxy.newProxyInstance(GL20::class.java.classLoader, arrayOf(GL20::class.java)) { _, m, args ->
                when (m.name) {
                    "glCreateShader",
                    "glCreateProgram" -> 1
                    "glGetShaderiv",
                    "glGetProgramiv" -> {
                        val out = args[2] as IntBuffer
                        out.put(
                            0,
                            if (args[1] == GL20.GL_COMPILE_STATUS || args[1] == GL20.GL_LINK_STATUS) {
                                1
                            } else {
                                0
                            },
                        )
                        null
                    }
                    "glGetShaderInfoLog",
                    "glGetProgramInfoLog" -> ""
                    "glGetUniformLocation" -> names.indexOf(args[1] as String)
                    "glGetAttribLocation" -> -1
                    "glUniform1i" -> {
                        uniforms += (args[0] as Int) to listOf((args[1] as Int).toFloat())
                        null
                    }
                    "glUniform1f" -> {
                        uniforms += (args[0] as Int) to listOf(args[1] as Float)
                        null
                    }
                    "glUniform2f" -> {
                        uniforms += (args[0] as Int) to listOf(args[1] as Float, args[2] as Float)
                        null
                    }
                    "glUniform1fv",
                    "glUniform3fv",
                    "glUniform4fv" -> {
                        val v = args[2]
                        uniforms +=
                            (args[0] as Int) to
                                if (v is FloatArray) {
                                    v.toList().drop(args[3] as Int)
                                } else {
                                    floats(v as FloatBuffer)
                                }
                        null
                    }
                    "glUniformMatrix4fv" -> {
                        val v = args[3]
                        uniforms +=
                            (args[0] as Int) to
                                if (v is FloatArray) {
                                    v.toList().drop(args[4] as Int)
                                } else {
                                    floats(v as FloatBuffer)
                                }
                        null
                    }
                    else ->
                        when (m.returnType) {
                            java.lang.Boolean.TYPE -> false
                            Integer.TYPE -> 0
                            else -> null
                        }
                }
            } as GL20
        Gdx.gl20 = stub
        Gdx.gl = stub
        // ShaderProgram keeps its programs per application, in a map: the stub needs identity equality
        Gdx.app =
            Proxy.newProxyInstance(Application::class.java.classLoader, arrayOf(Application::class.java)) {
                proxy,
                m,
                args ->
                when (m.name) {
                    "equals" -> proxy === args[0]
                    "hashCode" -> System.identityHashCode(proxy)
                    else ->
                        if (m.returnType == Integer.TYPE) {
                            0
                        } else {
                            null
                        }
                }
            } as Application
    }

    @After
    fun restoreGl() {
        Gdx.gl20 = gl
        Gdx.gl = gl
        Gdx.app = app
    }

    private fun floats(b: FloatBuffer): List<Float> = (b.position() until b.limit()).map(b::get)

    private val texture =
        object : GLTexture(GL20.GL_TEXTURE_2D, 7) {
            override fun getWidth() = 2048

            override fun getHeight() = 2048

            override fun getDepth() = 0

            override fun isManaged() = false

            override fun reload() = Unit
        }

    private val sun = DirectionalLight()

    private fun atlas(x: Float) =
        ShadowAtlasAttribute(
            texture,
            listOf(
                ShadowLightRecord(
                    "sun",
                    ShadowLightKind.DIRECTIONAL,
                    sun,
                    listOf(ShadowAtlasView(Matrix4().setToTranslation(x, 0f, 0f), Vector4(0f, 0f, 1f, 1f))),
                    Vector3(1f, 2f, 3f),
                    1f,
                    0.002f,
                )
            ),
        )

    private fun program() = ShaderProgram("void main() {}", "void main() {}")

    private fun location(name: String) = names.indexOf(name)

    private fun at(bindings: ShadowAtlasBindings, row: Int, column: Int, component: Int = 0) =
        bindings.data[(row * SHADOW_DATA_WIDTH + column) * 4 + component]

    private fun entry(bindings: ShadowAtlasBindings, tableRow: Int, i: Int) =
        at(bindings, tableRow + i / SHADOW_DATA_WIDTH, i % SHADOW_DATA_WIDTH)

    private fun bind(
        bindings: ShadowAtlasBindings,
        program: ShaderProgram,
        atlas: ShadowAtlasAttribute?,
        unit: Int = 3,
        dataUnit: Int = 4,
    ) =
        bindings.bind(
            program,
            atlas,
            atlas?.records?.take(1).orEmpty(),
            emptyList(),
            emptyList(),
            unit,
            dataUnit,
        )

    @Test
    fun theSameAtlasBoundTwiceUploadsOnce() {
        val bindings = ShadowAtlasBindings()
        val program = program()
        val atlas = atlas(5f)
        bind(bindings, program, atlas)
        val first = uniforms.size
        assertTrue("the first bind uploads", first > 0)
        assertEquals(1L, bindings.uploads)

        bind(bindings, program, atlas)
        assertEquals("no upload the second time", first, uniforms.size)
        assertEquals(1L, bindings.uploads)
    }

    @Test
    fun theUploadHoldsTheAtlasValues() {
        val bindings = ShadowAtlasBindings()
        val atlas = atlas(5f)
        bind(bindings, program(), atlas)
        fun last(name: String) = uniforms.last { it.first == location(name) }.second
        assertEquals(listOf(3f), last("u_shadowAtlas"))
        assertEquals(listOf(4f), last("u_shadowData"))
        assertEquals(listOf(1f), last("u_shadowEnabled"))
        assertEquals(listOf(1f / 2048, 1f / 2048), last("u_shadowTexel"))
        assertEquals("the view's matrix", 5f, bindings.data[12], 0f)
        assertEquals(listOf(0f, 0f, 1f, 1f), (0..3).map { at(bindings, 0, 4, it) })
        assertEquals(listOf(1f, 2f, 3f, 1f), (0..3).map { at(bindings, 0, 5, it) })
        assertEquals(0.002f, at(bindings, 0, 6), 0f)
        assertEquals("the sun's view starts at 0", 0f, entry(bindings, SHADOW_DIRECTIONAL_TABLE_ROW, 0), 0f)
        assertEquals(-1f, entry(bindings, SHADOW_DIRECTIONAL_TABLE_ROW, 1), 0f)
        assertEquals(-1f, entry(bindings, SHADOW_POINT_TABLE_ROW, 0), 0f)
        assertEquals(-1f, entry(bindings, SHADOW_SPOT_TABLE_ROW, SHADOW_MAX_LOCAL - 1), 0f)
    }

    @Test
    fun aNewAtlasAnotherProgramOrUnitUploadsAgain() {
        val bindings = ShadowAtlasBindings()
        val program = program()
        val a = atlas(5f)
        bind(bindings, program, a)
        val b = atlas(9f)
        bind(bindings, program, b)
        assertEquals("the light camera moved: a new atlas revision", 2L, bindings.uploads)
        assertEquals(9f, bindings.data[12], 0f)

        bind(bindings, program, b, unit = 4, dataUnit = 5)
        assertEquals("another texture unit", 3L, bindings.uploads)

        val other = program()
        bind(bindings, other, b, unit = 4, dataUnit = 5)
        assertEquals("a program that never held it", 4L, bindings.uploads)
        bind(bindings, program, b, unit = 4, dataUnit = 5)
        assertEquals("each program keeps what it holds", 4L, bindings.uploads)
    }

    @Test
    fun noAtlasDisablesShadows() {
        val bindings = ShadowAtlasBindings()
        bind(bindings, program(), null, unit = 2)
        assertEquals(listOf(0f), uniforms.last { it.first == location("u_shadowEnabled") }.second)
        assertEquals(-1f, entry(bindings, SHADOW_DIRECTIONAL_TABLE_ROW, 0), 0f)
    }

    @Test
    fun aSceneOfTwoHundredFiftySixPointLightsKeepsEveryLightsSixViews() {
        val lights = (0 until SHADOW_MAX_LOCAL).map { PointLight() }
        val records =
            lights.mapIndexed { i, light ->
                ShadowLightRecord(
                    "p$i",
                    ShadowLightKind.POINT,
                    light,
                    List(6) { face ->
                        ShadowAtlasView(
                            Matrix4().setToTranslation(i.toFloat(), face.toFloat(), 0f),
                            Vector4(0f, 0f, 1f, 1f),
                        )
                    },
                    Vector3(i.toFloat(), 0f, 0f),
                    10f,
                )
            }
        val atlas = ShadowAtlasAttribute(texture, records)
        val bindings = ShadowAtlasBindings()
        bindings.bind(program(), atlas, emptyList(), atlas.records, emptyList(), 3, 4)
        for (i in 0 until SHADOW_MAX_LOCAL) {
            assertEquals("first view of light $i", (i * 6).toFloat(), entry(bindings, SHADOW_POINT_TABLE_ROW, i), 0f)
        }
        val lastView = SHADOW_MAX_LOCAL * 6 - 1
        assertEquals("the last face of the last light", 255f, at(bindings, lastView, 5), 0f)
        assertEquals("the matrix of that view", 5f, at(bindings, lastView, 3, 1), 0f)
        assertEquals(10f, at(bindings, lastView, 5, 3), 0f)
        assertTrue("the views stay clear of the tables", lastView < SHADOW_VIEW_ROWS)
    }
}
