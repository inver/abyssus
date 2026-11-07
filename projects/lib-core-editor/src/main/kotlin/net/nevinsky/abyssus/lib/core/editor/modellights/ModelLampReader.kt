/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modellights

import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.assets.model.selectMesh
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader

/** The source of the geometry Create Lights From Model works on. */
fun interface MaterialGeometrySource {
    /**
     * The materials with triangles of model asset [assetName] (only its mesh [meshId] when given); null if
     * unreadable.
     */
    fun read(assetName: String, meshId: String?): List<MaterialGeometry>?
}

/**
 * Reads the geometry of a model asset from the project's files. It parses the model file but touches no GL and decodes
 * no image, so it may run on any thread; call it off the UI thread.
 */
class ModelLampReader(
    files: FileLoader,
    metas: AssetMetaLoader,
    assimp: AssimpModelLoader = AssimpModelLoader(),
) : MaterialGeometrySource {
    private val loader = ModelLoader(metas, assimp, files, decodeTextures = false)

    override fun read(assetName: String, meshId: String?): List<MaterialGeometry>? {
        val prepared = runCatchingKeepingCancellation { loader.prepare(assetName) }.getOrNull() ?: return null
        return try {
            val data = prepared.staged.data
            if (meshId != null) {
                runCatchingKeepingCancellation { selectMesh(data, assetName, meshId) }.getOrNull() ?: return null
            }
            materialGeometries(data)
        } finally {
            loader.discardStaged(prepared.staged)
        }
    }
}
