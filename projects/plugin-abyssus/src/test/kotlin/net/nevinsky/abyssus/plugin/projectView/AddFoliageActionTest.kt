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
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.lib.core.ecs.component.FoliageComponent
import net.nevinsky.abyssus.plugin.editSceneText

class AddFoliageActionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"
    private val metas get() = service<AbyssusPluginCtx>().assets.metaFiles
    private fun fixture() = myFixture.copyDirectoryToProject("Foliage", "Foliage")
    private fun scene() = myFixture.findFileInTempDir("Foliage/scenes/Main Scene.scene")!!
    private fun doc() = FileDocumentManager.getInstance().getDocument(scene())!!
    private fun scene1() = net.nevinsky.abyssus.plugin.parsedScene(doc().text).entity(1)!!

    fun testAttachChangesOnlyTheTerrainComponentAndUndoRestoresText() {
        fixture()
        myFixture.openFileInEditor(scene())
        val before = doc().text
        assertEquals(listOf("foliage_meadow"), foliageChoices(project, scene(), 1L, metas))
        assertTrue(setFoliage(project, scene(), 1L, "foliage_meadow", metas))
        assertEquals("foliage_meadow", scene1().component(FoliageComponent::class.java)!!.assetName)
        val changed = net.nevinsky.abyssus.plugin.parsedScene(doc().text)
        changed.entity(1)!!.components.removeAll { it is FoliageComponent }
        assertEquals(net.nevinsky.abyssus.plugin.prettyScene(before), net.nevinsky.abyssus.plugin.printScene(changed))
        UndoManager.getInstance(project).undo(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
        assertEquals(before, doc().text)
    }

    fun testReplacePreservesExtensionFields() {
        fixture()
        val json = service<AbyssusPluginCtx>().documents.json
        editSceneText(project, doc()) { scene ->
            val components = scene.entity(1)!!.components
            components.removeAll { it is FoliageComponent }
            components.add(json.parse("""{"assetName":"old","note":"keep"}""", FoliageComponent::class.java))
        }
        assertTrue(setFoliage(project, scene(), 1L, "foliage_meadow", metas))
        assertEquals("\"keep\"", scene1().component(FoliageComponent::class.java)!!.extras["note"].toString())
    }

    fun testNoMatchingAssetAndModelEntityCannotAttach() {
        fixture()
        assertTrue(foliageChoices(project, scene(), 2L, metas).isEmpty())
        assertFalse(setFoliage(project, scene(), 2L, "foliage_meadow", metas))
        val folder = myFixture.findFileInTempDir("Foliage/assets/foliage_meadow")!!
        WriteCommandAction.runWriteCommandAction(project) { folder.delete(this) }
        assertTrue(foliageChoices(project, scene(), 1L, metas).isEmpty())
        assertFalse(setFoliage(project, scene(), 1L, "foliage_meadow", metas))
    }
}
