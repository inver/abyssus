/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import net.nevinsky.abyssus.editor.document.SceneJson
import net.nevinsky.abyssus.editor.meta.AssetChoice

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.AbyssusSelection
import net.nevinsky.abyssus.projectView.DtoEntryNode
import java.awt.Component
import java.awt.Container
import java.io.File
import net.nevinsky.abyssus.editor.meta.SKYBOX_FACES
import net.nevinsky.abyssus.testPanelServices

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
            d.listFiles { f -> f.isFile }!!.filter { it.extension in setOf("json", "png") }.forEach {
                val path = "$dir/assets/${d.name}/${it.name}"
                if (it.extension == "json") {
                    myFixture.addFileToProject(path, it.readText().trimEnd() + "\n")
                } else {
                    myFixture.copyFileToProject(path, path)
                }
            }
        }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name
    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
    private fun asset(name: String) =
        children(children(abss()).single { label(it) == "assets" }).single { (it as DtoEntryNode).label == name }

    private fun panel() = AssetPropertiesPanel(project, testRootDisposable, testPanelServices(project), { it.run() }, { it.run() })

    private fun texts(c: Component): List<String> = buildList {
        if (c is JBLabel && c.text != null) add(c.text)
        if (c is Container) c.components.forEach { addAll(texts(it)) }
    }

    private fun find(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { find(it, name) }

    private fun field(p: Component, key: String) = find(p, "asset-field-$key")
    private fun errorOf(p: Component, key: String) = (find(p, "asset-error-$key") as JBLabel).text
    private fun metaText(path: String) = FileDocumentManager.getInstance().getDocument(myFixture.findFileInTempDir(path))!!.text
    private fun type(c: JBTextField, text: String) {
        c.text = text
        c.postActionEvent()
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
        // a scene row shows the scene's view settings (its Ray Tracing switch) instead of an empty state
        p.show(scene)
        val details = p.state as PanelState.UISceneState
        assertEquals("Main Scene.scene", details.file.name)
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
        assertTrue(shown.contains("skybox asset"))
        assertTrue(shown.contains("Edits change every instance that uses this asset."))
        assertTrue(shown.contains("NAME") && shown.contains("VALUE"))
        assertTrue(shown.contains("additional"))
        assertEquals(6, shown.count { it == "skybox_default.png" }) // the six face captions; the rows are choosers
    }

    fun testUnknownTypeUsesTheGenericIconAndTypeText() {
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1}""")
        myFixture.addFileToProject("p/assets/w/meta.json", """{"format":"abyssus","formatVersion":1,"type":"WIDGET","additional":{"a":1}}""")
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
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1}""")
        myFixture.addFileToProject("p/assets/w/meta.json", """{"format":"abyssus","formatVersion":1,"type":"MODEL"}""")
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
        val p = panel()
        p.showFolder(diskHdr())
        val hdr = (p.state as PanelState.Details).hdr!!
        assertEquals("sky.exr · 1024 × 512", hdr.label)
        assertEquals(2 * hdr.image!!.height, hdr.image!!.width)
        val shown = texts(p)
        assertTrue(shown.toString(), shown.contains("PREVIEW") && shown.contains("sky.exr · 1024 × 512"))
    }

    fun testHdrShowsNoFacePreviews() {
        copyProject()
        val p = panel()
        p.show(asset("skybox_hdr"))
        assertNull((p.state as PanelState.Details).faces)
        assertFalse(texts(p).contains("FACE PREVIEWS"))
    }

    /** Test-owned EXR bytes on disk, where the preview decoder reads them; never modifies shared assets. */
    private fun diskHdr(truncated: Boolean = false): VirtualFile {
        val disk = com.intellij.openapi.util.io.FileUtil.createTempDirectory("abyssus-hdr-sky", null)
        val sky = File(disk, "assets/skybox_hdr").apply { mkdirs() }
        File(sky, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{"file":"sky.exr"}}""")
        val bytes = File("$testDataPath/Untitled/assets/skybox_hdr/sky.exr").readBytes()
        File(sky, "sky.exr").writeBytes(if (truncated) bytes.copyOf(bytes.size / 2) else bytes)
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sky)!!
    }

    fun testUnreadableHdrShowsAPlaceholderAndRows() {
        val p = panel()
        p.showFolder(diskHdr(truncated = true))
        val details = p.state as PanelState.Details
        assertNull(details.hdr!!.image)
        assertTrue(details.hdr!!.label, details.hdr!!.label.startsWith("Cannot read sky.exr: ") && details.hdr!!.label.contains("EXR image error"))
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
            doc.setText(doc.text.replace("\"size\": 1600", "\"size\": 2048"))
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

    // typed asset properties (2.1, 2.2)

    private val terrain = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"

    fun testTerrainShowsTypedEditorsWithItsValues() {
        copyProject()
        val p = panel()
        p.show(asset(terrain))
        assertEquals("1600", (field(p, "size") as JBTextField).text)
        assertEquals("60.0", (field(p, "uv") as JBTextField).text)
        for (key in listOf("splatMap", "splatBase", "splatR", "splatG", "splatB", "splatA")) assertTrue(key, field(p, key) is com.intellij.openapi.ui.ComboBox<*>)
        assertNull("terrainFile is not editable", field(p, "terrainFile"))
        assertNull(field(p, "uuid"))
        assertTrue(texts(p).contains("terrain asset"))
        assertTrue(texts(p).contains("Edits change every instance that uses this asset."))
    }

    fun testCubeSkyboxHasSixFaceChoosers() {
        copyProject()
        val p = panel()
        p.show(asset("skybox_default"))
        for (face in SKYBOX_FACES) {
            val combo = field(p, face) as com.intellij.openapi.ui.ComboBox<*>
            assertEquals("skybox_default.png", (combo.selectedItem as net.nevinsky.abyssus.editor.meta.AssetChoice).value)
        }
    }

    fun testProceduralSkyShowsEffectiveDefaultsForOmittedFields() {
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1}""")
        myFixture.addFileToProject("p/assets/sky/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"type":"SKYBOX_PROCEDURAL","additional":{"vertex":"v","fragment":"f"}}""")
        val p = panel()
        p.show(children(children(abss()).single { label(it) == "assets" }).single())
        assertEquals("20.0", (field(p, "sunIntensity") as JBTextField).text)
        assertEquals("0.76", (field(p, "mieG") as JBTextField).text)
        assertEquals("6360000.0", (field(p, "planetRadius") as JBTextField).text)
        assertEquals("5.8E-6, 1.35E-5, 3.31E-5", (field(p, "betaRayleigh") as JBTextField).text)
        assertEquals("the file still omits them", """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"type":"SKYBOX_PROCEDURAL","additional":{"vertex":"v","fragment":"f"}}""", metaText("p/assets/sky/meta.json"))
    }

    fun testUnsupportedAssetsStayReadOnly() {
        copyProject()
        val p = panel()
        p.show(asset("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"))
        assertTrue((p.state as PanelState.Details).fields.isEmpty())
        assertTrue(texts(p).contains("model asset · read-only"))
        assertFalse(texts(p).contains("Edits change every instance that uses this asset."))
        p.show(asset("skybox_hdr"))
        assertTrue(texts(p).contains("skybox_hdr asset · read-only"))
        assertNull(field(p, "file"))
    }

    fun testSelectingAnAssetWritesNothing() {
        copyProject()
        val path = "Untitled/assets/$terrain/meta.json"
        val before = myFixture.findFileInTempDir(path).modificationStamp
        val text = metaText(path)
        val p = panel()
        p.show(asset(terrain))
        p.show(asset("skybox_default"))
        p.show(asset(terrain))
        assertEquals(text, metaText(path))
        assertEquals(before, myFixture.findFileInTempDir(path).modificationStamp)
    }

    fun testEditingSizeWritesOnlyThatValueAndRefreshesTheRow() {
        copyProject()
        val path = "Untitled/assets/$terrain/meta.json"
        val before = metaText(path)
        val p = panel()
        p.show(asset(terrain))
        type(field(p, "size") as JBTextField, "800")
        assertEquals(before.replace("\"size\": 1600", "\"size\": 800"), metaText(path))
        assertEquals("800", (field(p, "size") as JBTextField).text)
        assertEquals("", errorOf(p, "size"))
    }

    fun testInvalidValuesRevertAndExplainWithoutWriting() {
        copyProject()
        val path = "Untitled/assets/$terrain/meta.json"
        val before = metaText(path)
        val p = panel()
        p.show(asset(terrain))
        for ((text, reason) in listOf("0" to "Must be greater than zero.", "abc" to "Enter a number.", "12.5" to "Enter a whole number.")) {
            type(field(p, "size") as JBTextField, text)
            assertEquals(reason, errorOf(p, "size"))
            assertEquals("1600", (field(p, "size") as JBTextField).text)
        }
        assertEquals(before, metaText(path))
    }

    fun testACommitBasedOnASupersededValueIsRejectedExplainedAndRefreshed() {
        copyProject()
        val path = "Untitled/assets/$terrain/meta.json"
        val p = panel()
        p.show(asset(terrain))
        val stale = field(p, "size") as JBTextField
        val document = FileDocumentManager.getInstance().getDocument(myFixture.findFileInTempDir(path))!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.setText(document.text.replace("\"size\": 1600", "\"size\": 1000"))
        }
        val changed = metaText(path)
        type(stale, "800")
        assertEquals(changed, metaText(path))
        assertEquals("1000", (field(p, "size") as JBTextField).text)
        assertEquals("This value changed elsewhere; the current value is shown.", errorOf(p, "size"))
    }

    fun testAtmosphereRadiiAreValidatedTogether() {
        copyProject()
        val path = "Untitled/assets/skybox_physical/meta.json"
        val before = metaText(path)
        val p = panel()
        p.show(asset("skybox_physical"))
        type(field(p, "atmosphereRadius") as JBTextField, "6000000")
        assertEquals("Atmosphere radius must be greater than planet radius.", errorOf(p, "atmosphereRadius"))
        assertEquals(before, metaText(path))
        type(field(p, "sunIntensity") as JBTextField, "25")
        assertTrue(metaText(path).contains("\"sunIntensity\": 25.0"))
    }

    /** A copy of the skybox fixture on the real file system, where the chooser lists files with java.io. */
    private fun diskSkybox(): VirtualFile {
        val dir = com.intellij.openapi.util.io.FileUtil.createTempDirectory("abyssus-sky", null)
        val sky = File(dir, "assets/skybox_default").apply { mkdirs() }
        File("$testDataPath/Untitled/assets/skybox_default").listFiles()!!.forEach { it.copyTo(File(sky, it.name)) }
        File(sky, "other.png").writeBytes(File(sky, "skybox_default.png").readBytes())
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sky)!!
    }

    fun testChoosingAFaceChangesOnlyThatKey() {
        val folder = diskSkybox()
        val meta = folder.findChild("meta.json")!!
        val before = FileDocumentManager.getInstance().getDocument(meta)!!.text
        val p = panel()
        p.showFolder(folder)
        @Suppress("UNCHECKED_CAST")
        val combo = field(p, "left") as com.intellij.openapi.ui.ComboBox<net.nevinsky.abyssus.editor.meta.AssetChoice>
        val other = (0 until combo.itemCount).map { combo.getItemAt(it) }.single { it.value == "other.png" }
        combo.selectedItem = other
        val after = FileDocumentManager.getInstance().getDocument(meta)!!.text
        assertEquals(before.trimEnd().replaceFirst("\"left\": \"skybox_default.png\"", "\"left\": \"other.png\""), after.trimEnd())
        assertEquals("other.png", (p.state as PanelState.Details).faces!!.single { it.face == "left" }.file)
    }

    private fun click(p: Component, name: String) = (find(p, name) as javax.swing.JButton).doClick()

    fun testUndoAndRedoFromThePanelRestoreAndReapplyAnEdit() {
        copyProject()
        val path = "Untitled/assets/$terrain/meta.json"
        val original = metaText(path)
        // Fixture creation has its own commands; this test starts before the first panel edit.
        (com.intellij.openapi.command.undo.UndoManager.getInstance(project) as com.intellij.openapi.command.impl.UndoManagerImpl)
            .clearUndoRedoQueueInTests(myFixture.findFileInTempDir(path))
        val p = panel()
        p.show(asset(terrain))
        assertFalse("nothing to undo yet", find(p, "asset-undo")!!.isEnabled)
        type(field(p, "uv") as JBTextField, "30")
        val edited = metaText(path)
        assertTrue(edited, edited.contains("\"uv\": 30.0"))
        assertTrue(find(p, "asset-undo")!!.isEnabled)
        click(p, "asset-undo")
        assertEquals(original, metaText(path))
        assertEquals("60.0", (field(p, "uv") as JBTextField).text)
        assertTrue(find(p, "asset-redo")!!.isEnabled)
        click(p, "asset-redo")
        assertEquals(edited, metaText(path))
        assertEquals("30.0", (field(p, "uv") as JBTextField).text)
    }

    private fun providedEditor(p: AssetPropertiesPanel): com.intellij.openapi.fileEditor.FileEditor? {
        var found: Any? = null
        p.uiDataSnapshot(object : com.intellij.openapi.actionSystem.DataSink {
            override fun <T : Any> set(key: com.intellij.openapi.actionSystem.DataKey<T>, data: T?) {
                if (key == com.intellij.openapi.actionSystem.PlatformCoreDataKeys.FILE_EDITOR) found = data
            }

            override fun <T : Any> setNull(key: com.intellij.openapi.actionSystem.DataKey<T>) {}
            override fun <T : Any> lazy(key: com.intellij.openapi.actionSystem.DataKey<T>, data: () -> T?) {}
            override fun <T : Any> lazyNull(key: com.intellij.openapi.actionSystem.DataKey<T>) {}
            override fun uiDataSnapshot(provider: com.intellij.openapi.actionSystem.UiDataProvider) {}
            override fun dataSnapshot(provider: com.intellij.openapi.actionSystem.DataSnapshotProvider) {}
            override fun uiDataSnapshot(provider: com.intellij.openapi.actionSystem.DataProvider) {}
            override fun <T : Any> lazyValue(key: com.intellij.openapi.actionSystem.DataKey<T>, data: (com.intellij.openapi.actionSystem.DataMap) -> T?) {}
        })
        return found as? com.intellij.openapi.fileEditor.FileEditor
    }

    fun testSceneRaySettingsUndoAndRedoFromThePanelFollowTheSavedScene() {
        copyProject()
        val path = "Untitled/scenes/Main Scene.scene"
        val original = metaText(path)
        val p = panel()
        p.show(children(children(abss()).single { label(it) == "scenes" }).single())
        assertEquals("selecting the scene writes nothing", original, metaText(path))
        assertEquals("256", (find(p, "ray-setting-targetSamplesPerPixel") as JBTextField).text)
        val editor = providedEditor(p) as com.intellij.openapi.fileEditor.TextEditor
        assertEquals("Main Scene.scene", editor.file.name)
        type(find(p, "ray-setting-targetSamplesPerPixel") as JBTextField, "512")
        val edited = metaText(path)
        assertEquals(512, net.nevinsky.abyssus.editor.document.SceneJson().parse(edited)["rayTracing"]["targetSamplesPerPixel"].intValue())
        assertEquals("the panel refreshes from the document", "512", (find(p, "ray-setting-targetSamplesPerPixel") as JBTextField).text)
        val undo = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
        undo.undo(editor)
        assertEquals(original, metaText(path))
        assertEquals("256", (find(p, "ray-setting-targetSamplesPerPixel") as JBTextField).text)
        undo.redo(editor)
        assertEquals(edited, metaText(path))
        assertEquals("512", (find(p, "ray-setting-targetSamplesPerPixel") as JBTextField).text)
        // an external text edit, valid or not, is read back without a selection change
        val document = FileDocumentManager.getInstance().getDocument(myFixture.findFileInTempDir(path))!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(edited.replace("512", "1024")) }
        assertEquals("1024", (find(p, "ray-setting-targetSamplesPerPixel") as JBTextField).text)
        WriteCommandAction.runWriteCommandAction(project) { document.setText(edited.replace("512", "-1")) }
        assertTrue(errorText(p, "ray-setting-targetSamplesPerPixel-error").isNotBlank())
        assertEquals("the invalid file is not rewritten", edited.replace("512", "-1"), document.text)
    }

    private fun errorText(p: Component, name: String) = (find(p, name) as JBLabel).text

    fun testThePanelProvidesAnEditorForThePlatformUndo() {
        copyProject()
        val p = panel()
        p.show(asset(terrain))
        assertEquals("meta.json", providedEditor(p)!!.file.name)
        p.show(asset("skybox_hdr"))
        assertNull(providedEditor(p))
        p.show(null)
        assertNull(providedEditor(p))
    }
}
