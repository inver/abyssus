package net.nevinsky.abyssus.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderSourceTest {
    private val shaders = ShaderSource("/shader/test", ShaderSourceTest::class.java)

    @Test
    fun fragmentsAreJoinedInOrder() {
        val joined = shaders.fragment("common.glsl", "main.frag").replace("\r\n", "\n")
        assertEquals("float a() { return 1.0; }\n\nvoid main() { gl_FragColor = vec4(a()); }\n", joined)
    }

    @Test
    fun aMissingFileNamesThePath() {
        val error = runCatching { shaders.read("nope.vert") }.exceptionOrNull()
        assertTrue("got $error", error is IllegalStateException && error.message == "Missing shader /shader/test/nope.vert")
    }
}
