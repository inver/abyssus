/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.dto
import com.intellij.openapi.components.service
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.sceneview.SceneParamsSource

class NativeDocumentReadTest : BasePlatformTestCase() {
    fun testNativeAndUnsupportedSiblingsUseCurrentDocumentText() {
        val reader = service<SceneReader>()
        val native = """{"format":"abyssus","formatVersion":1,"name":"Native","ecs":{"entities":{}}}"""
        val good = myFixture.addFileToProject("p/scenes/Good.scene", native).virtualFile
        val bad = myFixture.addFileToProject("p/scenes/Legacy.scene", "{\"name\":\"Legacy\"}").virtualFile
        assertTrue(reader.read(good).success)
        assertFalse(reader.read(bad).success)
        val document = FileDocumentManager.getInstance().getDocument(good)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(native.replace("\"formatVersion\":1", "\"formatVersion\":2")) }
        assertFalse(reader.read(good).success)
        org.junit.Assert.assertThrows(Exception::class.java) { SceneParamsSource.editorText(reader).read(good) }
        assertEquals(native, String(good.contentsToByteArray(), good.charset))
    }
    fun testUnsupportedProjectDoesNotEnumerateItsNativeScene() {
        val file = myFixture.addFileToProject("p/Legacy.abss", "{\"name\":\"Legacy\"}").virtualFile
        myFixture.addFileToProject("p/scenes/Native.scene", """{"format":"abyssus","formatVersion":1,"name":"Native"}""")
        val result = project.service<ProjectReader>().read(file)
        assertFalse(result.success)
        assertNull(result.obj)
        assertNotNull(result.message)
    }
}
