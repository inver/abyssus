/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.vfs.VfsUtilCore
import net.nevinsky.abyssus.plugin.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusRootNode
import com.intellij.ide.projectView.ViewSettings
import net.nevinsky.abyssus.plugin.testPanelServices
import net.nevinsky.abyssus.plugin.sceneview.SceneViewSettings
import java.awt.Component
import java.awt.Container
import javax.swing.JCheckBox

/** The `Show FPS` view setting in the project properties: an IDE preference that never touches the project's files. */
class ProjectFpsSettingsTest : BasePlatformTestCase() {
    private val key = "net.nevinsky.abyssus.sceneView.showFps"

    override fun tearDown() {
        try {
            PropertiesComponent.getInstance(project).unsetValue(key)
        } finally {
            super.tearDown()
        }
    }

    private fun addProject(abss: String = """{"format":"abyssus","formatVersion":1,"name":"P"}""") {
        myFixture.addFileToProject("p/P.abss", abss)
        myFixture.addFileToProject("p/scenes/S.scene", """{"format":"abyssus","formatVersion":1,"name":"S"}""")
        myFixture.addFileToProject("p/assets/m/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"6a1b8d52-9f3e-4c71-8b0d-2e5f7a9c1d34","type":"MODEL","additional":{"file":"model.glb"}}""")
    }

    private fun node() = AbyssusRootNode(project, ViewSettings.DEFAULT).children.map { it as com.intellij.ide.util.treeView.AbstractTreeNode<*> }
        .single { (it as? AbyssusAssetNode)?.virtualFile?.name?.endsWith(".abss") == true }

    private fun panel() = AssetPropertiesPanel(project, testRootDisposable, testPanelServices(project), { it.run() }, { it.run() })

    private fun find(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { find(it, name) }

    private fun fps(p: AssetPropertiesPanel) = find(p, "show-fps") as JCheckBox

    private fun texts(vararg names: String): Map<String, String> = names.associateWith {
        VfsUtilCore.loadText(myFixture.findFileInTempDir(it))
    }

    fun testItStartsUncheckedForAProjectWithNoSavedPreference() {
        addProject()
        val p = panel()
        p.show(node())
        assertFalse(fps(p).isSelected)
    }

    fun testTogglingWorksWithNoSceneOpenAndChangesNoFile() {
        addProject()
        val files = arrayOf("p/P.abss", "p/scenes/S.scene", "p/assets/m/meta.json")
        val before = texts(*files)
        val p = panel()
        p.show(node())
        fps(p).doClick()
        assertTrue(project.service<SceneViewSettings>().showFps)
        fps(p).doClick()
        assertFalse(project.service<SceneViewSettings>().showFps)
        assertEquals(before, texts(*files))
        assertFalse("no document was changed", FileDocumentManager.getInstance().unsavedDocuments.isNotEmpty())
    }

    fun testItStaysCheckedAfterSelectingSomethingElseAndComingBack() {
        addProject()
        val p = panel()
        p.show(node())
        fps(p).doClick()
        val assets = node().children.map { it as com.intellij.ide.util.treeView.AbstractTreeNode<*> }
        p.show(assets.first())
        p.show(node())
        assertTrue(fps(p).isSelected)
    }

    fun testItFollowsTheSettingChangedElsewhere() {
        addProject()
        val p = panel()
        p.show(node())
        project.service<SceneViewSettings>().showFps = true
        assertTrue(fps(p).isSelected)
    }

    fun testAnUnsupportedProjectStillOffersIt() {
        addProject("""{"format":"abyssus","formatVersion":2,"name":"P"}""")
        val p = panel()
        p.show(node())
        assertTrue(fps(p).isEnabled)
        fps(p).doClick()
        assertTrue(project.service<SceneViewSettings>().showFps)
    }

    fun testTheListenerGoesWhenTheViewIsReplaced() {
        addProject()
        val p = panel()
        p.show(node())
        val old = fps(p)
        p.show(null)
        project.service<SceneViewSettings>().showFps = true
        assertFalse("the replaced view no longer follows the setting", old.isSelected)
    }
}
