/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import net.nevinsky.abyssus.lib.gdx.editor.document.SceneJson
import net.nevinsky.abyssus.plugin.projectView.ComponentTarget
import net.nevinsky.abyssus.lib.gdx.editor.document.RayMaterialIdentity
import net.nevinsky.abyssus.plugin.testMetaFiles
import net.nevinsky.abyssus.plugin.testPanelServices
import java.awt.Component
import java.awt.Container

class RayMaterialPropertiesTest : BasePlatformTestCase() {
    /** The model's material table as the loader reports it: two PBR materials, a repeated identifier and a Phong one. */
    private val table = listOf(RayMaterialIdentity("glass", true), RayMaterialIdentity("frame", true),
        RayMaterialIdentity("twin", true), RayMaterialIdentity("twin", true), RayMaterialIdentity("phong", false))
    private val services get() = testPanelServices(project) { _, name -> if (name == "bottle") table else null }

    private fun scene(overrides: String = ""): VirtualFile {
        myFixture.addFileToProject("p/p.abss", """{"format":"abyssus","formatVersion":1,"name":"p"}""")
        fun render(extra: String) = """{"renderable":{"kind":"asset","shaderKey":"pbr","asset":{"type":"MODEL","assetName":"bottle"}}$extra}"""
        return myFixture.addFileToProject("p/scenes/s.scene", SceneJson().pretty(SceneJson().parse("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{
            "0":{"components":{"RenderComponent":${render(overrides)}}},
            "1":{"components":{"RenderComponent":${render("")}}}}}}"""))).virtualFile
    }

    private fun view(file: VirtualFile, entity: String = "0") =
        EntityDetailsView(project, readEntityState(ComponentTarget(file, entity, null), services) as PanelState.EntityDetails, testMetaFiles())

    private fun named(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }
    private fun field(v: Component, key: String, id: String) = named(v, "optics-$key-$id") as JBTextField
    private fun error(v: Component, id: String) = (named(v, "optics-error-$id") as JBLabel).text
    private fun type(field: JBTextField, text: String) { field.text = text; field.postActionEvent() }
    private fun text(file: VirtualFile) = FileDocumentManager.getInstance().getDocument(file)!!.text
    private fun materials(file: VirtualFile, entity: String) =
        SceneJson().parse(text(file))["ecs"]["entities"][entity]["components"]["RenderComponent"]["rayTracingMaterials"]

    fun testTransmissionIsEditedInPercentAndStoredAsAFractionWithDefaultIorOmitted() {
        val file = scene()
        val v = view(file)
        assertEquals("0", field(v, "transmission", "glass").text)
        assertEquals("1.5", field(v, "ior", "glass").text)
        assertTrue((named(v, "optics-note") as JBLabel).text.contains("this entity only"))
        type(field(v, "transmission", "glass"), "100")
        assertEquals(1.0, materials(file, "0")["glass"]["transmission"].doubleValue(), 0.0)
        assertNull("the default IOR is not written", materials(file, "0")["glass"]["ior"])
        val again = view(file)
        assertEquals("100", field(again, "transmission", "glass").text)
        type(field(again, "transmission", "glass"), "45.5")
        assertEquals(.455, materials(file, "0")["glass"]["transmission"].doubleValue(), 1e-12)
        assertEquals("45.5", field(view(file), "transmission", "glass").text)
    }

    fun testOnlyEntitiesOwnInstanceChangesAndTheModelStaysUntouched() {
        val file = scene()
        type(field(view(file), "transmission", "glass"), "100")
        type(field(view(file), "ior", "glass"), "1.45")
        assertEquals(1.45, materials(file, "0")["glass"]["ior"].doubleValue(), 0.0)
        assertNull("the second instance of the model keeps its material", materials(file, "1"))
        assertEquals("0", field(view(file, "1"), "transmission", "glass").text)
    }

    fun testInvalidEditsKeepThePreviousValueAndWriteNothing() {
        val file = scene()
        val original = text(file)
        val v = view(file)
        for ((key, bad) in listOf("transmission" to "110", "transmission" to "-1", "transmission" to "glass", "ior" to "0", "ior" to "NaN", "ior" to "3.5")) {
            type(field(v, key, "glass"), bad)
            assertEquals("$key=$bad", original, text(file))
            assertTrue("$key=$bad", error(v, "glass").isNotBlank())
            assertEquals(if (key == "transmission") "0" else "1.5", field(v, key, "glass").text)
        }
    }

    fun testRepeatedAndNonPbrMaterialsAreExplainedInsteadOfEditable() {
        val v = view(scene())
        assertNull(named(v, "optics-transmission-twin"))
        assertTrue(error(v, "twin").contains("unique identifier"))
        assertNull(named(v, "optics-ior-phong"))
        assertTrue(error(v, "phong").contains("PBR"))
        assertNotNull(named(v, "optics-transmission-frame"))
    }

    fun testOverridesForMaterialsTheModelNoLongerHasAreShownAndKept() {
        val file = scene(""","rayTracingMaterials":{"old":{"transmission":1}}""")
        val v = view(file)
        assertTrue((named(v, "optics-unresolved-old") as JBLabel).text.contains("kept but not applied"))
        type(field(v, "transmission", "glass"), "50")
        assertEquals("the unresolved entry is never retargeted or dropped", 1, materials(file, "0")["old"]["transmission"].intValue())
        assertEquals(.5, materials(file, "0")["glass"]["transmission"].doubleValue(), 0.0)
    }

    fun testAnUnreadableModelIsExplained() {
        val file = scene()
        val missing = testPanelServices(project) { _, _ -> null }
        val v = EntityDetailsView(project, readEntityState(ComponentTarget(file, "0", null), missing) as PanelState.EntityDetails, testMetaFiles())
        assertTrue((named(v, "optics-problem") as JBLabel).text.contains("bottle"))
        assertNull(named(v, "optics-transmission-glass"))
    }

    fun testExternalChangesAndUndoRedoAreReadBack() {
        val file = scene()
        val original = text(file)
        val editor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        try {
            type(field(view(file), "transmission", "glass"), "100")
            val edited = text(file)
            val undo = UndoManager.getInstance(project)
            undo.undo(editor)
            assertEquals(original, text(file))
            assertEquals("0", field(view(file), "transmission", "glass").text)
            undo.redo(editor)
            assertEquals(edited, text(file))
            assertEquals("100", field(view(file), "transmission", "glass").text)
            // a stale editor built before an external change must not overwrite the newer value
            val stale = view(file)
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            WriteCommandAction.runWriteCommandAction(project) { document.setText(edited.replace(Regex("\"transmission\"\\s*:\\s*1(\\.0)?"), "\"transmission\": 0.25")) }
            assertEquals("25", field(view(file), "transmission", "glass").text)
            type(field(stale, "transmission", "glass"), "75")
            assertEquals(.25, materials(file, "0")["glass"]["transmission"].doubleValue(), 0.0)
            assertTrue(error(stale, "glass").contains("changed"))
        } finally { Disposer.dispose(editor) }
    }
}
