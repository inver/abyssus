/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.sky.cube

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.core.io.FileLoader
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.loading.ShaderSource
import net.nevinsky.abyssus.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.core.loader.Pixmaps

/** Skybox assets: the six face images decoded off the GL thread, then uploaded as one cube map. */
class SkyboxLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
    private val shaders: ShaderSource
) : AssetLoader<PreparedSkybox, SkyboxCube> {
    override fun loadPrepared(meta: AssetMeta<Any>): PreparedSkybox {
        val additional = meta.typedAdditional<SkyboxMeta>()
        val faces = ArrayList<Pixmap>(6)
        try {
            faces += Pixmaps.load(fileLoader.loadAssetFile(meta.name, additional.back))
            faces += Pixmaps.load(fileLoader.loadAssetFile(meta.name, additional.front))
            faces += Pixmaps.load(fileLoader.loadAssetFile(meta.name, additional.left))
            faces += Pixmaps.load(fileLoader.loadAssetFile(meta.name, additional.right))
            faces += Pixmaps.load(fileLoader.loadAssetFile(meta.name, additional.bottom))
            faces += Pixmaps.load(fileLoader.loadAssetFile(meta.name, additional.top))
        } catch (e: Throwable) {
            faces.forEach(Pixmap::dispose)
            throw e
        }
        return PreparedSkybox(faces, additional.shader ?: "skybox")
    }

    override fun prepare(name: String): PreparedSkybox? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        return loadPrepared(meta)
    }

    override fun build(prepared: PreparedSkybox, assets: BuiltAssets) = SkyboxCube(prepared, shaders.program(prepared.shaderName))

    override fun discard(prepared: PreparedSkybox) = prepared.dispose()
}

/** The six decoded faces of a skybox asset, in libGDX cube map order. Released when built or discarded. */
class PreparedSkybox(val faces: List<Pixmap>, val shaderName: String) {
    fun dispose() = faces.forEach(Pixmap::dispose)
}