/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.utils.BufferUtils

/**
 * A nearest-filtered RGBA32F texture of [texWidth] x [texHeight] texels that holds data a shader reads by row (the
 * shadow atlas's view and light tables, the local lights): uploaded whole when its data changed.
 */
class FloatDataTexture(private val texWidth: Int, private val texHeight: Int) :
    GLTexture(GL20.GL_TEXTURE_2D, Gdx.gl.glGenTexture()) {
    private val buffer = BufferUtils.newFloatBuffer(texWidth * texHeight * 4)

    /** False until the first upload and again after the context was lost, when the data must be written again. */
    var allocated = false
        private set

    /** Writes [data] ([texWidth] x [texHeight] x 4 floats) to the texture, on texture unit [unit]. */
    fun upload(unit: Int, data: FloatArray) {
        require(data.size == texWidth * texHeight * 4) { "unexpected data size ${data.size}" }
        val gl = Gdx.gl
        gl.glActiveTexture(GL20.GL_TEXTURE0 + unit)
        gl.glBindTexture(GL20.GL_TEXTURE_2D, glHandle)
        buffer.clear()
        buffer.put(data)
        buffer.flip()
        if (!allocated) {
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST)
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST)
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE)
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE)
            gl.glTexImage2D(
                GL20.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, texWidth, texHeight, 0, GL20.GL_RGBA,
                GL20.GL_FLOAT, buffer,
            )
            allocated = true
        } else {
            gl.glTexSubImage2D(
                GL20.GL_TEXTURE_2D, 0, 0, 0, texWidth, texHeight, GL20.GL_RGBA, GL20.GL_FLOAT, buffer,
            )
        }
    }

    override fun getWidth() = texWidth

    override fun getHeight() = texHeight

    override fun getDepth() = 0

    override fun isManaged() = false

    override fun reload() {
        allocated = false
    }
}
