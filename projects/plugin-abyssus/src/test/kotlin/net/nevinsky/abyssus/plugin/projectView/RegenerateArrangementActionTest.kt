/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.arrange.LineHeights
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.plugin.sceneview.ArrangementEditing
import net.nevinsky.abyssus.plugin.sceneview.ArrangementHeights
import java.io.File

/** Regenerate on the tree row of a path arrangement of a copy of the Untitled project. */
class RegenerateArrangementActionTest : BasePlatformTestCase() {
    private lateinit var scene: VirtualFile

    override fun setUp() {
        super.setUp()
        val dir = FileUtil.createTempDirectory("abyssus-regenerate", null)
        FileUtil.copyDir(File("src/test/testData/project/Untitled"), dir)
        scene = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(dir, "scenes/Main Scene.scene"))!!
        // drawn at height 0 over a terrain that is not flat: Regenerate has work to do
        ArrangementEditing.create(project, scene, RenderAsset("MODEL", "tree"), listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f)), false, LineHeights())
    }

    private fun text() = FileDocumentManager.getInstance().getDocument(scene)!!.text

    private fun rowOf(id: String): DtoEntryNode {
        val tree = SceneEntityTree(SceneJson().parse(text()))
        return DtoEntryNode(project, scene.path, DtoRow(id, tree.entities()!![id]), scene, tree.entityKeys())
    }

    private class On(val node: Any?) : RegenerateArrangementAction() {
        override fun selected(e: AnActionEvent) = node
    }

    private fun event(action: AnAction): AnActionEvent = TestActionEvent.createTestEvent(action).also { action.update(it) }

    fun testItIsOfferedOnAnArrangementRowOnly() {
        assertTrue(ActionManager.getInstance().getAction("Abyssus.RegenerateArrangement") is RegenerateArrangementAction)
        assertTrue(event(On(rowOf("11"))).presentation.isEnabledAndVisible)
        assertFalse(event(On(rowOf("12"))).presentation.isEnabledAndVisible)
        assertFalse(event(On(rowOf("0"))).presentation.isEnabledAndVisible)
    }

    fun testItPlacesTheCopiesOnTheTerrainAndASecondRunWritesNothing() {
        val action = On(rowOf("11"))
        val before = text()
        action.actionPerformed(event(action))
        val after = text()
        assertTrue("the copies stood at the line's height, not on the terrain", before != after)
        val ecs = SceneJson().parse(after)["ecs"]
        assertEquals(ArrangementHeights.of(project).forScene(scene)!!.heightAt(20f, 0f, -99f), ecs["14"]["components"]["PositionComponent"]["localPosition"].path("y").floatValue(), 1e-3f)
        action.actionPerformed(event(action))
        assertEquals(after, text())
    }
}
