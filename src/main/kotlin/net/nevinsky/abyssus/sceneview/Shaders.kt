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

import com.badlogic.gdx.graphics.glutils.ShaderProgram

/** The scene view's own GLSL programs, from `/shader/scene/<name>.vert` and `.frag`. GL context required. */
object Shaders {
    fun load(name: String): ShaderProgram {
        val program = ShaderProgram(source("$name.vert"), source("$name.frag"))
        check(program.isCompiled) { "Shader '$name' failed to compile: ${program.log}" }
        return program
    }

    private fun source(file: String): String =
        checkNotNull(Shaders::class.java.getResourceAsStream("/shader/scene/$file")) { "Missing shader /shader/scene/$file" }
            .use { String(it.readAllBytes(), Charsets.UTF_8) }
}
