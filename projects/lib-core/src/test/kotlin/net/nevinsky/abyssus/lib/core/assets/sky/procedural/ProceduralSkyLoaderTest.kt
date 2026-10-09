/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.sky.procedural

import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.assets.testMetaLoader
import net.nevinsky.abyssus.lib.gdx.assets.testProject
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProceduralSkyLoaderTest {
    private val fixture = testProject("Untitled")

    private fun loader(projectDir: File): ProceduralSkyLoader =
        FileLoader(projectDir).let { ProceduralSkyLoader(it, testMetaLoader(projectDir, fileLoader = it)) }

    private fun copyOfProject(): File {
        val dir = Files.createTempDirectory("sky").toFile()
        File(fixture, "assets/skybox_physical").copyRecursively(File(dir, "assets/skybox_physical"))
        return dir
    }

    @Test
    fun prepareReadsMetaAndBothShaders() {
        val prepared = loader(fixture).prepare("skybox_physical")!!.staged
        assertEquals(AtmosphereParams(), prepared.params)
        assertTrue(prepared.vertex.contains("a_position"))
        assertTrue(prepared.fragment.contains("raySphere"))
    }

    @Test
    fun missingFragmentFileFailsPrepare() {
        val dir = copyOfProject()
        try {
            File(dir, "assets/skybox_physical/sky.frag").delete()
            val error = runCatching { loader(dir).prepare("skybox_physical") }.exceptionOrNull()
            assertTrue("expected a missing-shader error, got $error", error is IllegalStateException && error.message!!.contains("sky.frag"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theFixtureSkyHasNoClouds() {
        assertNull(loader(fixture).prepare("skybox_physical")!!.staged.clouds)
    }

    private val cloudsUuid = "3f2a9c1e-7b4d-4e8a-9c6f-1d2e3b4a5c6d"

    /** A copy of the project whose sky's `clouds` is [clouds] (JSON), with a `CLOUDS` asset `clouds_fair` of [cloudsUuid]. */
    private fun withClouds(clouds: String, test: (File) -> Unit) {
        val dir = copyOfProject()
        try {
            val meta = File(dir, "assets/skybox_physical/meta.json")
            meta.writeText(meta.readText().replace("\"sunIntensity\": 20.0", "\"sunIntensity\": 20.0,\n    \"clouds\": $clouds"))
            File(dir, "assets/clouds_fair").mkdirs()
            File(dir, "assets/clouds_fair/meta.json").writeText(
                """{"format": "abyssus", "formatVersion": 1, "uuid": "$cloudsUuid", "type": "CLOUDS", "additional": {"low": {"type": "cumulus"}}}"""
            )
            test(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun loader(dir: File, log: RecordingLogger): ProceduralSkyLoader =
        FileLoader(dir).let { ProceduralSkyLoader(it, testMetaLoader(dir, fileLoader = it), log = log) }

    @Test
    fun theCloudAssetIsADependencyFoundByUuid() = withClouds("\"$cloudsUuid\"") { dir ->
        val sky = loader(dir)
        val prepared = sky.prepare("skybox_physical")!!.staged
        assertEquals("clouds_fair", prepared.clouds)
        assertEquals(setOf("clouds_fair"), sky.dependencies(prepared))
    }

    @Test
    fun anUnknownCloudUuidIsLoggedAndTheSkyHasNoClouds() = withClouds("\"0b1c2d3e-0000-4000-8000-000000000000\"") { dir ->
        val log = RecordingLogger()
        val sky = loader(dir, log)
        val prepared = sky.prepare("skybox_physical")!!.staged
        assertNull(prepared.clouds)
        assertEquals(emptySet<String>(), sky.dependencies(prepared))
        assertEquals(1, log.warnings.size)
    }

    @Test
    fun aCloudsValueOfTheWrongKindStillLoadsTheSky() = withClouds("""{"enabled": true, "low": {"type": "cumulus"}}""") { dir ->
        val prepared = loader(dir).prepare("skybox_physical")!!.staged
        assertEquals(AtmosphereParams(), prepared.params)
        assertNull(prepared.clouds)
    }

    @Test
    fun anUnknownAssetPreparesNothing() {
        assertNull(loader(fixture).prepare("nope"))
    }
}
