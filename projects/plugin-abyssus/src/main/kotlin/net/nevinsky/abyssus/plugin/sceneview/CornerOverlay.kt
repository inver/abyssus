/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import java.awt.image.BufferedImage

/** What to show in a [corner]: [image] (changing [version] means new content) at [margin] device pixels from the edge. */
internal class CornerSprite(val corner: Corner, val image: BufferedImage, val version: Long, val margin: Int)

/**
 * Draws Swing-painted images as blended quads in the corners of the view, in GL, after everything else. Call only with the
 * GL context current. A texture is uploaded again only when its sprite's version changes.
 */
internal class CornerOverlay(shaders: ShaderStorage) : Disposable {
    private val program = shaders.program("cornerquad")
    private val mesh = Mesh(
        false, 4, 0,
        VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
        VertexAttribute(Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
    )
    private val textures = HashMap<Corner, Pair<Long, Texture>>()

    fun draw(width: Int, height: Int, sprites: List<CornerSprite>) {
        if (sprites.isEmpty() || width <= 0 || height <= 0) return
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        program.bind()
        for (sprite in sprites) {
            val texture = textureOf(sprite)
            val rect = cornerRect(sprite.corner, width, height, sprite.image.width, sprite.image.height, sprite.margin)
            val left = 2f * rect.x / width - 1f
            val right = 2f * (rect.x + rect.width) / width - 1f
            val top = 1f - 2f * rect.y / height
            val bottom = 1f - 2f * (rect.y + rect.height) / height
            mesh.setVertices(floatArrayOf(
                left, top, 0f, 0f,
                left, bottom, 0f, 1f,
                right, top, 1f, 0f,
                right, bottom, 1f, 1f,
            ))
            texture.bind(0)
            program.setUniformi("u_texture", 0)
            mesh.render(program, GL20.GL_TRIANGLE_STRIP)
        }
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    private fun textureOf(sprite: CornerSprite): Texture {
        textures[sprite.corner]?.takeIf { it.first == sprite.version }?.let { return it.second }
        textures.remove(sprite.corner)?.second?.dispose()
        val texture = upload(sprite.image)
        textures[sprite.corner] = sprite.version to texture
        return texture
    }

    private fun upload(image: BufferedImage): Texture {
        val pixmap = Pixmap(image.width, image.height, Pixmap.Format.RGBA8888)
        try {
            val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            val buffer = pixmap.pixels
            buffer.clear()
            for (argb in pixels) {
                buffer.put((argb shr 16).toByte()).put((argb shr 8).toByte()).put(argb.toByte()).put((argb ushr 24).toByte())
            }
            buffer.flip()
            return Texture(pixmap).also {
                it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                it.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
            }
        } finally {
            pixmap.dispose()
        }
    }

    /** No GL calls: for a context that is already gone. */
    fun abandon() = textures.clear()

    override fun dispose() {
        textures.values.forEach { it.second.dispose() }
        textures.clear()
        mesh.dispose()
        program.dispose()
    }
}
