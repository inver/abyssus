/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.components.service
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.document.SceneDocument
import java.io.File

class AddFoliageActionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"
    private val metas get() = service<AbyssusCore>().assets.metaFiles
    private fun fixture() = myFixture.copyDirectoryToProject("Foliage", "Foliage")
    private fun scene() = myFixture.findFileInTempDir("Foliage/scenes/Main Scene.scene")!!
    private fun doc() = FileDocumentManager.getInstance().getDocument(scene())!!
    private fun tree() = SceneDocument(SceneJson().parse(doc().text))

    fun testAttachChangesOnlyTheTerrainComponentAndUndoRestoresText() {
        fixture()
        myFixture.openFileInEditor(scene())
        val before = doc().text
        assertEquals(listOf("foliage_meadow"), foliageChoices(project, scene(), "1", metas))
        assertTrue(setFoliage(project, scene(), "1", "foliage_meadow", metas))
        assertEquals("foliage_meadow", tree().components("1")!!["FoliageComponent"]["assetName"].textValue())
        val original = SceneJson().parse(before)
        val changed = SceneJson().parse(doc().text)
        (changed["ecs"]["1"]["components"] as com.fasterxml.jackson.databind.node.ObjectNode).remove("FoliageComponent")
        assertEquals(original, changed)
        UndoManager.getInstance(project).undo(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
        assertEquals(before, doc().text)
    }

    fun testReplacePreservesExtensionFields() {
        fixture()
        WriteCommandAction.runWriteCommandAction(project) {
            val root = SceneJson().parse(doc().text)
            SceneDocument(root).components("1")!!.putObject("FoliageComponent").put("assetName", "old").put("note", "keep")
            doc().setText(SceneJson().pretty(root))
        }
        assertTrue(setFoliage(project, scene(), "1", "foliage_meadow", metas))
        assertEquals("keep", tree().components("1")!!["FoliageComponent"]["note"].textValue())
    }

    fun testNoMatchingAssetAndModelEntityCannotAttach() {
        fixture()
        assertTrue(foliageChoices(project, scene(), "2", metas).isEmpty())
        assertFalse(setFoliage(project, scene(), "2", "foliage_meadow", metas))
        val folder = myFixture.findFileInTempDir("Foliage/assets/foliage_meadow")!!
        WriteCommandAction.runWriteCommandAction(project) { folder.delete(this) }
        assertTrue(foliageChoices(project, scene(), "1", metas).isEmpty())
        assertFalse(setFoliage(project, scene(), "1", "foliage_meadow", metas))
    }
}
