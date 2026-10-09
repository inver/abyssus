/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson

import net.nevinsky.abyssus.plugin.SceneRayControls

import net.nevinsky.abyssus.plugin.SceneFileEditorProvider

import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Quat

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.testPanelServices
import net.nevinsky.abyssus.plugin.testMetaFiles

class SceneFileEditorTest : BasePlatformTestCase() {
    private val provider = SceneFileEditorProvider()

    private fun file(path: String, text: String = """{"format":"abyssus","formatVersion":1}""") = myFixture.addFileToProject(path, text).virtualFile

    fun testAcceptsOnlyExactSceneExtension() {
        assertTrue(provider.accept(project, file("a/Main.scene")))
        assertFalse(provider.accept(project, file("a/Main.SCENE")))
        assertFalse(provider.accept(project, file("a/Main.scene.bak")))
        assertFalse(provider.accept(project, file("a/Main.abss")))
        assertFalse(provider.accept(project, myFixture.tempDirFixture.findOrCreateDir("dir.scene")))
    }

    fun testIsPlacedAfterTextEditor() {
        assertEquals(FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR, provider.policy)
    }

    fun testMalformedSceneShowsParseErrorInsteadOfThrowing() {
        for (text in listOf("not json", "[]", "")) {
            val editor = provider.createEditor(project, file("bad/${text.length}.scene", text)) as SceneFileEditor
            try {
                assertNotNull(editor.component)
                val status = editor.statusText
                assertNotNull("status for '$text'", status)
                assertTrue(status!!, status.startsWith("Cannot read scene"))
            } finally {
                editor.dispose()
            }
        }
    }

    fun testValidSceneEditorCreatesAndDisposesWithoutThrowing() {
        // In a headless/GL-less environment the tab shows the glUnavailable message; either way no exception.
        val editor = provider.createEditor(project, file("ok/Main.scene", """{"format":"abyssus","formatVersion":1,"name":"x"}""")) as SceneFileEditor
        assertNotNull(editor.component)
        editor.dispose()
    }

    fun testLateGlFailureReplacesTabWithGlUnavailableMessage() {
        val editor = provider.createEditor(project, file("late/Main.scene", """{"format":"abyssus","formatVersion":1,"name":"x"}""")) as SceneFileEditor
        try {
            editor.showGlFailure(RuntimeException("no GL 3.2"))
            val status = editor.statusText
            assertNotNull(status)
            assertTrue(status!!, status.startsWith("OpenGL scene is unavailable") && status.contains("no GL 3.2"))
        } finally {
            editor.dispose()
        }
    }

    private class FakeView(var current: SceneRenderParams) : SceneView {
        val component = javax.swing.JPanel()
        var disposed = false
        var stops = 0
        override fun stopPlay() { stops++ }
        var updates = 0
        var selected: String? = null
        override fun selectEntity(entityId: String) { selected = entityId }
        override var onFailure: ((Throwable) -> Unit)? = null
        override var onPick: ((String) -> Unit)? = null
        override var onTransform: ((String, TransformEdit) -> Boolean)? = null
        override val view: javax.swing.JComponent get() = component
        override fun setParams(params: SceneRenderParams) {
            current = params
            updates++
        }
        override fun dispose() {
            disposed = true
        }
    }

