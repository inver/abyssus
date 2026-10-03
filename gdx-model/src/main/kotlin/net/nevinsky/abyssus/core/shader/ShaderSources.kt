/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.shader

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets

/**
 * The GLSL sources bundled with this library. They are the canonical version: the bundled editor shaders are copies of
 * these files.
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
