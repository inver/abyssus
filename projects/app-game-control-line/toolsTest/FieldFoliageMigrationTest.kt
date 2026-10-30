/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.tools

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reruns, conflicts and what a migration leaves of the scene. */
class FieldFoliageMigrationTest {
    private fun entities(project: File) = SceneJson().parse(File(project, "scenes/Field.scene").readText())["ecs"]["entities"]

    private fun vegetation(project: File): Boolean = File(project, "scenes/Field.scene").readText().let {
        listOf("tree_broadleaf", "tree_young", "tree_acacia", "bush_tall", "bush_low").any { s -> it.contains("model_airfield_$s") }
    }

    @Test
    fun twoRunsFromTheOriginalAndTwoFromTheMigratedProjectWriteIdenticalFiles() {
        val tool = FieldFoliageTool()
        val results = ArrayList<Map<String, String>>()
        repeat(2) {
            withOriginal { project ->
                tool.generate(project, committedManifest())
                applyPostMigrationSceneEdits(project)
                results += checksums(project)
            }
        }
        // the committed project is already migrated
        withCopy { project ->
            tool.generate(project, committedManifest())
            repeat(2) {
                tool.generate(project, committedManifest())
                results += checksums(project)
            }
        }
        assertEquals(results[0].keys, results[1].keys)
        for (other in results.drop(1)) assertEquals(results[0], other)
        assertEquals(results[0], checksums(committedProject()))
    }

    @Test
    fun theSceneLosesOnlyTheVegetationAndGainsOnlyTheFoliageComponents() = withOriginal { project ->
        val before = entities(project)
        val manifest = FieldManifest.fromJson(SceneJson().parse(committedManifest().readText()))
        val ids = manifest.instances.map { it.id.toString() }.toSet()
        val result = FieldFoliageTool().generate(project, committedManifest())
        assertEquals(215, result.removedEntities)
        val after = entities(project)
        assertFalse(vegetation(project))
        for ((id, entity) in before.properties()) {
            if (id in ids) {
                assertFalse("entity $id should be gone", after.has(id))
            } else if (id == "0" || id == "300") {
                val components = after[id]["components"]
                assertEquals(manifest.terrains.first { it.entity == id.toInt() }.foliage, components["FoliageComponent"]["assetName"].asText())
                val others = entity["components"].properties().map { it.key }
                assertEquals(others + "FoliageComponent", components.properties().map { it.key })
                for (name in others) assertEquals("$id/$name", entity["components"][name], components[name])
            } else {
                assertEquals("entity $id changed", entity, after[id])
            }
        }
    }

    @Test
    fun aSceneEditedSinceTheManifestIsRefusedAndNothingIsPublished() = withOriginal { project ->
        val scene = File(project, "scenes/Field.scene")
        // move one recorded tree
        val text = scene.readText()
        val edited = text.replaceFirst(Regex("\"x\": -148.215"), "\"x\": -148.5")
        check(edited != text) { "the edit did not apply" }
        scene.writeText(edited)
        val before = checksums(project)
        assertThrows(MigrationConflict::class.java) { FieldFoliageTool().generate(project, committedManifest()) }
        assertEquals(before, checksums(project))
    }

    @Test
    fun aChangedRestOfTheSceneOrInputIsRefused() = withOriginal { project ->
        val scene = File(project, "scenes/Field.scene")
        val text = scene.readText()
        scene.writeText(text.replaceFirst("Airfield outer ground", "Airfield outer ground renamed"))
        check(scene.readText() != text) { "the edit did not apply" }
        val before = checksums(project)
        assertThrows(MigrationConflict::class.java) { FieldFoliageTool().generate(project, committedManifest()) }
        assertEquals(before, checksums(project))

        withOriginal { other ->
            File(other, "assets/terrain_airfield_site/terrain.data").appendBytes(byteArrayOf(0))
            val sums = checksums(other)
            assertThrows(MigrationConflict::class.java) { FieldFoliageTool().generate(other, committedManifest()) }
            assertEquals(sums, checksums(other))
        }
    }

    @Test
    fun aManifestCannotBeReplacedByADifferentRecording() = withOriginal { project ->
        val manifest = File(project, "manifest.json")
        FieldFoliageTool().record(project, manifest)
        val scene = File(project, "scenes/Field.scene")
        scene.writeText(scene.readText().replaceFirst("\"x\": -148.215", "\"x\": -148.5"))
        assertThrows(MigrationConflict::class.java) { FieldFoliageTool().record(project, manifest) }
        assertTrue(manifest.readText().isNotEmpty())
    }
}
