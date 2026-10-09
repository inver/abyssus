/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.scene

import net.nevinsky.abyssus.lib.gdx.dto.RayTracingDto
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SceneRayTracingBindingTest {
    private val json = JsonProcessor()
    private val loader = SceneLoader(json, FileLoader(File(".")))

    @Test fun omittedLimitsKeepTheirEffectiveDefaults() {
        val scene = loader.parse("""{"format":"abyssus","formatVersion":1,"rayTracing":{}}""")
        assertEquals(RayTracingDto(), scene.rayTracing)
    }

    @Test fun eachExplicitNullLimitSurvivesBindingForLaterValidation() {
        for (field in listOf("targetSamplesPerPixel", "maxRaysPerFrame", "maxReflectionBounces", "maxRefractionBounces")) {
            val scene = loader.parse("""{"format":"abyssus","formatVersion":1,"rayTracing":{"$field":null}}""")
            val serialized = json.readObject(json.toString(scene.rayTracing!!))
            assertTrue("$field must remain null rather than become a default", serialized[field].isNull)
        }
    }
}
