/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.cube

import net.nevinsky.abyssus.assets.ShaderSource
import net.nevinsky.abyssus.assets.loading.AssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.core.loader.Pixmaps
import net.nevinsky.abyssus.assets.files.AssetFiles

/** Skybox assets: the six face images decoded off the GL thread, then uploaded as one cube map. */
class SkyboxLoader(private val shaders: ShaderSource) : AssetLoader<PreparedSkybox, SkyboxCube> {
    /**
     * Mundus builds the cube map from (back, front, left, right, bottom, top) as (+X, -X, +Y, -Y, +Z, -Z); the same
     * order is kept so a skybox looks here as it does in the editor.
     */
    override fun prepare(files: AssetFiles, name: String): PreparedSkybox? {
        val assetData = files.loadAsset(SkyboxMeta::class.java, name) ?: return null
        val additional = assetData.meta.additional
        val faces = ArrayList<Pixmap>(6)
        try {
            for (f in listOf(
                files.loadFile(name, additional.back),
                files.loadFile(name, additional.front),
                files.loadFile(name, additional.left),
                files.loadFile(name, additional.right),
                files.loadFile(name, additional.bottom),
                files.loadFile(name, additional.top),
            )) {
                faces += Pixmaps.load(FileHandle(f))
            }
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