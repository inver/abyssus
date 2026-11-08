/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelmesh

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import java.nio.file.Files
import net.nevinsky.abyssus.lib.core.assets.model.PivotRefusalReason
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ModelPartsListerTest {
    private val dirs = mutableListOf<File>()

    @After fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private val trainerSource = File("../app-game-control-line/project/ControlLine/assets/model_trainer")
    private val piperSource =
        File(System.getProperty("abyssus.testData"), "project/Untitled/assets/model_900f6f61-6384-434a-be81-56ce303fbb56")

    /** A project holding [source] as the asset [name], with [parts] JSON spliced into its meta when not null. */
    fun lister(source: File, name: String, parts: String? = null): Pair<File, ModelMeshLister> {
        GdxNativesLoader.load()
        val root = Files.createTempDirectory("parts-lister").toFile().also(dirs::add)
        assertTrue(source.copyRecursively(File(root, "assets/$name")))
        if (parts != null) {
            val meta = File(root, "assets/$name/meta.json")
            meta.writeText(meta.readText().replace(Regex("\"materials\"\\s*:\\s*\\[\\s*]"), "\"materials\": [], \"parts\": $parts"))
            assertTrue(meta.readText().contains("\"parts\""))
        }
        val files = testFileLoader(root)
        return root to ModelMeshLister(files, testMetaLoader(root, fileLoader = files))
    }

    private fun names(part: ModelPart): List<String> = listOf(part.name) + part.children.flatMap(::names)

    @Test
    fun theTrainerTreeIsNestedAsInTheModel() {
        val (_, lister) = lister(trainerSource, "trainer")
        val info = checkNotNull(lister.parts("trainer"))
        val root = info.roots.single()
        assertEquals("Trainer", root.name)
        val children = root.children.map { it.name }
        for (name in listOf("Fuselage", "VTail", "Hub", "Propeller", "Wings", "Tail", "NoseWheel", "LeftWheel", "RightWheel")) {
            assertTrue("$name in $children", name in children)
        }
        val wings = root.children.first { it.name == "Wings" }
        assertTrue(wings.children.map { it.name }.containsAll(listOf("LeftWing", "RightWing")))
        val leftWing = wings.children.first { it.name == "LeftWing" }
        assertTrue(leftWing.children.map { it.name }.containsAll(listOf("LeftFlap", "LeftAileron")))
        val flap = leftWing.children.first { it.name == "LeftFlap" }
        assertNotNull(flap.bounds)
        assertEquals(NO_PIVOT, flap.pivot)
        assertTrue(info.triangles.getValue("LeftFlap").triangleCount > 0)
    }

    @Test
    fun piperIsOneRootWithItsFlatChildren() {
        val (_, lister) = lister(piperSource, "piper")
        val info = checkNotNull(lister.parts("piper"))
        val root = info.roots.single()
        assertEquals("piper_pa18.obj", root.name)
        assertEquals(192, root.children.size)
        assertEquals(193, info.count)
        assertTrue(root.children.any { it.name == "flap_L01_Cube.012" })
    }

    @Test
    fun aMeshVariantListsOnlyItsNodesAndAncestors() {
        val (_, lister) = lister(trainerSource, "trainer")
        val mesh = checkNotNull(lister.list("trainer")).first().id
        val variant = checkNotNull(lister.parts(net.nevinsky.abyssus.lib.core.assets.ModelKey("trainer", mesh).encode()))
        val all = checkNotNull(lister.parts("trainer"))
        assertTrue(variant.count < all.count)
        assertTrue(variant.count >= 1)
        // every listed part has geometry or an ancestor relationship to one
        fun hasGeometry(part: ModelPart): Boolean = part.bounds != null || part.children.any(::hasGeometry)
        variant.roots.forEach { assertTrue(hasGeometry(it)) }
    }

    @Test
    fun aRefusedPivotReportsItsReasonAndAnAppliedOneMovesTheFileFrame() {
        val (_, lister) = lister(
            trainerSource, "trainer",
            """{"NoSuchNode":{"pivot":{"position":{"x":1,"y":0,"z":0}}},
               "Fuselage":{"pivot":{"position":{"x":0.01,"y":0,"z":0}}},
               "LeftFlap":{"pivot":{"position":{"x":0.01,"y":0,"z":0}}}}""",
        )
        val info = checkNotNull(lister.parts("trainer"))
        val fuselage = checkNotNull(info.find("Fuselage"))
        assertEquals(PartPivotStatus(true, null), fuselage.pivot)
        // the trainer's flaps are moved by its crash animations, so a pivot on them is refused
        assertEquals(PartPivotStatus(true, PivotRefusalReason.ANIMATED), info.find("LeftFlap")!!.pivot)
        // the file frame is the pivoted rest moved back by the pivot: rest origin = fileFrame origin + 0.01 along its X
        val rest = info.rest.getValue("Fuselage").getTranslation(Vector3())
        val file = checkNotNull(lister.fileFrame("trainer", "Fuselage")).getTranslation(Vector3())
        val expected = Vector3(0.01f, 0f, 0f).mul(checkNotNull(lister.fileFrame("trainer", "Fuselage")))
        assertTrue("$rest vs $expected", rest.dst(expected) < 1e-5f)
        assertTrue(rest.dst(file) > 1e-4f)
        assertNull("unknown nodes are not parts", info.find("NoSuchNode"))
    }

    @Test
    fun everyPartKnowsWhetherAPivotCouldBeAppliedToIt() {
        // the trainer's crash animations move the flaps (and the root, wheels, wings...), not the fuselage
        val (_, lister) = lister(trainerSource, "trainer")
        val info = checkNotNull(lister.parts("trainer"))
        assertEquals(PivotRefusalReason.ANIMATED, info.find("LeftFlap")!!.pivotRefusal)
        assertNull(info.find("Fuselage")!!.pivotRefusal)
        assertFalse(info.find("Fuselage")!!.pivot.configured)
    }

    @Test
    fun invalidateDropsPartsAndVariants() {
        val (_, lister) = lister(trainerSource, "trainer")
        lister.parts("trainer")
        assertNotNull(lister.cachedParts("trainer"))
        lister.invalidate("trainer")
        assertNull(lister.cachedParts("trainer"))
    }
}
