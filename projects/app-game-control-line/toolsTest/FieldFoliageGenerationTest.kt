/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.tools

import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FOLIAGE_DATA_FILE
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBakeState
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageRuntimeLoad
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_CANDIDATE_LIMIT
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_COPY_LIMIT
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/** What the migration makes of the committed field: native files, valid bakes, provenance, counts and clear areas. */
class FieldFoliageGenerationTest {
    private val editing = HeadlessEditing()
    private val manifest = FieldManifest.fromJson(SceneJson().parse(committedManifest().readText()))

    @Test
    fun theCommittedManifestIsWhatTheOriginalSceneRecords() = withOriginal { project ->
        val fresh = File(project, "fresh-manifest.json")
        FieldFoliageTool().record(project, fresh)
        assertEquals(committedManifest().readText(), fresh.readText())
        assertEquals(215, manifest.instances.size)
        assertEquals(listOf(52, 40, 33, 42, 48), listOf("tree_broadleaf", "tree_young", "tree_acacia", "bush_tall", "bush_low").map { s ->
            manifest.instances.count { it.model == "model_airfield_$s" }
        }.let { listOf(it[0], it[1], it[2], it[3], it[4]) })
    }

    @Test
    fun theCommittedProjectIsExactlyWhatTheMigrationMakesOfTheOriginal() = withOriginal { project ->
        FieldFoliageTool().generate(project, committedManifest())
        val made = checksums(project)
        val committed = checksums(committedProject())
        for (path in made.keys.filter { it.startsWith("assets/foliage_airfield_") || it.startsWith("assets/model_airfield_grass/") || it == "scenes/Field.scene" }) {
            assertEquals(path, made[path], committed[path])
        }
        assertEquals(committed.keys.filter { it.startsWith("assets/foliage_") }.toSet(), made.keys.filter { it.startsWith("assets/foliage_") }.toSet())
    }

    @Test
    fun theOutputsAreNativeWithValidBakesAndProvenance() = withOriginal { project ->
        FieldFoliageTool().generate(project, committedManifest())
        for (name in listOf("foliage_airfield_site", "foliage_airfield_outer", "model_airfield_grass")) {
            val dir = File(project, "assets/$name")
            assertNull(name, editing.validate(File(dir, "meta.json").readText(), DocumentKind.ASSET))
            val source = SceneJson().parse(File(dir, "source.json").readText())
            assertEquals("CC0-1.0", source["license"].asText())
            assertEquals("tools/FieldFoliage.kt", source["generator"].asText())
        }
        assertNull(editing.validate(File(project, "scenes/Field.scene").readText(), DocumentKind.SCENE))
        val files = FileLoader(project)
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)
        val runtime = FoliageRuntimeLoad(files, metas, TerrainLoader(files, metas))
        for (t in manifest.terrains) {
            val read = runtime.read(t.foliage, t.asset)
            assertEquals(t.foliage + read.warnings, emptyList<String>(), read.warnings)
            assertEquals(FoliageBakeState.MATCHING, read.foliage!!.state)
            assertTrue(read.foliage!!.bake.copyCount in 1..FOLIAGE_COPY_LIMIT)
            val bake = FoliageDataFile().read(File(project, "assets/${t.foliage}/$FOLIAGE_DATA_FILE").readBytes())!!
            assertEquals(read.foliage!!.bake.copyCount, bake.copyCount)
            val candidates = read.foliage!!.meta.layers.maxOf { l -> (t.size * Math.sqrt(l.density.toDouble())).toLong().let { it * it } }
            assertTrue(candidates <= FOLIAGE_CANDIDATE_LIMIT)
        }
    }

    @Test
    fun everySpeciesKeepsItsCountWithinAQuarterOnEachTerrain() = withCopy { project ->
        FieldFoliageTool().generate(project, committedManifest())
        for (t in manifest.terrains) {
            val bake = FoliageDataFile().read(File(project, "assets/${t.foliage}/foliage.data").readBytes())!!
            val meta = SceneJson().parse(File(project, "assets/${t.foliage}/meta.json").readText())["additional"]["layers"]
            for (layer in meta) {
                val model = layer["models"][0]["asset"].asText()
                if (model.endsWith("grass")) continue
                val target = manifest.countOf(model, t.entity)
                val count = bake.layers.first { it.id == layer["id"].asInt() }.copyCount
                assertTrue("$model on ${t.foliage}: $count against $target", abs(count - target) <= 0.25 * target)
            }
        }
        // and across both terrains
        val total = manifest.terrains.sumOf { t ->
            FoliageDataFile().read(File(project, "assets/${t.foliage}/foliage.data").readBytes())!!.layers.filter { it.id != 6 }.sumOf { it.copyCount }
        }
        assertTrue("$total trees and bushes against 215", abs(total - 215) <= 0.25 * 215)
    }

    @Test
    fun noGrassWithinThePadAndNoOuterCopyUnderTheSite() = withCopy { project ->
        FieldFoliageTool().generate(project, committedManifest())
        val site = manifest.terrains[0]
        val siteBake = FoliageDataFile().read(File(project, "assets/${site.foliage}/foliage.data").readBytes())!!
        val grass = siteBake.layers.first { it.id == 6 }
        assertTrue("no grass was made", grass.copyCount > 1000)
        for (copy in grass.chunks.flatten()) {
            val d = hypot(site.originX + copy.x - manifest.pilotX, site.originZ + copy.z - manifest.pilotZ)
            assertTrue("grass at $d m", d >= FIELD_GRASS_CLEAR_RADIUS)
        }
        for (layer in siteBake.layers.filter { it.id != 6 }) for (copy in layer.chunks.flatten()) {
            assertTrue(hypot(site.originX + copy.x - manifest.pilotX, site.originZ + copy.z - manifest.pilotZ) >= FIELD_CLEAR_RADIUS)
        }
        val outer = manifest.terrains[1]
        val outerBake = FoliageDataFile().read(File(project, "assets/${outer.foliage}/foliage.data").readBytes())!!
        for (copy in outerBake.layers.flatMap { it.chunks.flatten() }) {
            val x = outer.originX + copy.x
            val z = outer.originZ + copy.z
            assertTrue("an outer copy under the site at $x, $z",
                x < site.originX || x > site.originX + site.size || z < site.originZ || z > site.originZ + site.size)
        }
    }

    @Test
    fun aFitThatCannotBeMadeFailsAndPublishesNothing() = withOriginal { project ->
        val before = checksums(project)
        // a manifest that asks for far more young trees on the site than its mask can hold
        val tampered = SceneJson().parse(committedManifest().readText())
        val instances = tampered["instances"] as com.fasterxml.jackson.databind.node.ArrayNode
        repeat(5000) { i ->
            instances.addArray().add(900000 + i).add("tree_young").add(0f).add(0f).add(60f).add(0f).add(1f).add(0)
        }
        val file = File(project, "tampered-manifest.json").apply { writeText(SceneJson().pretty(tampered)) }
        val after = checksums(project).keys
        assertThrows(Exception::class.java) { FieldFoliageTool().generate(project, file) }
        // the tool refuses before anything is published: only the tampered manifest differs
        assertEquals(before, checksums(project).filterKeys { it != "tampered-manifest.json" })
        assertTrue(after.isNotEmpty())
    }
}
