/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.document.SceneRayField
import net.nevinsky.abyssus.lib.core.editor.document.RayDataEdit
import net.nevinsky.abyssus.lib.core.editor.document.RayMaterialIdentity
import net.nevinsky.abyssus.lib.core.editor.document.RayOpticalField
import net.nevinsky.abyssus.plugin.filetype.SceneRayEdits

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson

class SceneRaySettingsEditTest : BasePlatformTestCase() {
    private val original=SceneJson().pretty(SceneJson().parse("""{"format":"abyssus","formatVersion":1,"rayTracing":{"maxReflectionBounces":3},"other":2.500,"ecs":{"entities":{"0":{"components":{"RenderComponent":{}}}}}}"""))
    fun testSceneEditsAreOneCommandWithExactUndoRedoAndNoOtherWrites() {
        val file=myFixture.addFileToProject("p/scenes/main.scene",original).virtualFile
        val projectFile=myFixture.addFileToProject("p/p.abss","untouched project").virtualFile
        val meta=myFixture.addFileToProject("p/assets/model/meta.json","untouched metadata").virtualFile
        val model=myFixture.addFileToProject("p/assets/model/model.gltf","untouched model").virtualFile
        myFixture.openFileInEditor(file)
        val editor=TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val expected=SceneJson().parse(original)["rayTracing"]["maxReflectionBounces"]
        assertEquals(RayDataEdit.Changed,SceneRayEdits.setting(project,file,SceneRayField.REFLECTIONS,expected,"2"))
        val document=FileDocumentManager.getInstance().getDocument(file)!!
        val changed=original.replace("Bounces\": 3","Bounces\": 2")
        assertEquals(changed,document.text)
        val undo=UndoManager.getInstance(project);undo.undo(editor)
        assertEquals(original,document.text);undo.redo(editor);assertEquals(changed,document.text)
        assertEquals("untouched project",String(projectFile.contentsToByteArray()))
        assertEquals("untouched metadata",String(meta.contentsToByteArray()))
        assertEquals("untouched model",String(model.contentsToByteArray()))
    }
    fun testStaleInvalidEqualAndUnsupportedEditsWriteNothing() {
        val file=myFixture.addFileToProject("s.scene",original).virtualFile
        val expected=SceneJson().parse(original)["rayTracing"]["maxReflectionBounces"]
        assertEquals(RayDataEdit.Conflict,SceneRayEdits.setting(project,file,SceneRayField.REFLECTIONS,null,"4"))
        assertEquals(RayDataEdit.Unchanged,SceneRayEdits.setting(project,file,SceneRayField.REFLECTIONS,expected,"3"))
        assertTrue(SceneRayEdits.setting(project,file,SceneRayField.REFLECTIONS,expected,"17") is RayDataEdit.Rejected)
        assertEquals(original,FileDocumentManager.getInstance().getDocument(file)!!.text)
        val legacy=myFixture.addFileToProject("legacy.scene","{}").virtualFile
        assertTrue(SceneRayEdits.setting(project,legacy,SceneRayField.REFLECTIONS,null,"2") is RayDataEdit.Rejected)
        assertEquals("{}",FileDocumentManager.getInstance().getDocument(legacy)!!.text)
    }
    fun testOpticalOverrideHasExactUndoAndExpectedValueChecks() {
        val file=myFixture.addFileToProject("s.scene",original).virtualFile
        myFixture.openFileInEditor(file)
        val identities=listOf(RayMaterialIdentity("glass",true))
        assertEquals(RayDataEdit.Changed,SceneRayEdits.material(project,file,"0","glass",RayOpticalField.TRANSMISSION,null,"1",identities))
        val document=FileDocumentManager.getInstance().getDocument(file)!!
        val edited=document.text
        assertEquals(RayDataEdit.Conflict,SceneRayEdits.material(project,file,"0","glass",RayOpticalField.TRANSMISSION,null,"0.5",identities))
        assertEquals(edited,document.text)
        val editor=TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        UndoManager.getInstance(project).undo(editor);assertEquals(original,document.text)
        UndoManager.getInstance(project).redo(editor);assertEquals(edited,document.text)
    }
}
