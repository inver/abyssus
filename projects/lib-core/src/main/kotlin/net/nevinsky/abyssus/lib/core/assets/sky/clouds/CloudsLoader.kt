/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.Prepared

private const val GL_R8 = 0x8229
private const val GL_RED = 0x1903
internal const val GL_TEXTURE_3D = 0x806F
private const val GL_TEXTURE_WRAP_R = 0x8072

/**
 * Loads `CLOUDS` assets: [prepare] reads the bands and technique (bad bands are skipped and logged to [log]) and, when
 * there is a band, makes the 3D noise; [build] uploads the noise. Skies name the asset by `uuid` and load it as a
 * dependency.
 */
class CloudsLoader(
    private val metaLoader: AssetMetaLoader,
    private val noise: CloudNoiseGenerator = CloudNoiseGenerator(),
) : AssetLoader<Unit, PreparedClouds, Clouds> {
    override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, PreparedClouds> {
        val additional = meta.typedAdditional<CloudMeta>()
        return Prepared(PreparedClouds(meta.name, additional, if (additional.visible) noise.generate() else null))
    }

    override fun prepare(name: String): Prepared<Unit, PreparedClouds>? =
        metaLoader.loadBaseMeta(name)?.let(::loadPrepared)

    override fun build(staged: PreparedClouds, assets: BuiltAssets) =
        Clouds(staged.name, staged.meta, staged.noise)

    override fun discard(model: Unit) = Unit
}

/** A `CLOUDS` asset read from disk: its [meta] and the volumetric technique's [noise]; no GL resources. */
data class PreparedClouds(val name: String, val meta: CloudMeta, val noise: CloudNoise?)

/**
 * A built `CLOUDS` asset: the weather ([settings]) skies draw, and the volumetric technique's 3D noise textures
 * ([baseNoise], [detailNoise]; 0 when they could not be created, the reason in [noiseFailure]). Skies read it from the
 * asset storage on every draw, like a terrain its splat textures, and never own or dispose it. GL thread only.
 */
class Clouds(val name: String, val settings: CloudMeta, noise: CloudNoise?) : Disposable {
    var baseNoise = 0
        private set
    var detailNoise = 0
        private set

    /** Why there are no noise textures; null when they were created. */
    var noiseFailure: String? = null
        private set

    init {
        try {
            checkNotNull(noise) { "the volumetric noise was not prepared" }
            val gl30 = checkNotNull(Gdx.gl30) { "3D noise textures need OpenGL 3" }
            baseNoise = volume(gl30, noise.baseSize, noise.base)
            detailNoise = volume(gl30, noise.detailSize, noise.detail)
        } catch (e: Exception) {
            deleteNoise()
            noiseFailure = e.message ?: e.toString()
        }
    }

    private fun volume(gl30: GL30, size: Int, texels: ByteArray): Int {
        val gl = Gdx.gl
        val texture = gl.glGenTexture()
        gl.glBindTexture(GL_TEXTURE_3D, texture)
        val buffer = BufferUtils.newByteBuffer(texels.size)
        buffer.put(texels).flip()
        gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 1)
        gl30.glTexImage3D(GL_TEXTURE_3D, 0, GL_R8, size, size, size, 0, GL_RED, GL20.GL_UNSIGNED_BYTE, buffer)
        gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 4)
        gl.glTexParameteri(GL_TEXTURE_3D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL_TEXTURE_3D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL_TEXTURE_3D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_REPEAT)
        gl.glTexParameteri(GL_TEXTURE_3D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_REPEAT)
        gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL20.GL_REPEAT)
        gl.glBindTexture(GL_TEXTURE_3D, 0)
        val error = gl.glGetError()
        if (error != GL20.GL_NO_ERROR) {
            gl.glDeleteTexture(texture)
            error("the volumetric noise texture could not be created (GL error 0x${error.toString(16)})")
        }
        return texture
    }

    private fun deleteNoise() {
        val gl = Gdx.gl ?: return
        if (baseNoise != 0) gl.glDeleteTexture(baseNoise)
        if (detailNoise != 0) gl.glDeleteTexture(detailNoise)
        baseNoise = 0
        detailNoise = 0
    }

    override fun dispose() = deleteNoise()
}

