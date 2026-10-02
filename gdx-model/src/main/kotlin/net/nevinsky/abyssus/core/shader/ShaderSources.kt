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
