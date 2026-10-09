/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.cube

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.lib.gdx.assets.AssetMeta
import net.nevinsky.abyssus.lib.gdx.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.Prepared
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.loader.Pixmaps

/** Skybox assets: the six face images decoded off the GL thread, then uploaded as one cube map. */
class SkyboxLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
    private val shaders: ShaderStorage
) : AssetLoader<Unit, PreparedSkybox, SkyboxCube> {
    override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, PreparedSkybox> = Prepared(read(meta))

    private fun read(meta: AssetMeta<Any>): PreparedSkybox {
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

    override fun prepare(name: String): Prepared<Unit, PreparedSkybox>? =
        metaLoader.loadBaseMeta(name)?.let(::loadPrepared)

    override fun build(staged: PreparedSkybox, assets: BuiltAssets) =
        SkyboxCube(staged, shaders.program(staged.shaderName))

    override fun discard(model: Unit) = Unit

    override fun discardStaged(staged: PreparedSkybox) = staged.dispose()
}

/** The six decoded faces of a skybox asset, in libGDX cube map order. Released when built or discarded. */
class PreparedSkybox(val faces: List<Pixmap>, val shaderName: String) {
    private var disposed = false

    /** Frees the faces unless the cube map took them already. Safe to call more than once. */
    fun dispose() {
        if (disposed) return
        disposed = true
        faces.forEach(Pixmap::dispose)
    }
}