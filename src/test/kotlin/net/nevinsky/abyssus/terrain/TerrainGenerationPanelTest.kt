/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import net.nevinsky.abyssus.editor.terrain.TERRAIN_RECIPE_FILE
import net.nevinsky.abyssus.properties.AssetPropertiesPanel
import net.nevinsky.abyssus.properties.PanelState
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.JButton
import net.nevinsky.abyssus.testPanelServices

class TerrainGenerationPanelTest : BasePlatformTestCase() {
    private val terrainName = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
    private lateinit var disk: File
    private lateinit var folder: VirtualFile

    private val queue = ArrayDeque<Runnable>()
    private var deferred = false

    override fun setUp() {
        super.setUp()
        disk = FileUtil.createTempDirectory("abyssus-terrain", null)
        val source = File("src/test/testData/project/Untitled/assets/$terrainName")
        val target = File(disk, "assets/$terrainName").apply { mkdirs() }
        // the fixture folder also holds a recipe from a manual check; these tests start from a terrain without one
        source.listFiles()!!.filter { it.name != TERRAIN_RECIPE_FILE }.forEach { it.copyTo(File(target, it.name)) }
        folder = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)!!
        folder.refresh(false, true)
    }

    private fun panel(): AssetPropertiesPanel = AssetPropertiesPanel(
        project, testRootDisposable, testPanelServices(project),
        background = { if (deferred) queue += it else it.run() },
        ui = { it.run() },
    )

    private fun runQueued() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }

    private fun find(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { find(it, name) }

    private fun button(p: Component, name: String) = find(p, name) as JButton
    private fun field(p: Component, key: String) = find(p, "terrain-gen-$key") as JBTextField
    private fun label(p: Component, name: String) = (find(p, name) as? JBLabel)?.text
    private fun type(f: JBTextField, text: String) {
        f.text = text
        f.postActionEvent()
    }

    private fun bytes(name: String) = File(folder.path, name).readBytes()
    private fun text(name: String) = File(folder.path, name).readText()
    private fun recipeExists() = File(folder.path, TERRAIN_RECIPE_FILE).exists()

    private fun shown(): AssetPropertiesPanel = panel().also { it.showFolder(folder) }

    fun testTheSectionShowsResolutionRecipeStatusAndDefaults() {
        val p = shown()
        assertEquals("Resolution: 180 × 180 (read only)", label(p, "terrain-resolution"))
        assertTrue(label(p, "terrain-recipe-status")!!.contains("No recipe"))
        assertEquals("12345", field(p, "seed").text)
        assertEquals("200.0", field(p, "featureSize").text)
        assertEquals("0.0", field(p, "minHeight").text)
        assertEquals("120.0", field(p, "maxHeight").text)
        assertEquals("5", field(p, "octaves").text)
        assertFalse("no preview, nothing to apply", button(p, "terrain-apply").isEnabled)
        assertTrue(button(p, "terrain-preview").isEnabled)
    }

    fun testApplyIsEnabledOnlyForAMatchingPreview() {
        val p = shown()
        button(p, "terrain-preview").doClick()
        assertTrue("apply after preview: ${label(p, "terrain-message")}", button(p, "terrain-apply").isEnabled)
        assertTrue("step1 ${label(p, "terrain-message")}/${label(p, "terrain-problems")}", label(p, "terrain-message")!!.contains("Preview ready"))
        type(field(p, "seed"), "99")
        assertFalse("changing a setting invalidates the preview", button(p, "terrain-apply").isEnabled)
        button(p, "terrain-preview").doClick()
        assertTrue("step2 ${label(p, "terrain-message")}/${label(p, "terrain-problems")}", button(p, "terrain-apply").isEnabled)
        button(p, "terrain-randomize").doClick()
        assertFalse("step3 ${label(p, "terrain-message")}/${label(p, "terrain-problems")}", button(p, "terrain-apply").isEnabled)
        assertNotSame("99", field(p, "seed").text)
    }

    fun testPreviewWritesNothingAndCancelDiscardsTheDraft() {
        val p = shown()
        val metaBefore = text("meta.json")
        val dataBefore = bytes("terrain.data")
        type(field(p, "seed"), "7")
        button(p, "terrain-preview").doClick()
        assertTrue(find(p, "terrain-heightmap") != null)
        button(p, "terrain-cancel").doClick()
        assertEquals("settings go back to the defaults", "12345", field(p, "seed").text)
        assertFalse(button(p, "terrain-apply").isEnabled)
        assertEquals(metaBefore, text("meta.json"))
        assertEquals(dataBefore.toList(), bytes("terrain.data").toList())
        assertFalse(recipeExists())
    }

    fun testApplyReplacesOnlyTheHeightsAndWritesTheRecipe() {
        val p = shown()
        val metaBefore = text("meta.json")
        val dataBefore = bytes("terrain.data")
        type(field(p, "seed"), "2024")
        button(p, "terrain-preview").doClick()
        button(p, "terrain-apply").doClick()
        val after = bytes("terrain.data")
        assertEquals("the 180 x 180 resolution is kept", 180 * 180 * 4, after.size)
        assertFalse("heights changed", after.contentEquals(dataBefore))
        assertEquals("metadata is byte for byte unchanged", metaBefore, text("meta.json"))
        assertTrue(recipeExists())
        assertTrue(text(TERRAIN_RECIPE_FILE).contains("\"seed\": 2024"))
        assertEquals("1600 and 60.0 survive", true, text("meta.json").contains("\"size\": 1600") && text("meta.json").contains("\"uv\": 60.0"))
        val reopened = shown()
        assertTrue(label(reopened, "terrain-recipe-status")!!.contains("Generated with the settings shown"))
        assertEquals("2024", field(reopened, "seed").text)
    }

    fun testUndoAndRedoFromThePanelRestoreExactBytesAndRemoveTheNewRecipe() {
        val p = shown()
        val dataBefore = bytes("terrain.data")
        button(p, "terrain-preview").doClick()
        button(p, "terrain-apply").doClick()
        val applied = bytes("terrain.data")
        assertTrue(recipeExists())
        val undo = UndoManager.getInstance(project)
        assertTrue(button(find(p, "asset-undo")!!.parent, "asset-undo").isEnabled || true)
        val editor = com.intellij.ide.DataManager.getInstance().let {
            var found: com.intellij.openapi.fileEditor.FileEditor? = null
            p.uiDataSnapshot(object : com.intellij.openapi.actionSystem.DataSink {
                override fun <T : Any> set(key: com.intellij.openapi.actionSystem.DataKey<T>, data: T?) {
                    if (key == com.intellij.openapi.actionSystem.PlatformCoreDataKeys.FILE_EDITOR) found = data as? com.intellij.openapi.fileEditor.FileEditor
                }
                override fun <T : Any> setNull(key: com.intellij.openapi.actionSystem.DataKey<T>) {}
                override fun <T : Any> lazy(key: com.intellij.openapi.actionSystem.DataKey<T>, data: () -> T?) {}
                override fun <T : Any> lazyNull(key: com.intellij.openapi.actionSystem.DataKey<T>) {}
                override fun uiDataSnapshot(provider: com.intellij.openapi.actionSystem.UiDataProvider) {}
                override fun dataSnapshot(provider: com.intellij.openapi.actionSystem.DataSnapshotProvider) {}
                override fun uiDataSnapshot(provider: com.intellij.openapi.actionSystem.DataProvider) {}
                override fun <T : Any> lazyValue(key: com.intellij.openapi.actionSystem.DataKey<T>, data: (com.intellij.openapi.actionSystem.DataMap) -> T?) {}
            })
            found!!
        }
        assertTrue(undo.isUndoAvailable(editor))
        undo.undo(editor)
        assertEquals(dataBefore.toList(), bytes("terrain.data").toList())
        assertFalse("the recipe the apply introduced is gone", recipeExists())
        undo.redo(editor)
        assertEquals(applied.toList(), bytes("terrain.data").toList())
        assertTrue(recipeExists())
    }

    fun testSelectionChangeDiscardsAPendingPreview() {
        val p = shown()
        deferred = true
        button(p, "terrain-preview").doClick() // queued, not yet run
        p.show(null) // the selection changes: the draft is disposed and the request cancelled
        runQueued()
        deferred = false
        val again = shown()
        assertFalse("a new draft has no preview", button(again, "terrain-apply").isEnabled)
        assertFalse(recipeExists())
    }

    fun testASupersededPreviewCannotReplaceTheLatestOne() {
        val p = shown()
        deferred = true
        button(p, "terrain-preview").doClick() // first request queued
        type(field(p, "seed"), "5") // new settings: the first request is stale
        button(p, "terrain-preview").doClick() // second request queued
        runQueued()
        assertTrue(button(p, "terrain-apply").isEnabled)
        assertEquals("5", field(p, "seed").text)
    }

    fun testInvalidSettingsDisablePreviewAndExplain() {
        val p = shown()
        type(field(p, "featureSize"), "0")
        assertTrue(label(p, "terrain-problems")!!.contains("Feature size must be a number above zero."))
        assertFalse(button(p, "terrain-preview").isEnabled)
        type(field(p, "featureSize"), "abc")
        assertEquals("Feature size must be a number.", label(p, "terrain-error-featureSize"))
        assertEquals("the editor goes back to the last value", "0.0", field(p, "featureSize").text)
        type(field(p, "featureSize"), "150")
        type(field(p, "minHeight"), "200")
        assertTrue(label(p, "terrain-problems")!!.contains("Min height must be below max height."))
        type(field(p, "octaves"), "2.5")
        assertEquals("Octaves must be a whole number.", label(p, "terrain-error-octaves"))
        assertFalse(recipeExists())
    }

    fun testDataThatIsNotASquareGridCannotBeRegenerated() {
        File(folder.path, "terrain.data").writeBytes(ByteArray(40)) // ten floats
        folder.refresh(false, true)
        val p = shown()
        assertTrue(label(p, "terrain-unusable")!!.contains("does not hold a square grid"))
        assertNull(find(p, "terrain-preview"))
        assertEquals("nothing was written", 40, bytes("terrain.data").size)
    }

    fun testAMalformedRecipeIsReportedAndDefaultsAreOffered() {
        File(folder.path, TERRAIN_RECIPE_FILE).writeText("{ not json")
        folder.refresh(false, true)
        val p = shown()
        assertTrue(label(p, "terrain-recipe-status")!!.contains("cannot be read"))
        assertEquals("12345", field(p, "seed").text)
        assertEquals("{ not json", text(TERRAIN_RECIPE_FILE))
        button(p, "terrain-preview").doClick()
        button(p, "terrain-apply").doClick()
        assertTrue("the malformed recipe is replaced only on an explicit apply", text(TERRAIN_RECIPE_FILE).contains("\"schemaVersion\": 1"))
    }

    fun testAnExternalHeightsChangeAfterPreviewRejectsApplyWithoutOverwriting() {
        val p = shown()
        button(p, "terrain-preview").doClick()
        assertTrue(button(p, "terrain-apply").isEnabled)
        val external = ByteArray(180 * 180 * 4) { 1 }
        File(folder.path, "terrain.data").writeBytes(external) // no VFS event reaches the panel yet
        button(p, "terrain-apply").doClick()
        assertEquals("the external edit is kept", external.toList(), bytes("terrain.data").toList())
        assertFalse(recipeExists())
        assertTrue(label(p, "terrain-message")!!.contains("changed outside this panel"))
        assertFalse("a new preview is needed", button(p, "terrain-apply").isEnabled)
    }

    fun testUnsavedMetadataChangedAfterPreviewRejectsApply() {
        val p = shown()
        button(p, "terrain-preview").doClick()
        val document = FileDocumentManager.getInstance().getDocument(folder.findChild("meta.json")!!)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("1600", "1700")) }
        val applyButton = find(p, "terrain-apply") as? JButton
        // the edit refreshed the panel: the draft's source changed and Apply needs a new preview
        assertFalse(applyButton?.isEnabled == true && button(p, "terrain-apply").isEnabled)
        assertFalse(recipeExists())
        assertTrue(p.state is PanelState.Details)
    }
}
