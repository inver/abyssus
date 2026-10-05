/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.cube

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.assets.ShaderSource
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.AssetMetaLoader
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.loader.Pixmaps

/** Skybox assets: the six face images decoded off the GL thread, then uploaded as one cube map. */
class SkyboxLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
    private val shaders: ShaderSource
) :
    AssetLoader<PreparedSkybox, SkyboxCube> {
    /**
     * The native cube map maps from (back, front, left, right, bottom, top) as (+X, -X, +Y, -Y, +Z, -Z); the same
     * order is kept so a skybox looks here as it does in the editor.
     */
    override fun prepare(files: AssetFiles, name: String): PreparedSkybox? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        val additional = meta.typedAdditional<SkyboxMeta>()
        val faces = ArrayList<Pixmap>(6)
        try {
            faces += Pixmaps.load(fileLoader.loadFile(name, additional.back))
            faces += Pixmaps.load(fileLoader.loadFile(name, additional.front))
            faces += Pixmaps.load(fileLoader.loadFile(name, additional.left))
            faces += Pixmaps.load(fileLoader.loadFile(name, additional.right))
            faces += Pixmaps.load(fileLoader.loadFile(name, additional.bottom))
            faces += Pixmaps.load(fileLoader.loadFile(name, additional.top))
        } catch (e: Throwable) {
            faces.forEach(Pixmap::dispose)
            throw e
        }
        return PreparedSkybox(faces)
    }

    override fun build(prepared: PreparedSkybox) = SkyboxCube(prepared, shaders.program("skybox"))

    override fun discard(prepared: PreparedSkybox) = prepared.dispose()
}

/** The six decoded faces of a skybox asset, in libGDX cube map order. Released when built or discarded. */
class PreparedSkybox(val faces: List<Pixmap>) {
    fun dispose() = faces.forEach(Pixmap::dispose)
}