/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderSourcesTest {
    @Test
    fun everyBundledShaderIsReadable() {
        for (resource in listOf(ShaderSources.DEFAULT_VERTEX, ShaderSources.DEFAULT_FRAGMENT, ShaderSources.PBR_FRAGMENT)) {
            assertTrue("$resource is empty", ShaderSources.read(resource).isNotBlank())
        }
    }

    @Test
    fun bothFragmentShadersPullInTheOutputTransformAndItsFunctionsAreGatedByTheFlag() {
        for (resource in listOf(ShaderSources.DEFAULT_FRAGMENT, ShaderSources.PBR_FRAGMENT)) {
            val source = ShaderSources.readAssembled(resource)
            assertFalse("$resource kept an include line", source.contains("#pragma include"))
            assertTrue("$resource has no transform", source.contains("vec3 outputTransform(vec3 linear)"))
            assertTrue("$resource ends in the transform", source.contains("gl_FragColor.rgb = outputTransform(gl_FragColor.rgb);"))
        }
        val include = ShaderSources.assemble("#pragma include \"output_transform.glsl\"\n", "test")
        assertTrue(include.contains("// The display transform of a host that lights in linear space"))
        // without the flag the only definition is the plain texture fetch
        assertTrue(include.contains("#else\n#define sampleColor texture2D\n#endif"))
    }

    @Test
    fun instancingIsOneMat4Attribute() {
        val vertex = ShaderSources.read(ShaderSources.DEFAULT_VERTEX)
        assertTrue(vertex.contains("attribute mat4 a_instance;"))
        assertTrue(vertex.contains("#define u_worldTrans a_instance"))
        assertFalse(vertex.contains("a_instance0"))
    }
}
