/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import net.nevinsky.abyssus.lib.core.assets.sky.procedural.AtmosphereParams
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.MetaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The `additional` block of a procedural sky `meta.json`, bound on its own. */
class AtmosphereParamsTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    private fun parse(additional: String) = json.parse(additional, ProceduralSkyMeta::class.java)

    @Test
    fun omittedParametersTakeEarthDefaults() {
        assertEquals(AtmosphereParams(), parse("""{"vertex":"a.vert","fragment":"a.frag"}""").params)
    }

    @Test
    fun parsesTheFixtureMeta() {
        val meta = testMetaLoader(testProject("Untitled")).loadBaseMeta("skybox_physical")!!
        assertEquals(MetaType.SKYBOX_PROCEDURAL, meta.type)
        val a = meta.typedAdditional<ProceduralSkyMeta>()
        assertEquals("sky.vert", a.vertex)
        assertEquals("sky.frag", a.fragment)
        assertEquals(AtmosphereParams(), a.params)
    }

    @Test
    fun overridesApplyAndTheRestStaysDefault() {
        val a = parse("""{"vertex":"v","fragment":"f","mieG":0.5,"betaRayleigh":[1e-6,2e-6,3e-6]}""")
        assertEquals(0.5f, a.params.mieG, 0f)
        assertEquals(listOf(1e-6f, 2e-6f, 3e-6f), a.params.betaRayleigh)
        assertEquals(AtmosphereParams().betaMie, a.params.betaMie, 0f)
    }

    @Test
    fun aMetaWithoutShaderNamesHasNoShaderFiles() {
        val a = parse("{}")
        assertNull(a.vertex)
        assertNull(a.fragment)
        assertNotNull(a.params)
    }
}
