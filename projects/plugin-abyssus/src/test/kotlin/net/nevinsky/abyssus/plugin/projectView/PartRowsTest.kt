/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.badlogic.gdx.utils.GdxNativesLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelMeshLister
import net.nevinsky.abyssus.lib.core.editor.parseScene

/** OpenSpec `abyssus-project-view`, "A model entity lists its parts". */
class PartRowsTest : BasePlatformTestCase() {
    private val scene
        get() = parseScene(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText())

    private fun entry(name: String, value: Any?) = DtoEntry("/p/$name", name, value, null, null, null, listOf("ecs"))

    private fun piperParts(): net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPartsInfo {
        GdxNativesLoader.load()
        val root = Files.createTempDirectory("tree-parts").toFile()
        try {
            val name = "model_900f6f61-6384-434a-be81-56ce303fbb56"
            assertTrue(File("src/test/testData/project/Untitled/assets/$name").copyRecursively(File(root, "assets/$name")))
            val files = testFileLoader(root)
            return checkNotNull(ModelMeshLister(files, testMetaLoader(root, fileLoader = files)).parts(name))
        } finally {
            root.deleteRecursively()
        }
    }

    fun testModelSixListsAPartsRowAfterItsChildEntitiesWithItsPartCount() {
        val node = EntityNode(scene, 6)
        val dto = DtoEntryNode(project, "/p/ecs", DtoRow("6", node), null, listOf("ecs"))
        val rows = dto.children.map { (it as DtoEntryNode).value }
        assertEquals(listOf(PARTS_ROW), rows.map { it.name })

        val info = piperParts()
        val parts = PartsNode(node.scene, 6, "model_900f6f61-6384-434a-be81-56ce303fbb56", null, info)
        assertEquals(RowText("Parts", "193"), rowText(entry(PARTS_ROW, parts)))
        val root = partRows(parts).single()
        assertEquals("piper_pa18.obj", root.name)
        val children = partRows(root.value as PartNode)
        assertEquals(192, children.size)
        assertTrue(children.any { it.name == "flap_L01_Cube.012" })
    }

    fun testAPartWithAPivotOrHingeShowsMarks() {
        val info = piperParts()
        val plain = partRows(partRows(PartsNode(scene, 6, "a", null, info)).single().value as PartNode).first().value as PartNode
        assertEquals(RowText(plain.part.name, null), rowText(entry(plain.part.name, plain)))
        val marked = PartNode(scene, 6, "a", null, plain.part.copy(
            pivot = net.nevinsky.abyssus.lib.core.editor.modelmesh.PartPivotStatus(true),
            hinge = net.nevinsky.abyssus.lib.core.assets.model.PartHinge(-35f, 35f),
        ))
        assertEquals("pivot, hinge", rowText(entry(marked.part.name, marked)).secondary)
    }

    fun testWhileTheModelIsReadTheRowShowsNothingBelowIt() {
        val parts = PartsNode(scene, 6, "model_900f6f61-6384-434a-be81-56ce303fbb56", null, null)
        assertEquals(RowText("Parts", null), rowText(entry(PARTS_ROW, parts)))
        assertTrue(partRows(parts).isEmpty())
    }
}
