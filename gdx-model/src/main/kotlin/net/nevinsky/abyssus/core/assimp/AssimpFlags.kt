/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assimp

import org.lwjgl.assimp.Assimp

/**
 * Post-processing flags for importing a scene.
 *
 *
 * Deliberately excludes `aiProcess_PreTransformVertices` (destroys node hierarchy, bones and animations) and
 * `aiProcess_FixInfacingNormals` (inverts normals of open meshes). `aiProcess_GenSmoothNormals` only
 * generates normals for meshes that have none.
 */
object AssimpFlags {
    val DEFAULT: Int = (Assimp.aiProcess_Triangulate
            or Assimp.aiProcess_JoinIdenticalVertices
            or Assimp.aiProcess_SortByPType
            or Assimp.aiProcess_GenSmoothNormals
            or Assimp.aiProcess_CalcTangentSpace
            or Assimp.aiProcess_LimitBoneWeights
            or Assimp.aiProcess_ImproveCacheLocality
            or Assimp.aiProcess_GenBoundingBoxes)
}
