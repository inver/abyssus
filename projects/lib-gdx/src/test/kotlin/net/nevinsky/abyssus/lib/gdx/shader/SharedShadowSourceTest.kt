/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The shadow atlas and directional visibility functions exist once, in `shadow_visibility.glsl`, and every lit fragment shader gets them from it. */
class SharedShadowSourceTest {
    private val functions = listOf("shadowCompare", "shadowBilinear", "shadowTileVisibility", "directionalVisibility")

    private fun definitions(source: String, function: String) = Regex("""(?m)^float $function\(""").findAll(source).count()

    @Test
    fun defaultAndPbrFragmentsContainEachSharedFunctionExactlyOnce() {
        for (resource in listOf(ShaderSources.DEFAULT_FRAGMENT, ShaderSources.PBR_FRAGMENT)) {
            val assembled = ShaderSources.readAssembled(resource)
            for (function in functions) assertEquals("$resource $function", 1, definitions(assembled, function))
            assertFalse(assembled.contains("#pragma include"))
        }
    }

    @Test
    fun theSourcesCarryNoCopyOfTheSharedFunctions() {
        for (resource in listOf(ShaderSources.DEFAULT_FRAGMENT, ShaderSources.PBR_FRAGMENT)) {
            val raw = ShaderSources.read(resource)
            for (function in functions) assertEquals("$resource $function", 0, definitions(raw, function))
        }
    }

    @Test
    fun theSharedResourceDeclaresTheAtlasUniformsAndTheirGuards() {
        val shared = ShaderSources.assemble("#pragma include \"${ShaderSources.SHADOW_VISIBILITY}\"", "test")
        for (name in listOf("u_shadowAtlas", "u_shadowTexel", "u_shadowMatrices", "u_dirVisMap", "u_dirVisIndex", "#ifdef shadowAtlasFlag", "#ifdef directionalVisibilityFlag")) {
            assertEquals(name, true, shared.contains(name))
        }
    }
}