    fun testTreeSelectionAndPickAreForwardedThroughHost() {
        val scene = file("host/Main.scene")
        var treeSelection: ((String) -> Unit)? = null
        var picked: String? = null
        val host = object : SceneViewHost {
            override fun listen(file: com.intellij.openapi.vfs.VirtualFile, parent: com.intellij.openapi.Disposable, selected: (String) -> Unit) { treeSelection = selected }
            override fun select(file: com.intellij.openapi.vfs.VirtualFile, entityId: String) { picked = entityId }
            override fun lightActions(file: com.intellij.openapi.vfs.VirtualFile, position: () -> Vec3) = com.intellij.openapi.actionSystem.DefaultActionGroup()
            override fun assetActions(file: com.intellij.openapi.vfs.VirtualFile, position: () -> Vec3) = com.intellij.openapi.actionSystem.DefaultActionGroup()
            override fun canAddLight(file: com.intellij.openapi.vfs.VirtualFile) = false
            override fun canAddAsset(file: com.intellij.openapi.vfs.VirtualFile) = false
        }
        lateinit var view: FakeView
        val editor = newSceneEditor(project, scene, host = host) { params -> FakeView(params).also { view = it } }
        try {
            treeSelection!!("12")
            assertEquals("12", view.selected)
            view.onPick!!("7")
            assertEquals("7", picked)
            val facts: net.nevinsky.abyssus.plugin.facts.SceneFacts<*> = project.getService(net.nevinsky.abyssus.plugin.SceneRayControls::class.java)
            assertEquals(view.current.content, facts.content(scene.path))
            assertEquals("7", facts.selection(scene.path))
        } finally {
            editor.dispose()
        }
        assertNull(project.getService(net.nevinsky.abyssus.plugin.SceneRayControls::class.java).content(scene.path))
    }

    private fun fakeEditor(path: String, text: String, views: MutableList<FakeView>): Pair<SceneFileEditor, com.intellij.openapi.vfs.VirtualFile> {
        val f = file(path, text)
        return newSceneEditor(project, f) { p -> FakeView(p).also { views += it } } to f
    }

    private fun setText(f: com.intellij.openapi.vfs.VirtualFile, text: String) {
        val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(f)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { doc.setText(text) }
    }

    /** Typing in a text tab is shown after a pause: this runs the re-read that is waiting for it. */
    private fun SceneFileEditor.afterThePause() = flushPendingReload()

    fun testSceneContentFollowsEdits() {
        val views = mutableListOf<FakeView>()
        val entity = """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"1":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"m"}}}}}}}}"""
        val (editor, f) = fakeEditor("content/a.scene", """{"format":"abyssus","formatVersion":1}""", views)
        try {
            assertTrue(views[0].current.content.models.isEmpty())
            setText(f, entity)
            editor.afterThePause()
            assertEquals(listOf("m"), views[0].current.content.models.map { it.assetName })
            setText(f, """{"format":"abyssus","formatVersion":1}""")
            editor.afterThePause()
            assertTrue(views[0].current.content.models.isEmpty())
        } finally {
            editor.dispose()
        }
    }

    fun testAddedLightSelectsInViewEvenWithoutATreeRow() {
        val views = mutableListOf<FakeView>()
        val (editor, file) = fakeEditor("selection/new-light.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""", views)
        try {
            val actions = net.nevinsky.abyssus.plugin.projectView.AddLightGroup(project, file, { Vec3(0f, 0f, 0f) }).getChildren(null)
            actions[0].actionPerformed(com.intellij.testFramework.TestActionEvent.createTestEvent(actions[0]))
            assertEquals("0", views.single().selected)
            assertEquals("0", net.nevinsky.abyssus.plugin.projectView.componentTargetOf(net.nevinsky.abyssus.plugin.projectView.AbyssusSelection.of(project).current)?.entityId)
        } finally { editor.dispose() }
    }

    fun testTreeSelectionReachesSceneView() {
        val views = mutableListOf<FakeView>()
        val text = """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"7":{"components":{"PositionComponent":{}}}}}}"""
        val (editor, file) = fakeEditor("selection/a.scene", text, views)
        try {
            val entry = net.nevinsky.abyssus.plugin.projectView.DtoRow("7", net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(text)["ecs"]["entities"]["7"])
            val node = net.nevinsky.abyssus.plugin.projectView.DtoEntryNode(project, file.path, entry, file, listOf("ecs", "entities"))
            net.nevinsky.abyssus.plugin.projectView.AbyssusSelection.of(project).select(node)
            assertEquals("7", views.single().selected)
        } finally { editor.dispose() }
    }

