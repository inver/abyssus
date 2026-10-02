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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SceneParamsSourceTest : BasePlatformTestCase() {
    private fun cameraOf(scene: com.intellij.openapi.vfs.VirtualFile) = SceneParamsSource.EDITOR_TEXT.read(scene).camera

    private fun abss(x: Int) =
        """{"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":-1},"position":{"x":$x,"y":2,"z":3}},"name":"P"}"""

    fun testSceneInScenesFolderUsesProjectCamera() {
        myFixture.addFileToProject("P/P.abss", abss(7))
        val scene = myFixture.addFileToProject("P/scenes/a.scene", "{}").virtualFile
        assertEquals(7f, cameraOf(scene).position.x, 0f)
    }

    fun testSceneOutsideScenesFolderIgnoresNeighbouringAbss() {
        myFixture.addFileToProject("foo/Game.abss", abss(7))
        val scene = myFixture.addFileToProject("foo/bar/x.scene", "{}").virtualFile
        assertEquals(CameraParams.DEFAULT, cameraOf(scene))
    }

    fun testUnsavedAbssEditIsUsed() {
        val abss = myFixture.addFileToProject("P/P.abss", abss(7)).virtualFile
        val scene = myFixture.addFileToProject("P/scenes/a.scene", "{}").virtualFile
        val doc = FileDocumentManager.getInstance().getDocument(abss)!!
        WriteCommandAction.runWriteCommandAction(project) { doc.setText(abss(9)) }
        assertEquals(9f, cameraOf(scene).position.x, 0f)
    }

    fun testProjectDirAndSourcesFollowTheLayout() {
        val abss = myFixture.addFileToProject("P/P.abss", abss(7)).virtualFile
        val scene = myFixture.addFileToProject("P/scenes/a.scene", "{}").virtualFile
        assertEquals(abss.parent.path, SceneParamsSource.EDITOR_TEXT.read(scene).projectDir!!.path)
        assertEquals(setOf(scene, abss), SceneParamsSource.EDITOR_TEXT.sources(scene))
    }
}
