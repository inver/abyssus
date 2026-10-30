/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import net.nevinsky.abyssus.app.game.controlline.render.fieldAssets
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.loading.AssetState
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

/** The foliage a field scene lists, and what a failed optional foliage asset does to loading. */
class FieldFoliageTest {
    private val copies = listOf(FoliageCopy(0, 100f, 100f, 0f, 1f))

    @Test
    fun theFieldSceneListsTheFoliageOfItsTerrainEntities() = FieldFoliageProject().use { project ->
        project.foliage(copies)
        val field = FieldScene(project.load(project.scene(xs = listOf(0f, 300f))))
        assertEquals(listOf("foliage_meadow", "foliage_meadow"), field.foliages.map { it.foliage })
        assertEquals(listOf("terr", "terr"), field.foliages.map { it.terrain })
        assertEquals(listOf(0L, 1L), field.foliages.map { it.entityId })
        assertTrue(field.foliageWarnings.isEmpty())
    }

    @Test
    fun aSceneWithoutFoliageListsNone() = FieldFoliageProject().use { project ->
        assertTrue(FieldScene(project.load(project.scene(foliage = null))).foliages.isEmpty())
    }

    @Test
    fun aMissingFoliageAssetFailsTerminallyAndNeverStaysLoading() = FieldFoliageProject().use { project ->
        val messages = mutableListOf<String>()
        val storage = fieldAssets(project.dir, JsonProcessor(warningsTo(messages)), warningsTo(messages), Executor { it.run() })
        try {
            storage.request("no_such_foliage")
            repeat(20) { storage.update() }
            assertFalse("a missing foliage asset keeps loading", storage.isLoading("no_such_foliage"))
            assertFalse(storage.isLoading())
            assertTrue(storage.state("no_such_foliage") is AssetState.Failed || storage.state("no_such_foliage") == null)
        } finally {
            storage.dispose()
        }
    }

    @Test
    fun unsupportedFoliageMetadataIsTerminalWithAWarning() = FieldFoliageProject().use { project ->
        project.foliage(copies)
        val meta = java.io.File(project.dir, "assets/foliage_meadow/meta.json")
        meta.writeText(meta.readText().replace("\"formatVersion\":1", "\"formatVersion\":2"))
        val messages = mutableListOf<String>()
        val storage = fieldAssets(project.dir, JsonProcessor(warningsTo(messages)), warningsTo(messages), Executor { it.run() })
        try {
            storage.request("foliage_meadow")
            repeat(20) { storage.update() }
            assertFalse(storage.isLoading("foliage_meadow"))
            assertTrue(storage.getAs<Any>("foliage_meadow") == null)
        } finally {
            storage.dispose()
        }
    }
}
