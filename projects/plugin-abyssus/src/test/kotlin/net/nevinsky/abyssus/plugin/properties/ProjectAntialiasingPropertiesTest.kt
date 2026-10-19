/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.properties

import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings
import net.nevinsky.abyssus.plugin.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusRootNode
import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel
import net.nevinsky.abyssus.plugin.testPanelServices
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.JComboBox
import javax.xml.parsers.DocumentBuilderFactory

class ProjectAntialiasingPropertiesTest : BasePlatformTestCase() {
    private fun setupProject(version: Int = 1): com.intellij.openapi.vfs.VirtualFile {
        val f = myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":$version,"name":"P","future":-0.00}""").virtualFile
        myFixture.addFileToProject("p/scenes/S.scene", """{"format":"abyssus","formatVersion":1,"name":"S"}""")
        myFixture.addFileToProject("p/assets/m/meta.json", """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{"file":"m.glb"}}""")
        return f
    }
    private fun panel(): AssetPropertiesPanel {
        val p = AssetPropertiesPanel(project, testRootDisposable, testPanelServices(project), { it.run() }, { it.run() })
        p.show(AbyssusRootNode(project, ViewSettings.DEFAULT).children.single {
            (it as? AbyssusAssetNode)?.virtualFile?.name == "P.abss"
        })
        return p
    }
    private fun find(c: Component): JComboBox<*>? =
        if (c.name == "scene-view-antialiasing") c as JComboBox<*> else (c as? Container)?.components?.firstNotNullOfOrNull(::find)
    private fun combo(p: AssetPropertiesPanel) = find(p)!!
    private fun text(f: com.intellij.openapi.vfs.VirtualFile) = FileDocumentManager.getInstance().getDocument(f)!!.text

    fun testProjectControlPersistsOnlyItsAbssAndFollowsUndoRedoAndTextEdits() {
        val f = setupProject()
        myFixture.openFileInEditor(f)
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val others = listOf("p/scenes/S.scene", "p/assets/m/meta.json").map { myFixture.findFileInTempDir(it) }
        val beforeOthers = others.map { text(it) }
        val before = text(f)
        val p = panel()
        assertEquals(AntialiasingLevel.X4, combo(p).selectedItem)
        assertEquals(before, text(f))
        combo(p).selectedItem = AntialiasingLevel.OFF
        assertEquals(AntialiasingLevel.OFF, project.service<AbyssusProjectSettings>().getSettings(f).antialiasing)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, text(f))
        assertEquals(AntialiasingLevel.X4, combo(p).selectedItem)
        UndoManager.getInstance(project).redo(editor)
        assertEquals(AntialiasingLevel.OFF, combo(p).selectedItem)
        WriteCommandAction.runWriteCommandAction(project) {
            FileDocumentManager.getInstance().getDocument(f)!!.setText(text(f).replace("\"OFF\"", "\"X8\""))
        }
        assertEquals(AntialiasingLevel.X8, combo(p).selectedItem)
        combo(p).selectedItem = AntialiasingLevel.X4
        assertEquals(before, text(f))
        assertEquals(beforeOthers, others.map { text(it) })
        assertEquals(beforeOthers, others.map { String(it.contentsToByteArray(), it.charset) })
    }

    fun testUnsupportedProjectCannotEditAntialiasing() {
        val f = setupProject(2)
        val before = text(f)
        assertFalse(combo(panel()).isEnabled)
        assertEquals(before, text(f))
    }

    fun testAntialiasingIsNotRegisteredInIdeSettings() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/resources/META-INF/plugin.xml"))
        val configurables = xml.getElementsByTagName("applicationConfigurable")
        for (i in 0 until configurables.length) {
            val entry = configurables.item(i) as org.w3c.dom.Element
            assertFalse(entry.getAttribute("instance").contains("AntialiasingConfigurable"))
            assertFalse(entry.getAttribute("id") == "net.nevinsky.abyssus.settings")
        }
    }
}
