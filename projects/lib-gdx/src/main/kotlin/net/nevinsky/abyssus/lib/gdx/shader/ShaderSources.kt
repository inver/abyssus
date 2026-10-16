/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets

/**
 * The GLSL sources bundled with this library. The model shaders of the `g3d` package read them; instancing is one `mat4 a_instance`.
 */
object ShaderSources {
    const val DEFAULT_VERTEX: String = "/shader/default.vertex.glsl"
    const val DEFAULT_FRAGMENT: String = "/shader/default.fragment.glsl"
    const val PBR_FRAGMENT: String = "/shader/pbr.fragment.glsl"


    fun read(resource: String): String {
        try {
            ShaderSources::class.java.getResourceAsStream(resource).use { `in` ->
                checkNotNull(`in`) { "Shader resource not found: " + resource }
                return String(`in`.readAllBytes(), StandardCharsets.UTF_8)
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }
}
