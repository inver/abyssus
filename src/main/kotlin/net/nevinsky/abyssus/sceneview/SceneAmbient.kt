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

package net.nevinsky.abyssus.sceneview

/** What lights the scene's content besides its light entities: the ambient color, or a built HDR sky that replaces it. */
sealed interface SceneAmbient<out E> {
    /** The scene's ambient color; null when its ambient light is disabled. */
    data class Color(val rgba: Rgba?) : SceneAmbient<Nothing>

    /** A built HDR sky's [environment]. */
    data class Sky<E>(val environment: E) : SceneAmbient<E>

    companion object {
        /**
         * The sky when the scene's enabled, named [skybox] is an HDR sky whose environment is built ([builtSky] returns
         * it), whether or not the ambient light is enabled; otherwise the [ambient] color, as before HDR skies.
         */
        fun <E : Any> of(skybox: String?, ambient: Rgba?, builtSky: (String) -> E?): SceneAmbient<E> =
            skybox?.let(builtSky)?.let { Sky(it) } ?: Color(ambient)
    }
}
