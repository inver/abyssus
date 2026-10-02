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

package net.nevinsky.abyssus.core.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture

/**
 * A [ModelMaterial] with metallic-roughness (glTF) properties. `null` values are not set on the material.
 * Textures use the `USAGE_*` constants of this class in addition to the ones of
 * [ModelTexture].
 */
class PbrModelMaterial : ModelMaterial() {
    enum class AlphaMode {
        OPAQUE, MASK, BLEND
    }

    var baseColor: Color? = null
    var metallic: Float? = null
    var roughness: Float? = null
    var doubleSided: Boolean = false
    var alphaMode: AlphaMode = AlphaMode.OPAQUE
    var alphaCutoff: Float = 0.5f

    companion object {
        const val USAGE_METALLIC_ROUGHNESS: Int = 100
        const val USAGE_OCCLUSION: Int = 101
    }
}
