/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.gdx.editor.scene.CameraParams
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SceneParamsSourceTest : BasePlatformTestCase() {
    private val editorText get() = SceneParamsSource.editorText(com.intellij.openapi.components.service<net.nevinsky.abyssus.plugin.dto.SceneReader>())

    private fun cameraOf(scene: com.intellij.openapi.vfs.VirtualFile) = editorText.read(scene).camera

    private fun abss(x: Int) =
        """{"format":"abyssus","formatVersion":1,"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":-1},"position":{"x":$x,"y":2,"z":3}},"name":"P"}"""

    fun testSceneInScenesFolderUsesProjectCamera() {
        myFixture.addFileToProject("P/P.abss", abss(7))
        val scene = myFixture.addFileToProject("P/scenes/a.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile
        assertEquals(7f, cameraOf(scene).position.x, 0f)
    }

    fun testSceneOutsideScenesFolderIgnoresNeighbouringAbss() {
        myFixture.addFileToProject("foo/Game.abss", abss(7))
        val scene = myFixture.addFileToProject("foo/bar/x.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile
        assertEquals(CameraParams.DEFAULT, cameraOf(scene))
    }

    fun testUnsavedAbssEditIsUsed() {
        val abss = myFixture.addFileToProject("P/P.abss", abss(7)).virtualFile
        val scene = myFixture.addFileToProject("P/scenes/a.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile
        val doc = FileDocumentManager.getInstance().getDocument(abss)!!
        WriteCommandAction.runWriteCommandAction(project) { doc.setText(abss(9)) }
        assertEquals(9f, cameraOf(scene).position.x, 0f)
    }

    fun testProjectDirAndSourcesFollowTheLayout() {
        val abss = myFixture.addFileToProject("P/P.abss", abss(7)).virtualFile
        val scene = myFixture.addFileToProject("P/scenes/a.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile
        assertEquals(abss.parent.path, editorText.read(scene).projectDir!!.path)
        assertEquals(setOf(scene, abss), editorText.sources(scene))
    }
}
