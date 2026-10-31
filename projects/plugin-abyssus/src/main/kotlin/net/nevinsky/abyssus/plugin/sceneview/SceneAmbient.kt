/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview


/**
 * What lights the scene's content besides its light entities: the ambient color, or a built HDR sky (or a procedural
 * sky with `lightsScene`) that replaces it.
 */
sealed interface SceneAmbient<out E> {
    /** The scene's ambient color; null when its ambient light is disabled. */
    data class Color(val rgba: com.badlogic.gdx.graphics.Color?) : SceneAmbient<Nothing>

    /** A built sky's lighting [environment]. */
    data class Sky<E>(val environment: E) : SceneAmbient<E>

    companion object {
        /**
         * The sky when the scene's enabled, named [skybox] is a lighting sky (HDR, or procedural with `lightsScene`) whose
         * environment is built ([builtSky] returns it), whether or not the ambient light is enabled; otherwise the
         * [ambient] color.
         */
        fun <E : Any> of(skybox: String?, ambient: com.badlogic.gdx.graphics.Color?, builtSky: (String) -> E?): SceneAmbient<E> =
            skybox?.let(builtSky)?.let { Sky(it) } ?: Color(ambient)
    }
}
