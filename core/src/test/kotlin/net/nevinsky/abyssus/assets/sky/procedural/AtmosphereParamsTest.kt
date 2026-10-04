package net.nevinsky.abyssus.assets.sky.procedural

import net.nevinsky.abyssus.assets.testProject
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.sky.procedural.AtmosphereParams
import net.nevinsky.abyssus.assets.sky.procedural.ProceduralSkyMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AtmosphereParamsTest {
    private val json = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private fun parse(text: String) = json.readValue(text, ProceduralSkyMeta::class.java)

    @Test
    fun omittedParametersTakeEarthDefaults() {
        val meta = parse("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"type":"SKYBOX_PROCEDURAL","additional":{"vertex":"a.vert","fragment":"a.frag"}}""")
        assertEquals(MetaType.SKYBOX_PROCEDURAL, meta.type)
        assertEquals(AtmosphereParams(), meta.additional.params)
    }

    @Test
    fun parsesTheFixtureMeta() {
        val text = java.io.File(testProject("Untitled"), "assets/skybox_physical/meta.json").readText()
        val a = parse(text).additional
        assertEquals("sky.vert", a.vertex)
        assertEquals("sky.frag", a.fragment)
        assertEquals(AtmosphereParams(), a.params)
    }

    @Test
    fun overridesApplyAndTheRestStaysDefault() {
        val a = parse("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"type":"SKYBOX_PROCEDURAL","additional":{"vertex":"v","fragment":"f","mieG":0.5,"betaRayleigh":[1e-6,2e-6,3e-6]}}""").additional
        assertEquals(0.5f, a.params.mieG, 0f)
        assertEquals(listOf(1e-6f, 2e-6f, 3e-6f), a.params.betaRayleigh)
        assertEquals(AtmosphereParams().betaMie, a.params.betaMie, 0f)
    }

    @Test
    fun rejectsAMetaWithoutShaderNames() {
        val a = parse("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"type":"SKYBOX_PROCEDURAL","additional":{}}""").additional
        assertNull(a.vertex)
        assertNull(a.fragment)
        assertNotNull(a.params)
    }
}
