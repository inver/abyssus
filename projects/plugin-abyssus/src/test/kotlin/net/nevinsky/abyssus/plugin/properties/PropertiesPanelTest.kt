/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.plugin.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusRootNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusSelection
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode
import net.nevinsky.abyssus.plugin.testPanelServices
import java.awt.Component
import java.awt.Container
import java.io.File

/** What the panel shows for each kind of selection, its empty state, and the `Enabled` boxes (spec `object-properties-panel`). */
class PropertiesPanelTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    override fun setUp() {
        super.setUp()
        AbyssusSelection.of(project).select(null)
    }

    override fun tearDown() {
        try {
            AbyssusSelection.of(project).select(null)
        } finally {
            super.tearDown()
        }
    }

    private fun copyProject(dir: String) {
        myFixture.copyFileToProject("$dir/$dir.abss", "$dir/$dir.abss")
        File("$testDataPath/$dir/scenes").listFiles()!!.forEach { myFixture.copyFileToProject("$dir/scenes/${it.name}", "$dir/scenes/${it.name}") }
        File("$testDataPath/$dir/assets").listFiles { f -> f.isDirectory }!!.forEach { d ->
            d.listFiles { f -> f.isFile && f.extension == "json" }!!.forEach {
                myFixture.addFileToProject("$dir/assets/${d.name}/${it.name}", it.readText().trimEnd() + "\n")
            }
        }
    }

    /** The children with their parent set, which the tree does and a screen row needs to find its asset. */
    private fun children(node: AbstractTreeNode<*>) = node.children.map { (it as AbstractTreeNode<*>).also { child -> child.parent = node } }
    private fun label(node: AbstractTreeNode<*>): String =
        (node as? DtoEntryNode)?.let { it.label ?: it.value.name } ?: (node as AbyssusAssetNode).virtualFile.name

    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
    private fun sceneNode() = children(children(abss()).single { label(it) == "scenes" }).single()
    private fun row(parent: AbstractTreeNode<*>, name: String) = children(parent).single { label(it) == name }
    private fun entity(id: String) = children(row(sceneNode(), "ecs")).first { (it as DtoEntryNode).value.name == id }

    private fun panel() = AssetPropertiesPanel(project, testRootDisposable, testPanelServices(project), { it.run() }, { it.run() })

    private fun all(c: Component): List<Component> = listOf(c) + ((c as? Container)?.components?.flatMap { all(it) } ?: emptyList())
    private fun find(c: Component, name: String) = all(c).firstOrNull { it.name == name }
    private fun texts(c: Component) = all(c).filterIsInstance<JBLabel>().mapNotNull { it.text }

    private fun sceneFile() = myFixture.findFileInTempDir("Untitled/scenes/Main Scene.scene")
    private fun sceneText() = FileDocumentManager.getInstance().getDocument(sceneFile())!!.text
    private fun sections(p: Component) = all(p).filter { it.name?.startsWith("section-title-") == true }.map { (it as JBLabel).text }

    fun testNothingSelectedShowsTheEmptyState() {
        val p = panel()
        assertEquals("Nothing is selected.", (p.state as PanelState.Empty).message)
        assertTrue(texts(p).contains("Nothing is selected."))
        assertTrue(texts(p).any { it.contains("Select a scene, an entity, a component or an asset in the Abyssus tree.") })
    }

    fun testProjectShowsProjectAndMainCameraSections() {
        copyProject("Untitled")
        val p = panel()
        p.show(abss())
        assertEquals("Untitled.abss", (find(p, "project-name") as JBLabel).text)
        assertEquals("Project · Untitled.abss", (find(p, "header-subtitle") as JBLabel).text)
        assertNull(find(p, "header-breadcrumb"))
        assertEquals(listOf("Project", "Main camera"), sections(p))
        assertTrue(texts(p).any { it.contains("Dim values are defaults") })
    }

    fun testSceneShowsItsSettingsAndTheRenderingSection() {
        copyProject("Untitled")
        val p = panel()
        p.show(sceneNode())
        assertEquals("Ololo (0)", (find(p, "scene-name") as JBLabel).text)
        assertEquals(listOf("Scene", "Ambient light", "Fog", "Skybox", "Rendering"), sections(p))
        assertEquals("Untitled.abss", (find(p, "header-breadcrumb") as JBLabel).text)
        assertNotNull(find(p, "section-enabled-scene.fog"))
        assertNull(find(p, "section-enabled-scene.main"))
    }

    fun testAFolderRowIsReadOnly() {
        copyProject("Untitled")
        val p = panel()
        p.show(row(abss(), "assets"))
        assertTrue(texts(p).any { it.startsWith("Folder · ") && it.endsWith(" items") })
        assertNotNull(find(p, "badge-read-only"))
        assertEquals(listOf("Folder"), sections(p))
    }

    fun testTheEcsRowCountsEntities() {
        copyProject("Untitled")
        val p = panel()
        p.show(row(sceneNode(), "ecs"))
        assertTrue(texts(p).any { it.startsWith("ecs · ") && it.contains("entities") })
        assertEquals(listOf("Entities"), sections(p))
        assertNotNull(find(p, "badge-read-only"))
        assertEquals(listOf("Untitled.abss", "Ololo"), (find(p, "header-breadcrumb") as JBLabel).text.split("  ›  "))
    }

    fun testASceneSettingShowsOnlyItsSection() {
        copyProject("Untitled")
        val p = panel()
        p.show(row(sceneNode(), "fog"))
        assertEquals(listOf("Fog"), sections(p))
        assertEquals("Scene setting · Ololo", (find(p, "header-subtitle") as JBLabel).text)
    }

    fun testAnEntityShowsBreadcrumbSubtitleAndOneSectionPerComponent() {
        copyProject("Untitled")
        val p = panel()
        p.show(entity("0"))
        assertEquals("Untitled.abss  ›  Ololo  ›  ecs", (find(p, "header-breadcrumb") as JBLabel).text)
        assertEquals("Entity 0 · OBJECT", (find(p, "header-subtitle") as JBLabel).text)
        assertTrue(sections(p).containsAll(listOf("Name", "Position", "Render")))
        assertNull("the Name section has no Enabled box", find(p, "section-enabled-0.NameComponent"))
        assertNotNull(find(p, "section-enabled-0.RenderComponent"))
    }

    fun testAComponentShowsItsOwnHeader() {
        copyProject("Untitled")
        val p = panel()
        p.show(row(entity("0"), "RenderComponent"))
        assertTrue((find(p, "header-subtitle") as JBLabel).text.startsWith("Component · RenderComponent on "))
        assertEquals(listOf("Render"), sections(p))
    }

    fun testUncheckingAComponentWritesOnlyItsFlagAsOneUndoStep() {
        copyProject("Untitled")
        val p = panel()
        p.show(entity("0"))
        val file = sceneFile()
        val before = sceneText()
        val box = find(p, "section-enabled-0.RenderComponent") as JBCheckBox
        assertTrue(box.isSelected)
        myFixture.openFileInEditor(file)
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val opened = sceneText()
        box.doClick()
        val after = SceneJson().parse(sceneText())
        assertEquals(false, after["ecs"]["0"]["components"]["RenderComponent"]["enabled"].booleanValue())
        assertNull(after["ecs"]["0"]["components"]["PositionComponent"]["enabled"])
        assertFalse((find(p, "section-enabled-0.RenderComponent") as JBCheckBox).isSelected)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(opened, sceneText())
        assertTrue((find(p, "section-enabled-0.RenderComponent") as JBCheckBox).isSelected)
        assertNotNull(before)
    }

    fun testUncheckingFogWritesFogEnabledOnly() {
        copyProject("Untitled")
        val p = panel()
        p.show(sceneNode())
        val before = SceneJson().parse(sceneText())
        (find(p, "section-enabled-scene.fog") as JBCheckBox).doClick()
        val after = SceneJson().parse(sceneText())
        assertEquals(false, after["fogEnabled"].booleanValue())
        assertEquals(before["fog"], after["fog"])
        assertEquals(before["ambientLightEnabled"], after["ambientLightEnabled"])
        assertEquals(before["ecs"], after["ecs"])
    }

    /** True when [c] and every ancestor up to [root] is visible (a hidden section body hides what is inside it). */
    private fun reallyVisible(c: Component, root: Component): Boolean {
        var at: Component? = c
        while (at != null && at !== root) {
            if (!at.isVisible) return false
            at = at.parent
        }
        return true
    }

    private fun click(c: Component) = c.mouseListeners.forEach { it.mouseClicked(java.awt.event.MouseEvent(c, 0, 0, 0, 1, 1, 1, false)) }

    fun testACollapsedSectionStaysCollapsedWhenTheFileChangesAndResetsOnAnotherNode() {
        copyProject("Untitled")
        val p = panel()
        p.show(entity("0"))
        fun assetField() = find(p, "field-RenderComponent-assetName")!!
        assertTrue(reallyVisible(assetField(), p))
        click(find(p, "section-chevron-0.RenderComponent")!!)
        assertFalse(reallyVisible(assetField(), p))
        // the scene changes (another section's box), the panel rebuilds, and the section stays collapsed
        (find(p, "section-enabled-0.RenderComponent") as JBCheckBox).doClick()
        assertFalse(reallyVisible(assetField(), p))
        // another node starts expanded again
        p.show(entity("2"))
        assertTrue(reallyVisible(find(p, "field-RenderComponent-assetName")!!, p))
    }

    private fun uiProject() {
        val fixture = File("../app-game-control-line/project/ControlLine")
        myFixture.addFileToProject("Ui/Ui.abss", """{"format":"abyssus","formatVersion":1,"name":"Ui","uiAssetNames":["screens"]}""")
        myFixture.addFileToProject("Ui/assets/screens/meta.json", File(fixture, "assets/screens/meta.json").readText())
    }

    fun testAUiScreenShowsItsAssetAndTheLink() {
        uiProject()
        val abssNode = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
        val screens = children(row(abssNode, "ui")).single()
        val screen = children(row(row(screens, "meta"), "additional")).first()
        val p = panel()
        p.show(screen)
        assertEquals("UI screen · assets/screens", (find(p, "header-subtitle") as JBLabel).text)
        assertEquals(listOf("Screen"), sections(p))
        assertNotNull(find(p, "link-screen-editor"))
        assertEquals("Ui.abss  ›  screens", (find(p, "header-breadcrumb") as JBLabel).text)
    }

    fun testAUiAssetShowsMetaAndAdditionalWithItsScreensTable() {
        uiProject()
        val abssNode = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
        val p = panel()
        p.show(children(row(abssNode, "ui")).single())
        assertEquals(listOf("meta.json", "additional.ui"), sections(p))
        assertTrue(texts(p).any { it.startsWith("Screens (") })
        assertNotNull(find(p, "link-screen-editor"))
    }
}
