/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.sky.procedural

import net.nevinsky.abyssus.core.io.FileLoader
import net.nevinsky.abyssus.core.assets.testMetaLoader
import net.nevinsky.abyssus.core.assets.testProject
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
        val prepared = loader(fixture).prepare("skybox_physical")!!
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
    fun anUnknownAssetPreparesNothing() {
        assertNull(loader(fixture).prepare("nope"))
    }
}
