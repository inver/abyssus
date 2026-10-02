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

package net.nevinsky.abyssus.lib.assets.assimp

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
