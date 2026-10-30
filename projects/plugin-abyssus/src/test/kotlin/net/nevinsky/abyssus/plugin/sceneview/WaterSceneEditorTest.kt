/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.water.ValidWater
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.water.WaterSceneEdits
import java.io.File

/** What the Scene view tab does with water selected in the tree, picked in the view and moved: the view is a fake. */
class WaterSceneEditorTest : BasePlatformTestCase() {
    private class FakeView(var current: SceneRenderParams) : SceneView {
        val component = javax.swing.JPanel()
        var selectedWater: String? = null
        override fun selectWater(waterId: String) { selectedWater = waterId }
        override var onFailure: ((Throwable) -> Unit)? = null
        override var onPick: ((String) -> Unit)? = null
        override var onPickWater: ((String) -> Unit)? = null
        override var onWaterMove: ((String, Vec3) -> Boolean)? = null
        override var onTransform: ((String, TransformEdit) -> Boolean)? = null
        override val view: javax.swing.JComponent get() = component
        override fun setParams(params: SceneRenderParams) { current = params }
        override fun dispose() {}
    }

    private val mainScene = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()
    private fun file(): VirtualFile = myFixture.addFileToProject("w/scenes/Main Scene.scene", mainScene).virtualFile
    private fun waters(view: FakeView) = view.current.content.water.filterIsInstance<ValidWater>().map { it.settings }

    fun testTreeSelectionAndPickAreForwardedThroughTheHost() {
        val scene = file()
        var treeSelection: ((String) -> Unit)? = null
        var picked: String? = null
        val host = object : SceneViewHost {
            override fun listen(file: VirtualFile, parent: Disposable, selected: (String) -> Unit) {}
            override fun select(file: VirtualFile, entityId: String) {}
            override fun lightActions(file: VirtualFile, position: () -> Vec3) = DefaultActionGroup()
            override fun assetActions(file: VirtualFile, position: () -> Vec3) = DefaultActionGroup()
            override fun canAddLight(file: VirtualFile) = false
            override fun canAddAsset(file: VirtualFile) = false
            override fun listenWater(file: VirtualFile, parent: Disposable, selected: (String) -> Unit) { treeSelection = selected }
            override fun selectWater(file: VirtualFile, waterId: String) { picked = waterId }
        }
        lateinit var view: FakeView
        val editor = newSceneEditor(project, scene, host = host) { FakeView(it).also { v -> view = v } }
        try {
            treeSelection!!("abc")
            assertEquals("abc", view.selectedWater)
            view.onPickWater!!("xyz")
            assertEquals("xyz", picked)
        } finally {
            Disposer.dispose(editor)
        }
    }

    fun testAWaterMoveWritesOnlyTheChangedCoordinateAsOneUndoableCommand() {
        val scene = file()
        val id = WaterSceneEdits(project).add(scene, WaterPreset.LAKE, 1.5, 2.0, 3.25).id!!
        lateinit var view: FakeView
        val editor = newSceneEditor(project, scene) { FakeView(it).also { v -> view = v } }
        val commands = mutableListOf<String?>()
        val connection = project.messageBus.connect()
        connection.subscribe(com.intellij.openapi.command.CommandListener.TOPIC, object : com.intellij.openapi.command.CommandListener {
            override fun commandFinished(event: com.intellij.openapi.command.CommandEvent) {
                if (event.project == project) commands += event.commandName
            }
        })
        try {
            val before = textOf(scene)
            assertEquals(1, waters(view).size)
            // only the water level changes: x and z keep their saved number text
            assertTrue(view.onWaterMove!!(id, Vec3(1.5f, 4f, 3.25f)))
            val after = textOf(scene)
            assertEquals(listOf("Move Water"), commands)
            val water = before.indexOf("\"waterSurfaces\"")
            assertEquals(before.substring(0, water) + before.substring(water).replaceFirst("\"y\": 2", "\"y\": 4"), after)
            assertEquals(4.0, waters(view).single().level, 0.0)
            val undo = UndoManager.getInstance(project)
            assertTrue(undo.isUndoAvailable(editor))
            undo.undo(editor)
            assertEquals(before, textOf(scene))
            assertEquals(2.0, waters(view).single().level, 0.0)
        } finally {
            connection.disconnect()
            Disposer.dispose(editor)
        }
    }

    fun testAMoveToTheSamePlaceOrAGoneSurfaceWritesNothing() {
        val scene = file()
        val id = WaterSceneEdits(project).add(scene, WaterPreset.SEA, 0.0, 1.0, 0.0).id!!
        lateinit var view: FakeView
        val editor = newSceneEditor(project, scene) { FakeView(it).also { v -> view = v } }
        try {
            val before = textOf(scene)
            assertFalse(view.onWaterMove!!(id, Vec3(0f, 1f, 0f)))
            assertFalse(view.onWaterMove!!("missing", Vec3(5f, 5f, 5f)))
            assertEquals(before, textOf(scene))
            assertEquals(WaterEdit.Changed, WaterSceneEdits(project).remove(scene, id))
            assertFalse(view.onWaterMove!!(id, Vec3(9f, 9f, 9f)))
        } finally {
            Disposer.dispose(editor)
        }
    }

    fun testASceneWithoutWaterIsReadWithoutWritingAnything() {
        val scene = file()
        lateinit var view: FakeView
        val editor = newSceneEditor(project, scene) { FakeView(it).also { v -> view = v } }
        try {
            assertTrue(view.current.content.water.isEmpty())
            assertEquals(mainScene, textOf(scene))
        } finally {
            Disposer.dispose(editor)
        }
    }
}
