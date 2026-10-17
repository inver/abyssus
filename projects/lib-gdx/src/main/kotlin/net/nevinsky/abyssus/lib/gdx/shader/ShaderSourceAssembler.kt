/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

/**
 * Expands `#pragma include "name"` lines of a GLSL source in place. An unexpanded pragma is ignored by a GLSL compiler, so a
 * source that is never assembled still compiles when it does not need the included code.
 *
 * Expanding in place keeps the `#define`s and declarations above the directive ahead of the included functions. Each
 * expansion is wrapped in `// begin include` / `// end include` comments naming the resource, so a compiler log line can be
 * traced to its source. [resolve] returns the text of an include name, or null when there is none; a missing include throws
 * with [label] and the line, so the failure names the shader that asked for it. Pure text, no GL.
 */
class ShaderSourceAssembler(private val resolve: (String) -> String?) {
    fun assemble(source: String, label: String): String {
        if (!source.contains("#pragma include")) {
            return source
        }
        val out = StringBuilder(source.length)
        source.lines().forEachIndexed { index, line ->
            val name = INCLUDE.matchEntire(line.trim())?.groupValues?.get(1)
            if (name == null) {
                out.append(line).append('\n')
            } else {
                val text = checkNotNull(resolve(name)) { "Shader '$label' line ${index + 1}: missing include '$name'" }
                out.append("// begin include ").append(name).append('\n')
                out.append(text.trimEnd()).append('\n')
                out.append("// end include ").append(name).append('\n')
            }
        }
        return out.toString().removeSuffix("\n").let { if (source.endsWith("\n")) it + "\n" else it }
    }

    private companion object {
        val INCLUDE = Regex("""#pragma\s+include\s+"([^"]+)"""")
    }
}
