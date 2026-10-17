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

    /** The folder of GLSL that sources pull in with `#pragma include "name"` (see [ShaderSourceAssembler]). */
    const val SHARED_ROOT: String = "/shader/shared"

    /** The shadow atlas and directional visibility functions every lit fragment shader shares. */
    const val SHADOW_VISIBILITY: String = "shadow_visibility.glsl"

    /** Expands the `#pragma include` lines of GLSL [source] from [SHARED_ROOT]; [label] names the shader in a failure. */
    fun assemble(source: String, label: String): String = ShaderSourceAssembler(::readShared).assemble(source, label)

    /** The resource [resource] with its includes expanded. */
    fun readAssembled(resource: String): String = assemble(read(resource), resource)

    private fun readShared(name: String): String? =
        ShaderSources::class.java.getResourceAsStream("$SHARED_ROOT/$name")?.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }

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
