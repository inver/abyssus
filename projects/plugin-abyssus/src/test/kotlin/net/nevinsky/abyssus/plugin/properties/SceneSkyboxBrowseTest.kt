/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextField
import java.awt.Component
import java.awt.Container
import javax.swing.AbstractButton
import net.nevinsky.abyssus.plugin.SceneRayControls
import net.nevinsky.abyssus.plugin.projectView.setSkybox

/** The scene `skybox` field of Abyssus Properties and its Browse button (the tree row no longer has a Choose button). */
class SceneSkyboxBrowseTest : BasePlatformTestCase() {
    private fun named(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    private fun scene(path: String): VirtualFile {
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        return myFixture.addFileToProject(
            path,
            """{"format":"abyssus","formatVersion":1,"name":"Main","skyboxName":"skybox_physical","ecs":{}}""",
        ).virtualFile
    }

    private fun view(file: VirtualFile, browse: (VirtualFile) -> Boolean = { false }) = SceneDetailsView(
        SceneRayControls(project) { _, _ -> error("no view") },
        readSceneState(file, "Main") as PanelState.UISceneState,
        testRootDisposable,
        browseSkybox = browse,
    )

    fun testProjectSceneShowsTheFieldAndABrowseButton() {
        val view = view(scene("p/scenes/Main.scene"))
        assertEquals("skybox_physical", (named(view, "scene-skybox") as JBTextField).text)
        val browse = named(view, "scene-skybox-browse") as AbstractButton
        assertFalse(browse.toolTipText.isNullOrBlank())
        assertEquals(browse.toolTipText, browse.accessibleContext.accessibleName)
        assertEquals(26, browse.preferredSize.height)
    }

    fun testStandaloneSceneHasNoBrowseButton() {
        val view = view(scene("alone/Alone.scene"))
        assertNotNull(named(view, "scene-skybox"))
        assertNull(named(view, "scene-skybox-browse"))
    }

    fun testNullSkyboxReadsNone() {
        val file = myFixture.addFileToProject("alone/N.scene", """{"format":"abyssus","formatVersion":1,"skyboxName":null,"ecs":{}}""").virtualFile
        assertEquals("none", (named(view(file), "scene-skybox") as JBTextField).text)
    }

    fun testAssignmentUpdatesTheFieldWritesOnlySkyboxNameAndUndoRestoresIt() {
        val file = scene("p/scenes/Main.scene")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        myFixture.openFileInEditor(file)
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        // opening the scene in a text editor pretty-prints it (SceneFormatListener); the baseline is the text as opened
        val before = document.text
        val view = view(file) { setSkybox(project, it, "skybox_default") }
        (named(view, "scene-skybox-browse") as AbstractButton).doClick()
        assertEquals("skybox_default", (named(view, "scene-skybox") as JBTextField).text)
        assertEquals(before.replace("skybox_physical", "skybox_default"), document.text)
        val undo = UndoManager.getInstance(project)
        undo.undo(editor)
        assertEquals(before, document.text)
    }
}
