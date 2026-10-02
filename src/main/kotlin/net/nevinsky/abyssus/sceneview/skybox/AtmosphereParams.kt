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

package net.nevinsky.abyssus.sceneview.skybox

/**
 * The physical constants of a single-scattering atmosphere, in metres. Rayleigh scatters air (blue sky, red
 * sunsets), Mie scatters haze (the halo around the sun, [mieG] its forward bias); each thins out exponentially with a
 * scale height. Plain data so the model and the shader are fed the same numbers.
 */
data class AtmosphereParams(
    val planetRadius: Float,
    val atmosphereRadius: Float,
    val betaRayleigh: List<Float>,
    val betaMie: Float,
    val heightRayleigh: Float,
    val heightMie: Float,
    val mieG: Float,
    val sunIntensity: Float,
) {
    companion object {
        /** Earth: the Nishita / Scratchapixel values. */
        val EARTH = AtmosphereParams(
            planetRadius = 6360e3f,
            atmosphereRadius = 6420e3f,
            betaRayleigh = listOf(5.8e-6f, 13.5e-6f, 33.1e-6f),
            betaMie = 21e-6f,
            heightRayleigh = 8e3f,
            heightMie = 1.2e3f,
            mieG = 0.76f,
            sunIntensity = 20f,
        )
    }
}
