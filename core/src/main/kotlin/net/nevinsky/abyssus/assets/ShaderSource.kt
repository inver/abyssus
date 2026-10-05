/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets

import com.badlogic.gdx.graphics.glutils.ShaderProgram

/**
 * GLSL programs from the resource folder [root] (for example `/shader/sky`), read through [resources]' class loader.
 * Reading is plain IO; [program] compiles and needs a current GL context.
 */
//todo replace to ShaderLoader with loading default shaders if specified doesnt found
class ShaderSource(private val root: String, private val resources: Class<*> = ShaderSource::class.java) {
    /** The text of [file] under [root]; throws, naming the path, when it is missing. */
    fun read(file: String): String =
        checkNotNull(resources.getResourceAsStream("$root/$file")) { "Missing shader $root/$file" }
            .use { String(it.readAllBytes(), Charsets.UTF_8) }

    /** The files [fragment] joined in order (shared functions first), as one fragment shader. */
    fun fragment(vararg fragment: String): String = fragment.joinToString("\n", transform = ::read)

    /** The program of [name]`.vert` and [name]`.frag`. */
    fun program(name: String): ShaderProgram = program("$name.vert", "$name.frag")

    /** The program of the file [vertex] and the files [fragment] joined in order. Throws with the log when it does not compile. */
    fun program(vertex: String, vararg fragment: String): ShaderProgram {
        val program = ShaderProgram(read(vertex), fragment(*fragment))
        if (!program.isCompiled) {
            val log = program.log
            program.dispose()
            throw IllegalStateException("Shader '${fragment.last()}' failed to compile: $log")
        }
        return program
    }
}
