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

package net.nevinsky.abyssus.properties

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.AbyssusSelection
import net.nevinsky.abyssus.projectView.DtoEntryNode
import java.awt.Component
import java.awt.Container
import java.io.File

class AssetPropertiesPanelTest : BasePlatformTestCase() {
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

    private fun copyProject() {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("$dir/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
        File("$testDataPath/$dir/assets").listFiles { f -> f.isDirectory }!!.forEach { d ->
            d.listFiles { f -> f.isFile }!!.filter { it.extension in setOf("json", "png", "hdr") }.forEach {
                myFixture.copyFileToProject("$dir/assets/${d.name}/${it.name}", "$dir/assets/${d.name}/${it.name}")
            }
        }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name
    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
    private fun asset(name: String) =
        children(children(abss()).single { label(it) == "assets" }).single { (it as DtoEntryNode).label == name }

    private fun panel() = AssetPropertiesPanel(project, testRootDisposable, { it.run() }, { it.run() })

    private fun texts(c: Component): List<String> = buildList {
        if (c is JBLabel && c.text != null) add(c.text)
        if (c is Container) c.components.forEach { addAll(texts(it)) }
    }

    private fun thumbnails(c: Component): List<Thumbnail> = buildList {
        if (c is Thumbnail) add(c)
        if (c is Container) c.components.forEach { addAll(thumbnails(it)) }
    }

    // 3.1 registration and empty state

    fun testToolWindowIsRegistered() {
        assertTrue(ToolWindowEP.EP_NAME.extensionList.any { it.id == "Abyssus Properties" && it.anchor == "right" })
    }

    fun testStartsWithNothingSelected() {
        val p = panel()
        val state = p.state as PanelState.Empty
        assertEquals("Nothing selected.", state.message)
        assertEquals("Select a skybox, model or terrain under Assets to see its meta.json.", state.hint)
        assertTrue(texts(p).contains("Nothing selected."))
    }

    fun testANonAssetShowsItsNameAndKind() {
        copyProject()
        val p = panel()
        p.show(abss())
        assertEquals("Nothing to show: Untitled.abss is the project file.", (p.state as PanelState.Empty).message)
        val scene = children(children(abss()).single { label(it) == "scenes" }).single()
        p.show(scene)
        assertTrue((p.state as PanelState.Empty).message.endsWith("is a scene."))
    }

    // 3.1a header

    fun testSkyboxHeaderAndRows() {
        copyProject()
        val p = panel()
        p.show(asset("skybox_default"))
        val details = p.state as PanelState.Details
        assertEquals("skybox_default", details.name)
        val shown = texts(p)
        assertTrue(shown.toString(), shown.contains("skybox_default"))
        assertTrue(shown.contains("skybox asset · read-only"))
        assertTrue(shown.contains("NAME") && shown.contains("VALUE"))
        assertTrue(shown.contains("additional"))
        assertEquals(12, shown.count { it == "skybox_default.png" }) // six rows and six face captions
    }

    fun testUnknownTypeUsesTheGenericIconAndTypeText() {
        myFixture.addFileToProject("p/P.abss", "{}")
        myFixture.addFileToProject("p/assets/w/meta.json", """{"type":"WIDGET","additional":{"a":1}}""")
        val node = children(children(abss()).single { label(it) == "assets" }).single()
        val p = panel()
        p.show(node)
        val details = p.state as PanelState.Details
        assertNull(details.faces)
        assertTrue(texts(p).contains("widget asset · read-only"))
        assertSame(net.nevinsky.abyssus.filetype.AssetIcons.UNKNOWN, net.nevinsky.abyssus.filetype.AssetIcons.forType(details.meta.type))
    }

    // 3.2 selection, previews

    fun testPanelOpenedAfterASelectionShowsIt() {
        copyProject()
        AbyssusSelection.of(project).select(asset("skybox_default"))
        val p = panel()
        assertTrue(p.state is PanelState.Details)
    }

    fun testPanelFollowsTheSelectionTopic() {
        copyProject()
        val p = panel()
        AbyssusSelection.of(project).select(asset("skybox_default"))
        assertTrue(p.state is PanelState.Details)
        AbyssusSelection.of(project).select(null)
        assertEquals("Nothing selected.", (p.state as PanelState.Empty).message)
    }

    fun testBrokenMetaShowsACannotReadMessage() {
        myFixture.addFileToProject("p/P.abss", "{}")
        myFixture.addFileToProject("p/assets/w/meta.json", """{"type":"MODEL"}""")
        val node = children(children(abss()).single { label(it) == "assets" }).single()
        val p = panel()
        p.show(node)
        val meta = myFixture.findFileInTempDir("p/assets/w/meta.json")
        WriteCommandAction.runWriteCommandAction(project) { FileDocumentManager.getInstance().getDocument(meta)!!.setText("{broken") }
        val state = p.state as PanelState.Empty
        assertTrue(state.message, state.message.startsWith("Cannot read the asset's meta: "))
        assertNull(state.hint)
    }

    // 3.2a face previews

    fun testSkyboxShowsSixLabelledThumbnails() {
        copyProject()
        val p = panel()
        p.show(asset("skybox_default"))
        val faces = (p.state as PanelState.Details).faces!!
        assertEquals(SKYBOX_FACES, faces.map { it.face })
        assertTrue(faces.all { it.image != null })
        assertEquals(6, thumbnails(p).size)
        assertTrue(texts(p).contains("FACE PREVIEWS"))
    }

    fun testMissingFaceFileGetsAPlaceholderAndTheRestStillRender() {
        copyProject()
        val meta = myFixture.findFileInTempDir("Untitled/assets/skybox_default/meta.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(meta)!!
            doc.setText(doc.text.replaceFirst("\"left\": \"skybox_default.png\"", "\"left\": \"gone.png\""))
        }
        val p = panel()
        p.show(asset("skybox_default"))
        val faces = (p.state as PanelState.Details).faces!!.associateBy { it.face }
        assertNull(faces.getValue("left").image)
        assertEquals("gone.png", faces.getValue("left").file)
        assertNotNull(faces.getValue("right").image)
    }

    // HDR skies

    fun testHdrHeaderShowsTheHdrIcon() {
        copyProject()
        val p = panel()
        p.show(asset("skybox_hdr"))
        val details = p.state as PanelState.Details
        assertTrue(texts(p).contains("skybox_hdr asset · read-only"))
        assertSame(net.nevinsky.abyssus.filetype.AssetIcons.forType("SKYBOX_HDR"), net.nevinsky.abyssus.filetype.AssetIcons.forType(details.meta.type))
        assertNotSame(net.nevinsky.abyssus.filetype.AssetIcons.UNKNOWN, net.nevinsky.abyssus.filetype.AssetIcons.forType(details.meta.type))
    }

    fun testHdrShowsOnePreviewLabelledWithSize() {
        copyProject()
        val p = panel()
        p.show(asset("skybox_hdr"))
        val hdr = (p.state as PanelState.Details).hdr!!
        assertEquals("sky.hdr · 64 × 32", hdr.label)
        assertEquals(2 * hdr.image!!.height, hdr.image!!.width)
        val shown = texts(p)
        assertTrue(shown.toString(), shown.contains("PREVIEW") && shown.contains("sky.hdr · 64 × 32"))
    }

    fun testHdrShowsNoFacePreviews() {
        copyProject()
        val p = panel()
        p.show(asset("skybox_hdr"))
        assertNull((p.state as PanelState.Details).faces)
        assertFalse(texts(p).contains("FACE PREVIEWS"))
    }

    fun testUnreadableHdrShowsAPlaceholderAndRows() {
        myFixture.addFileToProject("p/P.abss", "{}")
        myFixture.addFileToProject("p/assets/broken/meta.json", """{"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{}}""")
        val bytes = File("$testDataPath/Untitled/assets/skybox_hdr/sky.hdr").readBytes()
        val vf = myFixture.addFileToProject("p/assets/broken/sky.hdr", "").virtualFile
        WriteCommandAction.runWriteCommandAction(project) { vf.setBinaryContent(bytes.copyOf(bytes.size / 2)) }
        val node = children(children(abss()).single { label(it) == "assets" }).single()
        val p = panel()
        p.show(node)
        val details = p.state as PanelState.Details
        assertNull(details.hdr!!.image)
        assertTrue(details.hdr!!.label, details.hdr!!.label.startsWith("Cannot read sky.hdr: ") && details.hdr!!.label.contains("truncated"))
        assertTrue("the Meta rows are still shown", texts(p).contains("SKYBOX_HDR"))
    }

    fun testModelAndTerrainHaveNoPreviewSection() {
        copyProject()
        val p = panel()
        for (name in listOf("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")) {
            p.show(asset(name))
            assertNull((p.state as PanelState.Details).faces)
            assertFalse(texts(p).contains("FACE PREVIEWS"))
        }
    }

    // 3.3 refresh

    fun testEditingMetaTextRefreshesWithoutReselecting() {
        copyProject()
        val p = panel()
        val terrain = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
        p.show(asset(terrain))
        fun size() = (p.state as PanelState.Details).meta.rows.single { it.name == "size" }.value
        assertEquals("1600", size())
        val meta = myFixture.findFileInTempDir("Untitled/assets/$terrain/meta.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(meta)!!
            doc.setText(doc.text.replace("\"size\":1600", "\"size\":2048"))
        }
        assertEquals("2048", size())
    }

    fun testReplacingAFaceFileRefreshesItsPreview() {
        copyProject()
        val p = panel()
        p.show(asset("skybox_default"))
        val before = (p.state as PanelState.Details).faces!!.first().image
        val png = myFixture.findFileInTempDir("Untitled/assets/skybox_default/skybox_default.png")
        WriteCommandAction.runWriteCommandAction(project) { png.setBinaryContent(ByteArray(0)) }
        com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertNotNull(before)
        assertNull((p.state as PanelState.Details).faces!!.first().image)
    }
}
