/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.scene

import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.nio.file.Files

/** A game ignores the editor-only scene keys, and loading never writes the file. */
class SceneEditorKeysIgnoredTest {
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    /** A copy of `Untitled` whose `Main Scene` holds [extra] after its format keys; unchanged when [extra] is empty. */
    private fun project(extra: String = ""): File {
        val root = Files.createTempDirectory("editor-keys").toFile().also(dirs::add)
        assertTrue(testProject("Untitled").copyRecursively(root))
        val scene = File(root, "scenes/Main Scene.scene")
        val text = scene.readText()
        val marker = "\"formatVersion\": 1,"
        assertTrue("fixture shape changed", marker in text)
        if (extra.isNotEmpty()) scene.writeText(text.replaceFirst(marker, "$marker\n  $extra"))
        return root
    }

    private fun loader(root: File): RuntimeSceneLoader {
        val json = JsonProcessor(NOPLogger.NOP_LOGGER)
        return RuntimeSceneLoader(SceneLoader(json, FileLoader(root), ComponentRegistry()), EcsLoader(), NOPLogger.NOP_LOGGER)
    }

    @Test
    fun rayTracingKeysChangeNeitherTheLoadedSceneNorTheFile() {
        val plain = loader(project()).load("Main Scene.scene")
        val withRay = project("\"rayTracingEnabled\": true,\n  \"rayTracing\": {\"targetSamplesPerPixel\": 64},")
        val file = File(withRay, "scenes/Main Scene.scene")
        val before = file.readBytes()

        val loaded = loader(withRay).load("Main Scene.scene")

        assertNotNull(plain)
        assertNotNull(loaded)
        assertTrue(loaded!!.scene.ecs.isNotEmpty())
        assertEquals(plain!!.engine.entities.size(), loaded.engine.entities.size())
        assertEquals(plain.scene.ecs.keys, loaded.scene.ecs.keys)
        assertEquals(plain.scene.skyboxName, loaded.scene.skyboxName)
        assertArrayEquals(before, file.readBytes())
    }
}
