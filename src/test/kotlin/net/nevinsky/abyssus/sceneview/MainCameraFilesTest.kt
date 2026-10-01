package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MainCameraFilesTest : BasePlatformTestCase() {
    private fun abss(x: Int) =
        """{"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":-1},"position":{"x":$x,"y":2,"z":3}},"name":"P"}"""

    fun testSceneInScenesFolderUsesProjectCamera() {
        myFixture.addFileToProject("P/P.abss", abss(7))
        val scene = myFixture.addFileToProject("P/scenes/a.scene", "{}").virtualFile
        assertEquals(7f, MainCamera.forScene(scene).position.x, 0f)
    }

    fun testSceneOutsideScenesFolderIgnoresNeighbouringAbss() {
        myFixture.addFileToProject("foo/Game.abss", abss(7))
        val scene = myFixture.addFileToProject("foo/bar/x.scene", "{}").virtualFile
        assertEquals(CameraParams.DEFAULT, MainCamera.forScene(scene))
    }

    fun testUnsavedAbssEditIsUsed() {
        val abss = myFixture.addFileToProject("P/P.abss", abss(7)).virtualFile
        val scene = myFixture.addFileToProject("P/scenes/a.scene", "{}").virtualFile
        val doc = FileDocumentManager.getInstance().getDocument(abss)!!
        WriteCommandAction.runWriteCommandAction(project) { doc.setText(abss(9)) }
        assertEquals(9f, MainCamera.forScene(scene).position.x, 0f)
    }
}
