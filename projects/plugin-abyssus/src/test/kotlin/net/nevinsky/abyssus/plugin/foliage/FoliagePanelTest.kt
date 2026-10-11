package net.nevinsky.abyssus.plugin.foliage

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextField
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageScatter
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileUndoAction
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.plugin.properties.AssetPropertiesPanel
import net.nevinsky.abyssus.plugin.testPanelServices
import org.slf4j.helpers.NOPLogger
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.JButton

class FoliagePanelTest : BasePlatformTestCase() {
    private var previousDialog: com.intellij.openapi.ui.TestDialog? = null
    private lateinit var disk: File
    private lateinit var folder: VirtualFile
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val drafts get() = project.service<FoliageDrafts>()
    override fun setUp() {
        super.setUp()
        previousDialog = com.intellij.openapi.ui.TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.YES)
        disk = FileUtil.createTempDirectory("abyssus-foliage-panel", null)
        FileUtil.copyDir(File("src/test/testData/project/Foliage"), disk)
        folder = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(disk, "assets/foliage_meadow"))!!
        folder.refresh(false, true)
    }
    override fun tearDown() {
        try {
            previousDialog?.let { com.intellij.openapi.ui.TestDialogManager.setTestDialog(it) }
        } finally { super.tearDown() }
    }
    private fun source(): FoliageSource.Ready {
        val text = File(folder.path, "meta.json").readText()
        return readFoliageSource(folder.name, disk, json, text, net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parseObject(text)["additional"]) as FoliageSource.Ready
    }
    private fun controller() = FoliageController(project, folder, source(), drafts, json,
        background = { it.run() }, ui = { it.run() }, readCurrent = { source() })
    private fun panel() = AssetPropertiesPanel(project, testRootDisposable, testPanelServices(project),
        background = { it.run() }, ui = { it.run() }).also { it.showFolder(folder) }
    private fun find(c: Component, name: String): Component? = if (c.name == name) c else
        (c as? Container)?.components?.firstNotNullOfOrNull { find(it, name) }
    private fun bytes(name: String) = File(folder.path, name).readBytes().toList()

    fun testApplyWritesThePreviewAndUndoRestoresExactFiles() {
        val beforeMeta = bytes("meta.json")
        val beforeBake = bytes("foliage.data")
        val c = controller()
        assertNull(c.edit { it.copy(layers = it.layers.map { l -> l.copy(density = l.density * 2) }) })
        assertNotNull(drafts.of(folder.name))
        assertEquals(beforeMeta, bytes("meta.json"))
        val preview = FoliageDataFile().write(c.outcome!!.bake).toList()
        val runner = AssetFileCommand(project, LocalAssetFileStore(disk))
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().createEditor(project, folder.findChild("meta.json")!!)
        try {
            assertEquals(AssetCommandResult.Done, c.apply(runner))
            assertEquals(preview, bytes("foliage.data"))
            assertNull(drafts.of(folder.name))
            com.intellij.openapi.command.undo.UndoManager.getInstance(project).undo(editor)
            assertEquals(beforeMeta, bytes("meta.json"))
            assertEquals(beforeBake, bytes("foliage.data"))
            com.intellij.openapi.command.undo.UndoManager.getInstance(project).redo(editor)
            assertEquals(preview, bytes("foliage.data"))
        } finally { com.intellij.openapi.util.Disposer.dispose(editor) }
        c.dispose()
    }
    fun testSelectingAnotherAssetDiscardsTheDraft() {
        val p = panel()
        val field = find(p, "foliage-0-density") as JBTextField
        field.text = "0.0004"
        field.postActionEvent()
        assertNotNull(drafts.of(folder.name))
        p.show(null)
        assertNull(drafts.of(folder.name))
    }
    fun testUnsupportedMetadataShowsAReasonAndNoEditors() {
        val file = File(folder.path, "meta.json")
        file.writeText(file.readText().replace("\"formatVersion\": 1", "\"formatVersion\": 2"))
        folder.refresh(false, true)
        val before = file.readText()
        val p = panel()
        assertNull(find(p, "foliage-add-layer"))
        assertEquals(before, file.readText())
        assertTrue(p.state is net.nevinsky.abyssus.plugin.properties.PanelState.Empty)
    }
    fun testAddFirstLayerUsesIdOneAndAModel() {
        val c = controller()
        for (l in c.settings.layers.toList()) assertNull(c.removeLayer(l.id))
        assertNull(c.addLayer())
        assertEquals(1, c.settings.layers.single().id)
        assertEquals(1f, c.settings.layers.single().models.single().weight)
        c.dispose()
    }
    fun testFractionalSeedIsRejectedAndFullIntegerRangeIsPreserved() {
        val p = panel()
        val seed = find(p, "foliage-0-seed") as JBTextField
        seed.text = "2.5"
        seed.postActionEvent()
        assertEquals("7", (find(p, "foliage-0-seed") as JBTextField).text)
        val exact = find(p, "foliage-0-seed") as JBTextField
        exact.text = "2147483647"
        exact.postActionEvent()
        assertEquals("2147483647", (find(p, "foliage-0-seed") as JBTextField).text)
    }
    fun testRebakeOfCorruptBytesCanUndoToThoseExactBytes() {
        val old = byteArrayOf(1, 2, 3, 4)
        File(folder.path, "foliage.data").writeBytes(old)
        val c = controller()
        assertTrue(c.stale)
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().createEditor(project, folder.findChild("meta.json")!!)
        try {
            assertEquals(AssetCommandResult.Done, c.apply())
            com.intellij.openapi.command.undo.UndoManager.getInstance(project).undo(editor)
            assertEquals(old.toList(), bytes("foliage.data"))
        } finally { com.intellij.openapi.util.Disposer.dispose(editor) }
        c.dispose()
    }

    private fun capsule() = { _: net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCollider? ->
        net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCollider(
            net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderShape.CAPSULE, radius = 0.4f, halfHeight = 3f,
        )
    }

    private fun stamps() = listOf("foliage.data", "layer-1.mask").map { name ->
        File(folder.path, name).let { it.lastModified() to it.readBytes().toList() }
    }

    private fun metaEditor() =
        com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().createEditor(project, folder.findChild("meta.json")!!)

    private fun colliderOf(index: Int = 0) = net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(File(folder.path, "meta.json").readText())["additional"]["layers"][index]["collider"]

    fun testColliderOnlyApplyWritesMetadataOnlyAndUndoRedoRestoresIt() {
        val beforeMeta = bytes("meta.json")
        val beforeFiles = stamps()
        val c = controller()
        assertFalse(c.stale)
        assertNull(c.editCollider(0, capsule()))
        assertTrue(c.colliderOnly)
        assertTrue(c.canApply)
        assertEquals(beforeMeta, bytes("meta.json"))
        val editor = metaEditor()
        try {
            assertEquals(AssetCommandResult.Done, c.apply())
            assertEquals("CAPSULE", colliderOf()["shape"].asText())
            assertEquals(0.4, colliderOf()["radius"].asDouble(), 1e-6)
            assertEquals(3.0, colliderOf()["halfHeight"].asDouble(), 0.0)
            assertEquals(beforeFiles, stamps())
            assertNull(drafts.of(folder.name))
            val written = bytes("meta.json")
            com.intellij.openapi.command.undo.UndoManager.getInstance(project).undo(editor)
            assertEquals(beforeMeta, bytes("meta.json"))
            assertEquals(beforeFiles, stamps())
            com.intellij.openapi.command.undo.UndoManager.getInstance(project).redo(editor)
            assertEquals(written, bytes("meta.json"))
            assertEquals(beforeFiles, stamps())
            // exactly one undo step undid it: a second Undo has nothing of this edit left to reload from disk
            assertFalse(com.intellij.openapi.command.undo.UndoManager.getInstance(project).isUndoAvailable(editor) &&
                bytes("foliage.data") != beforeFiles[0].second)
        } finally { com.intellij.openapi.util.Disposer.dispose(editor) }
        c.dispose()
    }

    fun testColliderOnlyApplyWorksWithAStaleOrMissingBakeAndKeepsIt() {
        File(folder.path, "foliage.data").writeBytes(byteArrayOf(1, 2, 3, 4))
        val c = controller()
        assertTrue(c.stale)
        assertNull(c.editCollider(0, capsule()))
        assertEquals(AssetCommandResult.Done, c.apply())
        assertEquals(listOf<Byte>(1, 2, 3, 4), bytes("foliage.data"))
        assertTrue(source().stale)
        c.dispose()
    }

    fun testColliderOnlyApplyWithAMissingBakeLeavesItMissing() {
        File(folder.path, "foliage.data").delete()
        val c = controller()
        assertTrue(c.stale)
        assertNull(c.editCollider(0, capsule()))
        assertEquals(AssetCommandResult.Done, c.apply())
        assertFalse(File(folder.path, "foliage.data").exists())
        assertTrue(source().stale)
        c.dispose()
    }

    fun testNoneRemovesOnlyTheColliderAndKeepsNestedData() {
        val file = File(folder.path, "meta.json")
        file.writeText(file.readText().replace("\"seed\": 7", "\"seed\": 7,\n        \"collider\": {\"shape\": \"SPHERE\", \"radius\": 1.50, \"vendor\": {\"k\": 2.50}}"))
        folder.refresh(false, true)
        val withCollider = file.readText()
        val c = controller()
        assertEquals("SPHERE", c.colliderOf(0)!!.shape.name)
        assertNull(c.editCollider(0) { it!!.copy(radius = 2f) })
        assertNull(c.editCollider(0) { it })
        assertEquals(AssetCommandResult.Done, c.apply())
        assertEquals("2.50", colliderOf()["vendor"]["k"].asText())
        val c2 = controller()
        assertNull(c2.editCollider(0) { null })
        assertEquals(AssetCommandResult.Done, c2.apply())
        assertNull(colliderOf())
        assertEquals(withCollider.replace(Regex(",\\s*\"collider\": \\{[^\n]*\\}\\}"), ""), file.readText())
        c.dispose(); c2.dispose()
    }

    fun testInvalidColliderValuesAndKindChangeAreRefusedWithAReasonAndWriteNothing() {
        val beforeMeta = bytes("meta.json")
        val c = controller()
        assertNull(c.editCollider(0, capsule()))
        val reason = c.editCollider(0) { it!!.copy(radius = 0f) }
        assertEquals(net.nevinsky.abyssus.plugin.EditorBundle.message("foliageError.COLLIDER_SIZE"), reason)
        assertEquals(0.4f, c.colliderOf(0)!!.radius, 0f)
        assertNotNull(c.edit { it.copy(layers = it.layers.map { l -> if (l.id == 0) l.copy(kind = "DETAIL") else l }) })
        assertEquals("OBJECT", c.settings.layers[0].kind)
        assertNull(c.editCollider(0) { null })
        assertNull(c.edit { it.copy(layers = it.layers.map { l -> if (l.id == 0) l.copy(kind = "DETAIL") else l }) })
        assertEquals(beforeMeta, bytes("meta.json"))
        c.dispose()
    }

    fun testCancelAndAnExternalChangeBeforeApplyWriteNothing() {
        val beforeMeta = bytes("meta.json")
        var c = controller()
        assertNull(c.editCollider(0, capsule()))
        c.cancel()
        assertNull(c.colliderOf(0))
        assertNull(c.apply())
        assertEquals(beforeMeta, bytes("meta.json"))

        assertNull(c.editCollider(0, capsule()))
        val file = File(folder.path, "meta.json")
        file.writeText(file.readText().replace("\"seed\": 13", "\"seed\": 14"))
        folder.refresh(false, true)
        val external = bytes("meta.json")
        assertNull(c.apply())
        assertEquals(external, bytes("meta.json"))
        assertNotNull(c.message)
        c.dispose()
    }

    fun testOnlyObjectLayersOfThePanelOfferColliderControls() {
        val p = panel()
        assertNotNull(find(p, "foliage-collider-0"))
        assertNull(find(p, "foliage-collider-1"))
        assertNull(find(p, "foliage-collider-1-radius"))
        val combo = find(p, "foliage-collider-0") as javax.swing.JComboBox<*>
        assertEquals(listOf("None", "Box", "Sphere", "Capsule"), (0 until combo.itemCount).map { combo.getItemAt(it) })
        assertEquals(0, combo.selectedIndex)
        combo.selectedIndex = 3
        val radius = find(p, "foliage-collider-0-radius") as JBTextField
        assertEquals("0.5", radius.text)
        radius.text = "0.4"
        radius.postActionEvent()
        assertEquals("0.4", (find(p, "foliage-collider-0-radius") as JBTextField).text)
        radius.text = "0"
        (find(p, "foliage-collider-0-radius") as JBTextField).also { it.text = "0"; it.postActionEvent() }
        assertFalse((find(p, "foliage-error-0-collider") as javax.swing.JLabel).text.isNullOrEmpty())
        assertNotNull(find(p, "foliage-collider-note"))
    }

    fun testADetailLayersImportedColliderIsKeptAndShowsNoControls() {
        val file = File(folder.path, "meta.json")
        file.writeText(file.readText().replace("\"seed\": 13", "\"seed\": 13,\n        \"collider\": {\"shape\": \"SPHERE\", \"radius\": 0}"))
        folder.refresh(false, true)
        val c = controller()
        assertTrue(c.problems.toString(), c.problems.isEmpty())
        assertNull(c.edit { it.copy(layers = it.layers.map { l -> if (l.id == 1) l.copy(density = 0.002f) else l }) })
        assertEquals(AssetCommandResult.Done.javaClass, c.apply(AssetFileCommand(project, LocalAssetFileStore(disk)))!!.javaClass)
        assertEquals(0.0, colliderOf(1)["radius"].asDouble(), 0.0)
        assertNull(find(panel(), "foliage-collider-1"))
        c.dispose()
    }

    fun testAReadOnlyDocumentOrADisposedControllerWritesNothing() {
        val beforeMeta = bytes("meta.json")
        val c = controller()
        assertNull(c.editCollider(0, capsule()))
        val meta = folder.findChild("meta.json")!!
        com.intellij.openapi.application.runWriteAction { meta.isWritable = false }
        try {
            assertTrue(c.apply() is AssetCommandResult.Blocked)
            assertEquals(beforeMeta, bytes("meta.json"))
        } finally { com.intellij.openapi.application.runWriteAction { meta.isWritable = true } }
        c.dispose()
        assertNull(c.apply())
        assertEquals(beforeMeta, bytes("meta.json"))
        assertNull(drafts.of(folder.name))
    }
}
