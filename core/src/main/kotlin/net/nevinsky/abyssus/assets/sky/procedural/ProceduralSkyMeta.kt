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

package net.nevinsky.abyssus.assets.sky.procedural

import net.nevinsky.abyssus.assets.files.MetaBase
import net.nevinsky.abyssus.assets.files.MetaType
import java.util.*

class ProceduralSkyMeta(
    version: Int,
    lastModified: Long,
    type: MetaType,
    additional: ProceduralSkyAdditional,
    uuid: UUID? = null
) : MetaBase<ProceduralSkyAdditional>(version, lastModified, type, additional, uuid)

/**
 * The `additional` block of a `SKYBOX_PROCEDURAL` asset: the two GLSL files in the asset folder and the atmosphere
 * parameters. Every parameter is optional; [params] fills the gaps with the defaults of [AtmosphereParams].
 */
class ProceduralSkyAdditional(
    val vertex: String? = null,
    val fragment: String? = null,
    val planetRadius: Float? = null,
    val atmosphereRadius: Float? = null,
    val betaRayleigh: List<Float>? = null,
    val betaMie: Float? = null,
    val heightRayleigh: Float? = null,
    val heightMie: Float? = null,
    val mieG: Float? = null,
    val sunIntensity: Float? = null,
) {
    val params: AtmosphereParams
        get() {
            val d = AtmosphereParams()
            return AtmosphereParams(
                planetRadius = planetRadius ?: d.planetRadius,
                atmosphereRadius = atmosphereRadius ?: d.atmosphereRadius,
                betaRayleigh = betaRayleigh?.takeIf { it.size == 3 } ?: d.betaRayleigh,
                betaMie = betaMie ?: d.betaMie,
                heightRayleigh = heightRayleigh ?: d.heightRayleigh,
                heightMie = heightMie ?: d.heightMie,
                mieG = mieG ?: d.mieG,
                sunIntensity = sunIntensity ?: d.sunIntensity,
            )
        }
}

/** The atmosphere of a procedural sky; the defaults are Earth's (the Nishita / Scratchapixel values). */
data class AtmosphereParams(
    val planetRadius: Float = 6360e3f,
    val atmosphereRadius: Float = 6420e3f,
    val betaRayleigh: List<Float> = listOf(5.8e-6f, 13.5e-6f, 33.1e-6f),
    val betaMie: Float = 21e-6f,
    val heightRayleigh: Float = 8e3f,
    val heightMie: Float = 1.2e3f,
    val mieG: Float = 0.76f,
    val sunIntensity: Float = 20f,
)
