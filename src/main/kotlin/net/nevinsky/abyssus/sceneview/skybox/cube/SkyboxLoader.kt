/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview.skybox.cube

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.core.loader.Pixmaps
import net.nevinsky.abyssus.sceneview.AssetLoader
import net.nevinsky.abyssus.sceneview.ProjectAssetFiles

/** Skybox assets: the six face images decoded off the GL thread, then uploaded as one cube map. */
class SkyboxLoader : AssetLoader<PreparedSkybox, SkyboxCube> {
    /**
     * Mundus builds the cube map from (back, front, left, right, bottom, top) as (+X, -X, +Y, -Y, +Z, -Z); the same
     * order is kept so a skybox looks here as it does in the editor.
     */
    override fun prepare(files: ProjectAssetFiles, name: String): PreparedSkybox? {
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

    override fun build(prepared: PreparedSkybox) = SkyboxCube(prepared)

    override fun discard(prepared: PreparedSkybox) = prepared.dispose()
}

/** The six decoded faces of a skybox asset, in libGDX cube map order. Released when built or discarded. */
class PreparedSkybox(val faces: List<Pixmap>) {
    fun dispose() = faces.forEach(Pixmap::dispose)
}