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
    fun instancingIsOneMat4Attribute() {
        val vertex = ShaderSources.read(ShaderSources.DEFAULT_VERTEX)
        assertTrue(vertex.contains("attribute mat4 a_instance;"))
        assertTrue(vertex.contains("#define u_worldTrans a_instance"))
        assertFalse(vertex.contains("a_instance0"))
    }
}
