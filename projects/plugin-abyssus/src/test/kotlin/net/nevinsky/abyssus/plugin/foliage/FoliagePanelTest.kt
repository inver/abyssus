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
        return readFoliageSource(folder.name, disk, json, text, json.readObject(text)["additional"]) as FoliageSource.Ready
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
}
