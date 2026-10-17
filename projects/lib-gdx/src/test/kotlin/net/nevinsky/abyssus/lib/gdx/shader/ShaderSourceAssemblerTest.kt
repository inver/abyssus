/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShaderSourceAssemblerTest {
    private val assembler = ShaderSourceAssembler { if (it == "shared.glsl") "float shared() { return 1.0; }\n" else null }

    @Test
    fun sourceWithoutAnIncludeIsReturnedUnchanged() {
        val source = "#define a\nvoid main() {}\n"
        assertEquals(source, assembler.assemble(source, "plain"))
    }

    @Test
    fun definesAndDeclarationsAboveTheDirectiveStayAheadOfTheIncludedFunctions() {
        val out = assembler.assemble("#define shadowAtlasFlag\nuniform float u;\n#pragma include \"shared.glsl\"\nvoid main() {}", "s")
        val define = out.indexOf("#define shadowAtlasFlag")
        val uniform = out.indexOf("uniform float u;")
        val function = out.indexOf("float shared()")
        val main = out.indexOf("void main()")
        assertTrue(define in 0 until uniform && uniform < function && function < main)
        assertFalse(out.contains("#pragma include"))
    }

    @Test
    fun anExpansionIsLabelledWithItsResource() {
        val lines = assembler.assemble("#pragma include \"shared.glsl\"", "s").lines()
        assertEquals(listOf("// begin include shared.glsl", "float shared() { return 1.0; }", "// end include shared.glsl"), lines)
    }

    @Test
    fun aMissingIncludeNamesTheShaderTheLineAndTheInclude() {
        try {
            assembler.assemble("void a() {}\n#pragma include \"nope.glsl\"\n", "my.frag")
            fail("expected a failure")
        } catch (e: IllegalStateException) {
            assertTrue(e.message, e.message!!.contains("my.frag") && e.message!!.contains("line 2") && e.message!!.contains("nope.glsl"))
        }
    }
}
