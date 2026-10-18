/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FacedCubemapData
import com.badlogic.gdx.utils.GdxNativesLoader
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

class SolidTexturesTest {
    @Test
    fun whiteTextureAndBlackCubeReleaseTheirUploadPixmaps() {
        GdxNativesLoader.load()
        val uploads = mutableListOf<Int>()
        val previousGl = Gdx.gl
        val previousGl20 = Gdx.gl20
        val previousGraphics = Gdx.graphics
        Gdx.graphics = Proxy.newProxyInstance(Graphics::class.java.classLoader, arrayOf(Graphics::class.java)) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                else -> null
            }
        } as Graphics
        val gl = Proxy.newProxyInstance(GL20::class.java.classLoader, arrayOf(GL20::class.java)) { _, method, args ->
            when (method.name) {
                "glGenTexture" -> 1
                "glTexImage2D" -> {
                    assertEquals(1, args[3])
                    assertEquals(1, args[4])
                    assertEquals(GL20.GL_RGBA, args[6])
                    assertEquals(GL20.GL_UNSIGNED_BYTE, args[7])
                    val bytes = args[8] as ByteBuffer
                    uploads += (0..3).fold(0) { value, i -> (value shl 8) or (bytes.get(i).toInt() and 255) }
                    null
                }
                else -> when (method.returnType) {
                    Integer.TYPE -> 0
                    java.lang.Boolean.TYPE -> false
                    else -> null
                }
            }
        } as GL20
        Gdx.gl = gl
        Gdx.gl20 = gl
        try {
            val texture = newSolidTexture()
            try {
                assertTrue(texture.textureData.consumePixmap().isDisposed)
                assertEquals(Pixmap.Format.RGBA8888, texture.textureData.format)
            } finally {
                texture.dispose()
            }
            val cube = newSolidCubemap()
            try {
                val data = cube.cubemapData as FacedCubemapData
                for (side in Cubemap.CubemapSide.entries) {
                    assertTrue(data.getTextureData(side).consumePixmap().isDisposed)
                }
            } finally {
                cube.dispose()
            }
            assertEquals(listOf(-1) + List(6) { 0x000000ff }, uploads)
        } finally {
            Gdx.gl = previousGl
            Gdx.gl20 = previousGl20
            Gdx.graphics = previousGraphics
        }
    }
}
