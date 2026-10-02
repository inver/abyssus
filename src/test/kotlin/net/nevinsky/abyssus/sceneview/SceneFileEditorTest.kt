/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SceneFileEditorTest : BasePlatformTestCase() {
    private val provider = SceneFileEditorProvider()

    private fun file(path: String, text: String = "{}") = myFixture.addFileToProject(path, text).virtualFile

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
        val editor = provider.createEditor(project, file("ok/Main.scene", """{"name":"x"}""")) as SceneFileEditor
        assertNotNull(editor.component)
        editor.dispose()
    }

    fun testLateGlFailureReplacesTabWithGlUnavailableMessage() {
        val editor = provider.createEditor(project, file("late/Main.scene", """{"name":"x"}""")) as SceneFileEditor
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
        var updates = 0
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

    private fun fakeEditor(path: String, text: String, views: MutableList<FakeView>): Pair<SceneFileEditor, com.intellij.openapi.vfs.VirtualFile> {
        val f = file(path, text)
        return SceneFileEditor(project, f) { p -> FakeView(p).also { views += it } } to f
    }

    private fun setText(f: com.intellij.openapi.vfs.VirtualFile, text: String) {
        val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(f)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { doc.setText(text) }
    }

    fun testSceneContentFollowsEdits() {
        val views = mutableListOf<FakeView>()
        val entity = """{"ecs":{"entities":{"1":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"m"}}}}}}}}"""
        val (editor, f) = fakeEditor("content/a.scene", "{}", views)
        try {
            assertTrue(views[0].current.content.models.isEmpty())
            setText(f, entity)
            assertEquals(listOf("m"), views[0].current.content.models.map { it.assetName })
            setText(f, "{}")
            assertTrue(views[0].current.content.models.isEmpty())
        } finally {
            editor.dispose()
        }
    }

    fun testRendersAndUpdatesInPlaceOnUnsavedEdits() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("live/a.scene", """{"name":"a"}""", views)
        try {
            assertEquals(1, views.size)
            assertNull(editor.statusText)
            setText(f, """{"name":"a","fogEnabled":true,"fog":{"color":{"r":1,"g":0,"b":0,"a":1},"density":0.5}}""")
            assertEquals(1, views.size)
            assertEquals(1, views[0].updates)
            assertNotNull(views[0].current.fog)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testBadEditDisposesViewThenFixRecreatesIt() {
        val views = mutableListOf<FakeView>()
        val (editor, f) = fakeEditor("live/b.scene", """{"name":"b"}""", views)
        try {
            setText(f, "{ nope")
            assertTrue(views[0].disposed)
            assertTrue(editor.statusText!!.startsWith("Cannot read scene"))
            setText(f, """{"name":"b"}""")
            assertEquals(2, views.size)
            assertFalse(views[1].disposed)
            assertNull(editor.statusText)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testDisposingEditorDisposesView() {
        val views = mutableListOf<FakeView>()
        val (editor, _) = fakeEditor("live/c.scene", """{"name":"c"}""", views)
        com.intellij.openapi.util.Disposer.dispose(editor)
        assertTrue(views.single().disposed)
    }

    fun testViewFailureShowsGlUnavailableAndDisposesView() {
        val views = mutableListOf<FakeView>()
        val (editor, _) = fakeEditor("live/d.scene", """{"name":"d"}""", views)
        try {
            views[0].onFailure!!.invoke(RuntimeException("no GL"))
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            assertTrue(editor.statusText!!, editor.statusText!!.startsWith("OpenGL scene is unavailable"))
            assertTrue(views[0].disposed)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testAbssEditRefreshesCamera() {
        val views = mutableListOf<FakeView>()
        val abss = myFixture.addFileToProject("Q/Q.abss", """{"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":-1},"position":{"x":1,"y":2,"z":3}}}""").virtualFile
        val scene = file("Q/scenes/a.scene", """{"name":"a"}""")
        val editor = SceneFileEditor(project, scene) { p -> FakeView(p).also { views += it } }
        try {
            assertEquals(1f, views[0].current.camera.position.x, 0f)
            setText(abss, """{"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":-1},"position":{"x":5,"y":2,"z":3}}}""")
            assertEquals(5f, views[0].current.camera.position.x, 0f)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    private val mainScene get() = java.io.File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    private fun textOf(f: com.intellij.openapi.vfs.VirtualFile) =
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(f)!!.text

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
}
