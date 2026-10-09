/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.content.Rgba

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