    fun testRendersAndUpdatesInPlaceOnUnsavedEdits() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("live/a.scene", """{"format":"abyssus","formatVersion":1,"name":"a"}""", views)
        try {
            assertEquals(1, views.size)
            assertNull(editor.statusText)
            setText(f, """{"format":"abyssus","formatVersion":1,"name":"a","fogEnabled":true,"fog":{"color":{"r":1,"g":0,"b":0,"a":1},"density":0.5}}""")
            editor.afterThePause()
            assertEquals(1, views.size)
            assertEquals(1, views[0].updates)
            assertNotNull(views[0].current.fog)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testBadEditDisposesViewThenFixRecreatesIt() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("live/b.scene", """{"format":"abyssus","formatVersion":1,"name":"b"}""", views)
        try {
            setText(f, "{ nope")
            editor.afterThePause()
            assertTrue(views[0].disposed)
            assertTrue(editor.statusText!!.startsWith("Cannot read scene"))
            setText(f, """{"format":"abyssus","formatVersion":1,"name":"b"}""")
            editor.afterThePause()
            assertEquals(2, views.size)
            assertFalse(views[1].disposed)
            assertNull(editor.statusText)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testDisposingEditorDisposesView() {
        val views = mutableListOf<FakeView>()
        val (editor, _) = fakeEditor("live/c.scene", """{"format":"abyssus","formatVersion":1,"name":"c"}""", views)
        com.intellij.openapi.util.Disposer.dispose(editor)
        assertTrue(views.single().disposed)
    }

    fun testViewFailureShowsGlUnavailableAndDisposesView() {
        val views = mutableListOf<FakeView>()
        val (editor, _) = fakeEditor("live/d.scene", """{"format":"abyssus","formatVersion":1,"name":"d"}""", views)
        try {
            views[0].onFailure!!.invoke(RuntimeException("no GL"))
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            assertTrue(editor.statusText!!, editor.statusText!!.startsWith("OpenGL scene is unavailable"))
            assertTrue(views[0].disposed)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testPhysicsOnlyProjectEditsDoNotStopOrReloadUnrelatedSimulation() {
        val abss = myFixture.addFileToProject("Gate/Gate.abss", """{"format":"abyssus","formatVersion":1}""").virtualFile
        val scene = file("Gate/scenes/a.scene", """{"format":"abyssus","formatVersion":1,"name":"a"}""")
        lateinit var view: FakeView
        val editor = newSceneEditor(project, scene) { p -> FakeView(p).also { view = it } }
        try {
            val settings = project.getService(net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings::class.java)
            settings.setPhysicsEnabled(abss, true)
            settings.setPhysicsEnabled(abss, false)
            editor.afterThePause()
            assertEquals(0, view.stops)
            assertEquals(0, view.updates)
            setText(abss, """{"format":"abyssus","formatVersion":1,"name":"changed"}""")
            editor.afterThePause()
            assertTrue(view.stops > 0)
            assertTrue(view.updates > 0)
        } finally { com.intellij.openapi.util.Disposer.dispose(editor) }
    }

    fun testAbssEditRefreshesCamera() {
        val views = mutableListOf<FakeView>()
        val abss = myFixture.addFileToProject("Q/Q.abss", """{"format":"abyssus","formatVersion":1,"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":-1},"position":{"x":1,"y":2,"z":3}}}""").virtualFile
        val scene = file("Q/scenes/a.scene", """{"format":"abyssus","formatVersion":1,"name":"a"}""")
        val editor = newSceneEditor(project, scene) { p -> FakeView(p).also { views += it } }
        try {
            assertEquals(1f, views[0].current.camera.position.x, 0f)
            setText(abss, """{"format":"abyssus","formatVersion":1,"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":-1},"position":{"x":5,"y":2,"z":3}}}""")
            editor.afterThePause()
            assertEquals(5f, views[0].current.camera.position.x, 0f)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    // Pin the test copy to the native writer's indentation before checking one-line edits and exact Undo.
    private val mainScene get() = net.nevinsky.abyssus.lib.core.editor.document.SceneJson().pretty(
        net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(java.io.File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()),
    )

    private fun textOf(f: com.intellij.openapi.vfs.VirtualFile) =
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(f)!!.text

    fun testComponentEditsReachTheOpenView() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("edit/Main Scene.scene", mainScene, views)
        try {
            val old = views[0].current.content.models.first { it.entityId == "0" }.transform.position
            val result = net.nevinsky.abyssus.plugin.projectView.SceneComponentEdits
                .update(project, f, "0", "PositionComponent", "localPosition.x", (old.x + 3f).toString(), testMetaFiles())
            assertEquals(net.nevinsky.abyssus.lib.core.editor.components.EditResult.Changed, result)
            assertEquals(old.x + 3f, views[0].current.content.models.first { it.entityId == "0" }.transform.position.x, 1e-4f)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testMovingAnEntityWritesOnlyItsPositionAndUndoRestoresTheFile() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("move/Main Scene.scene", mainScene, views)
        try {
            val before = textOf(f)
            val old = views[0].current.content.models.first { it.entityId == "0" }.transform.position
            val moved = Vec3(old.x + 2f, old.y, old.z)
            assertTrue(views[0].onTransform!!.invoke("0", TransformEdit(position = moved)))
            val after = textOf(f)
            val changed = before.lines().indices.filter { before.lines()[it] != after.lines()[it] }
            assertEquals(1, changed.size)
            assertEquals(moved.x, views[0].current.content.models.first { it.entityId == "0" }.transform.position.x, 1e-5f)
            // the scene view tab's own Undo reaches the edit
            val undo = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
            assertTrue(undo.isUndoAvailable(editor))
            undo.undo(editor)
            assertEquals(before, textOf(f))
            assertEquals(old.x, views[0].current.content.models.first { it.entityId == "0" }.transform.position.x, 1e-5f)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testDroppingAnEntityIsOneMoveCommandAndUndoRestoresTheViewAndFile() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("drop/Main Scene.scene", mainScene, views)
        val connection = project.messageBus.connect()
        val commands = mutableListOf<String?>()
        connection.subscribe(com.intellij.openapi.command.CommandListener.TOPIC, object : com.intellij.openapi.command.CommandListener {
            override fun commandFinished(event: com.intellij.openapi.command.CommandEvent) {
                if (event.project == project) commands += event.commandName
            }
        })
        try {
            val before = textOf(f)
            val old = views[0].current.content.models.first { it.entityId == "0" }.transform.position
            val dropped = old.copy(y = old.y + 1.5f)
            assertTrue(editor.applyTransform("0", TransformEdit(position = dropped)))
            assertEquals(listOf("Move Entity"), commands)
            assertEquals(1, before.lines().indices.count { before.lines()[it] != textOf(f).lines()[it] })
            assertEquals(dropped, views[0].current.content.models.first { it.entityId == "0" }.transform.position)
            val undo = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
            assertTrue(undo.isUndoAvailable(editor))
            undo.undo(editor)
            assertEquals(before, textOf(f))
            assertEquals(old, views[0].current.content.models.first { it.entityId == "0" }.transform.position)
        } finally {
            connection.disconnect()
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testATransformThatChangesNothingIsNotWritten() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("same/Main Scene.scene", mainScene, views)
        try {
            val before = textOf(f)
            val old = views[0].current.content.models.first { it.entityId == "0" }.transform.position
            assertFalse(views[0].onTransform!!.invoke("0", TransformEdit(position = old)))
            assertFalse(views[0].onTransform!!.invoke("99", TransformEdit(position = Vec3(1f, 1f, 1f))))
            assertEquals(before, textOf(f))
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testRotatingIsUndoable() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("rot/Main Scene.scene", mainScene, views)
        try {
            val before = textOf(f)
            assertTrue(views[0].onTransform!!.invoke("0", TransformEdit(rotation = Quat(0f, 0.7071f, 0f, 0.7071f))))
            assertTrue(textOf(f).contains("localRotation"))
            val undo = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
            undo.undo(editor)
            assertEquals(before, textOf(f))
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    /** A copy of Untitled's `Main Scene` (the shared fixture is not touched) with a LightComponent on 7 and a spotlight 8. */
    private fun sceneWithOmittedLightValues(): String {
        val root = net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(
            java.io.File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText(),
        ) as com.fasterxml.jackson.databind.node.ObjectNode
        val entities = root.get("ecs") as com.fasterxml.jackson.databind.node.ObjectNode
        (entities.get("7").get("components") as com.fasterxml.jackson.databind.node.ObjectNode).set<com.fasterxml.jackson.databind.JsonNode>(
            "LightComponent",
            net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse("""{"light":{"color":{"r":1,"g":0.96,"b":0.84,"a":1},"intensity":1.2}}"""),
        )
        entities.set<com.fasterxml.jackson.databind.JsonNode>(
            "8",
            net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(
                """{"archetype":1,"components":{"NameComponent":{"name":"Spot Light 8"},"TypeComponent":{"type":"LIGHT_SPOT"},
                "PositionComponent":{"localPosition":{"x":1,"y":5,"z":2}},
                "LightComponent":{"light":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1}}}}""",
            ),
        )
        return net.nevinsky.abyssus.lib.core.editor.document.SceneJson().compact(root)
    }

    fun testOpeningTheViewAndSelectingLightsWithOmittedValuesLeavesTheTextAsItWas() {
        val views = mutableListOf<FakeView>()
        val prepared = sceneWithOmittedLightValues()
        val (editor, f) = fakeEditor("omitted/Main Scene.scene", prepared, views)
        try {
            val lights = views.single().current.content.lights.associateBy { it.entityId }
            assertEquals(1.2f, lights.getValue("7").intensity, 0f)
            assertEquals(100f, lights.getValue("8").range, 0f)
            assertEquals(45f, lights.getValue("8").coneAngle, 0f)
            assertEquals(0.2f, lights.getValue("8").edgeSoftness, 0f)
            for (id in listOf("7", "8")) {
                val entry = net.nevinsky.abyssus.plugin.projectView.DtoRow(id, net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(prepared)["ecs"][id])
                val node = net.nevinsky.abyssus.plugin.projectView.DtoEntryNode(project, f.path, entry, f, listOf("ecs"))
                net.nevinsky.abyssus.plugin.projectView.AbyssusSelection.of(project).select(node)
                assertEquals(id, views.single().selected)
                // the Properties panel reads the same entity: opening it must not write either
                net.nevinsky.abyssus.plugin.properties.readEntityState(net.nevinsky.abyssus.plugin.projectView.ComponentTarget(f, id, null), testPanelServices(project))
            }
            assertEquals(prepared, textOf(f))
            assertFalse(com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().isFileModified(f))
        } finally {
            editor.dispose()
        }
    }

    fun testTypingBurstReloadsOnce() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("burst/a.scene", """{"format":"abyssus","formatVersion":1,"name":"a"}""", views)
        try {
            val before = views[0].updates
            for (i in 1..10) setText(f, """{"format":"abyssus","formatVersion":1,"skyboxEnabled":true,"skyboxName":"s$i"}""")
            assertEquals("nothing is re-read while typing", before, views[0].updates)
            editor.afterThePause()
            assertEquals(before + 1, views[0].updates)
            assertEquals("s10", views[0].current.content.skybox)
            editor.afterThePause()
            assertEquals("a pause with nothing pending reads nothing", before + 1, views[0].updates)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testInvalidIntermediateTextNeverShowsError() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("burst/b.scene", """{"format":"abyssus","formatVersion":1,"name":"a"}""", views)
        try {
            setText(f, """{"name":""")
            assertNull("the half-typed text is not read", editor.statusText)
            setText(f, """{"format":"abyssus","formatVersion":1,"skyboxEnabled":true,"skyboxName":"fixed"}""")
            editor.afterThePause()
            assertNull(editor.statusText)
            assertFalse(views[0].disposed)
            assertEquals("fixed", views[0].current.content.skybox)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testDisposingWithAPendingReloadDeliversNothing() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("burst/c.scene", """{"format":"abyssus","formatVersion":1,"name":"a"}""", views)
        val updates = views[0].updates
        setText(f, """{"format":"abyssus","formatVersion":1,"name":"late"}""")
        com.intellij.openapi.util.Disposer.dispose(editor)
        editor.afterThePause()
        assertEquals(updates, views[0].updates)
        assertTrue(views[0].disposed)
    }
}
