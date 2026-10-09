/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assimp

import net.nevinsky.abyssus.lib.gdx.model.ModelData

/** An axis of a source file's coordinate system. */
enum class UpAxis { X, Y, Z }

/**
 * The unit and up axis a model file states about itself; `null` where it states nothing. Only reported, never
 * applied: see [AssimpModelDataLoader.loadScene].
 *
 * @param unitMetres metres per file unit
 */
data class StatedFrame(val unitMetres: Float?, val upAxis: UpAxis?) {
    companion object {
        val NONE = StatedFrame(null, null)
    }
}

/** Something of the source scene that a [ModelData] cannot hold, and was left out of it. */
data class LeftOut(val item: String, val kind: Kind) {
    enum class Kind {
        CAMERA,
        LIGHT,

        /** A mesh of points or lines. */
        NON_TRIANGLE_MESH,

        /** The morph targets of a mesh; the mesh keeps its base shape. */
        MORPH_TARGETS,
    }
}

/**
 * A loaded model: its [data], the frame the file states (FBX metadata only; see [ColladaAsset] for DAE files) and
 * what the scene had that [data] leaves out.
 */
class LoadedScene(val data: ModelData, val stated: StatedFrame, val leftOut: List<LeftOut>)
