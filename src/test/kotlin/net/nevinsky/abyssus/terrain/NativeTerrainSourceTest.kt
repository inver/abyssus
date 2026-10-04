/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.terrain

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.assetfiles.FileSnapshot
import net.nevinsky.abyssus.assets.terrain.generation.RecipeStatus
import net.nevinsky.abyssus.assets.terrain.generation.SourceSnapshot
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.properties.AssetReferenceChoices
import java.io.File

class NativeTerrainSourceTest : BasePlatformTestCase() {
    fun testRejectedMetadataStopsSourceReadingAndQueuedPreview() {
        val folder = myFixture.tempDirFixture.findOrCreateDir("assets/terrain")
        val core = service<AbyssusCore>()
        val choices = AssetReferenceChoices(core.json)
        for (text in listOf("{}", """{"format":"abyssus","formatVersion":2}""")) {
            val rejected = readTerrainSource(File(folder.path), text, SceneJson.parse(text), choices, core.terrainRecipes)
            assertTrue(rejected is TerrainSource.Unusable)
            val initial = TerrainSource.Ready("terrain", 400, 17, FileSnapshot.Bytes(ByteArray(17 * 17 * 4)),
                FileSnapshot.Absent, RecipeStatus.Missing, SourceSnapshot("native", "hash", null, true), FileSnapshot.Bytes(byteArrayOf()))
            val queued = mutableListOf<Runnable>()
            val controller = TerrainGenerationController(project, folder, initial, core.terrainGenerator,
                core.heightEncoder, core.terrainRecipes, queued::add, { it.run() }, { rejected })
            controller.preview()
            assertTrue(queued.isEmpty())
            assertFalse(controller.canApply)
            assertEquals((rejected as TerrainSource.Unusable).reason, controller.message)
            assertNull(controller.apply())
            assertTrue(folder.children.isEmpty())
            controller.dispose()
        }
    }
}
