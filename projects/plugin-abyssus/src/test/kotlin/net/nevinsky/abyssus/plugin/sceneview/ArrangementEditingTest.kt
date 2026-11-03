/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementResult
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.arrange.LineHeights
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.lib.core.editor.terrainData
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.testMetaFiles
import java.io.File

/** Arrangements written to a copy of the Untitled project on disk: one undo step each, terrain heights without a view. */
class ArrangementEditingTest : BasePlatformTestCase() {
    private lateinit var projectDir: File
    private lateinit var scene: VirtualFile
    private val tree = BaseMeta(MetaType.MODEL, "tree")
    private val terrain = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"

    override fun setUp() {
        super.setUp()
        projectDir = FileUtil.createTempDirectory("abyssus-arrangement", null)
        FileUtil.copyDir(File("src/test/testData/project/Untitled"), projectDir)
        scene = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectDir, "scenes/Main Scene.scene"))!!
    }

    private fun text() = FileDocumentManager.getInstance().getDocument(scene)!!.text
    private fun scene1() = net.nevinsky.abyssus.plugin.parsedScene(text())
    private fun slots() = scene1().entity(11)!!.component(PathArrangementComponent::class.java)!!.slots.toList()
    private fun y(id: Int) = scene1().entity(id.toLong())!!.component(PositionComponent::class.java)!!.localPosition.y

    private fun draw(heights: GroundHeight = LineHeights()): ArrangementResult =
        ArrangementEditing.create(project, scene, tree, listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f)), false, heights)

    fun testADrawingIsOneUndoStep() {
        myFixture.openFileInEditor(scene)
        val before = text()
        assertEquals(ArrangementResult.Changed(11L), draw())
        assertEquals(listOf(12, 13, 14, 15, 16), slots())
        UndoManager.getInstance(project).undo(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
        assertEquals(before, text())
    }

    fun testAnUnreadableSceneIsRefusedAndNothingIsWritten() {
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            FileDocumentManager.getInstance().getDocument(scene)!!.setText("{ not json")
        }
        assertTrue(draw() is ArrangementResult.Rejected)
        assertEquals("{ not json", text())
    }

    fun testASpacingEditFromThePropertiesPanelStandsOnTheFixtureTerrain() {
        assertEquals(ArrangementResult.Changed(11L), draw(ArrangementHeights.of(project).forScene(scene)!!))
        val result = SceneComponentEdits.update(project, scene, 11L, "PathArrangementComponent", "spacing", "20", testMetaFiles())
        assertEquals(EditResult.Changed, result)
        assertEquals(listOf(12, 13, 14), slots())
        val heights = terrainData(projectDir, terrain)
        // entity 1 stands at (-38.25267, 0, -32.7754)
        assertEquals(heights.heightAt(20f + 38.25267f, 32.7754f)!!, y(13), 1e-3f)
        assertEquals(heights.heightAt(40f + 38.25267f, 32.7754f)!!, y(14), 1e-3f)
    }

    fun testASettingEditThatMakesTooManyCopiesIsRefused() {
        draw()
        val before = text()
        val result = SceneComponentEdits.update(project, scene, 11L, "PathArrangementComponent", "spacing", "0.001", testMetaFiles())
        assertTrue(result is EditResult.Rejected)
        assertEquals(before, text())
    }

    fun testRegenerateWithNothingChangedWritesNothing() {
        draw(ArrangementHeights.of(project).forScene(scene)!!)
        val before = text()
        assertEquals(ArrangementResult.Unchanged, ArrangementEditing.regenerate(project, scene, 11L, ArrangementHeights.of(project).forScene(scene)!!))
        assertEquals(before, text())
    }

    fun testRegenerateAfterTheGroundChangedRewritesOnlyY() {
        draw()
        val before = scene1()
        assertEquals(ArrangementResult.Changed(), ArrangementEditing.regenerate(project, scene, 11L, GroundHeight { _, _, _ -> 7f }))
        val after = scene1()
        for (id in 12..16) {
            assertEquals(7f, y(id), 0f)
            fun withoutY(scene: net.nevinsky.abyssus.lib.core.dto.SceneDto): String {
                val entity = scene.entity(id.toLong())!!
                val position = entity.component(PositionComponent::class.java)!!
                val x = position.localPosition.x; val z = position.localPosition.z
                return listOf(x, z, entity.header().type, entity.component(NameComponent::class.java)?.name).joinToString("|")
            }
            assertEquals(withoutY(before), withoutY(after))
        }
    }

    fun testPointsAreEditedAndRegeneratedAsOneStep() {
        myFixture.openFileInEditor(scene)
        draw()
        val before = text()
        val result = ArrangementEditing.setPoints(project, scene, 11L, listOf(Vector3(0f, 0f, 0f), Vector3(60f, 0f, 0f)), LineHeights())
        assertEquals(ArrangementResult.Changed(), result)
        assertEquals(7, slots().size)
        UndoManager.getInstance(project).undo(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
        assertEquals(before, text())
    }

    fun testTheHeightsAreReadOnceAndKeptWhileTheTerrainIsUnchanged() {
        val first = ArrangementHeights.of(project).forScene(scene)!!
        val second = ArrangementHeights.of(project).forScene(scene)!!
        assertEquals(first.heightAt(20f, 0f, -99f), second.heightAt(20f, 0f, -99f), 0f)
        assertTrue(first.heightAt(20f, 0f, -99f) != -99f)
        assertEquals(-99f, first.heightAt(50000f, 0f, -99f), 0f)
    }
}
