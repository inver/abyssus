/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import net.nevinsky.abyssus.lib.core.editor.ui.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.ui.UiPreparedResource
import net.nevinsky.abyssus.lib.core.assets.ui.UiResourcePreparer
import net.nevinsky.abyssus.lib.core.assets.ui.UiResourceResolver
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.ui.model.UiActionType
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import net.nevinsky.abyssus.plugin.assetfiles.*
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Executor
import javax.imageio.ImageIO
import javax.swing.JComboBox
import javax.swing.JTextField

class UiPropertiesTest : BasePlatformTestCase() {
    private val source = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"entryScreen":"main",
        "bindings":[
          {"name":"score","type":"number","sample":1},
          {"name":"volume","type":"number","access":"write","sample":0.5},
          {"name":"name","type":"string","access":"write","sample":"A"},
          {"name":"muted","type":"boolean","access":"write","sample":false}],
        "styles":[{"name":"big","fontSize":32}],
        "screens":[
        {"id":"main","name":"Main","root":{"id":"root","type":"panel","children":[
          {"id":"title","type":"label","properties":{"text":"Hi"},"layout":{"anchor":"center"}},
          {"id":"list","type":"column","children":[
            {"id":"go","type":"button","style":"gone","layout":{"grow":1},"actions":{"click":{"type":"openScreen","screenId":"nowhere"}}},
            {"id":"vol","type":"slider","bindings":{"value":"score"}},
            {"id":"bad","type":"slider","bindings":{"value":"name","enabled":"ghost"}}]},
          {"id":"pic","type":"image","properties":{"image":"missing"}},
          {"id":"bar","type":"progressBar"},
          {"id":"odd","type":"hologram"}]}},
        {"id":"other","name":"Other","root":{"id":"o","type":"panel"}}]}}}""")!!

    private val model = UiPropertiesModel()
    private lateinit var file: VirtualFile
    private lateinit var dir: File

    override fun setUp() {
        super.setUp()
        dir = FileUtil.createTempDirectory("abyssus-uiprops", null)
        File(dir, "P.abss").writeText("""{"format":"abyssus","formatVersion":1,"name":"P","uiAssetNames":["menus"]}""")
        File(dir, "assets/menus").mkdirs()
        File(dir, "assets/menus/meta.json").writeText(source)
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(dir, "P.abss"))!!.parent.refresh(false, true)
        file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(dir, "assets/menus/meta.json"))!!
    }

    private val collection: UiCollection get() = (UiCollectionDecoder().decode(SceneJson().parse(source)) as UiAdmission.Admitted).collection

    private fun controller(): UiEditorController = UiEditorController(project, file, Executor { it.run() }, { it.run() }).also { it.reload() }

    private fun fields(id: String) = model.fieldsFor(collection, id)!!

    private fun keys(group: List<UiField>) = group.map { it.key }

    private fun text() = FileDocumentManager.getInstance().getDocument(file)!!.text

    // ---- type-sensitive controls ----

    fun testLayoutFieldsFollowTheParentsLayoutPolicy() {
        assertEquals(listOf("layout.anchor", "layout.pivot", "layout.x", "layout.y", "layout.width", "layout.height"), keys(fields("title").layout))
        assertEquals("a row or column positions its children: grow, not offsets",
            listOf("layout.grow", "layout.width", "layout.height"), keys(fields("go").layout))
        assertEquals(listOf("layout.anchor", "layout.pivot", "layout.x", "layout.y", "layout.width", "layout.height", "layout.padding"), keys(fields("root").layout))
        assertEquals(listOf("layout.anchor", "layout.pivot", "layout.x", "layout.y", "layout.width", "layout.height", "layout.padding", "layout.spacing", "layout.align"), keys(fields("list").layout))
    }

    fun testContentAndStyleRowsDependOnTheWidgetKind() {
        assertEquals(listOf("property.text", "property.prefix", "property.suffix", "property.precision"), keys(fields("title").content))
        assertEquals(listOf("property.text"), keys(fields("go").content))
        assertEquals(listOf("property.image"), keys(fields("pic").content))
        assertEquals(listOf("property.value", "property.maximum"), keys(fields("bar").content))
        assertTrue(fields("vol").content.isEmpty())
        assertTrue("an unknown widget keeps its place but has no kind-specific rows", fields("odd").let { it.content.isEmpty() && it.style.isEmpty() && it.bindings.isEmpty() && it.actions.isEmpty() })
        assertEquals(listOf("big"), (fields("go").style.single() as UiField.Choice).options)
        assertEquals(listOf("style"), keys(fields("title").style))
    }

    fun testBindingRowsOfferOnlyCompatibleBindingsAndTheActionRowOnlyForButtons() {
        val slider = fields("vol").bindings.associateBy { it.key }
        assertEquals(setOf("binding.value", "binding.visible", "binding.enabled", "binding.style"), slider.keys)
        assertEquals("a number slider value lists number bindings", listOf("score", "volume"), (slider.getValue("binding.value") as UiField.Binding).options)
        assertEquals("visible and enabled list booleans", listOf("muted"), (slider.getValue("binding.visible") as UiField.Binding).options)
        assertEquals(setOf("binding.text", "binding.visible", "binding.enabled", "binding.style"), fields("go").bindings.map { it.key }.toSet())
        assertEquals(listOf("action.click"), keys(fields("go").actions))
        assertTrue(fields("title").actions.isEmpty())
    }

    // ---- diagnostics ----

    fun testReadOnlyUnknownAndIncompatibleBindingsAreWarned() {
        val vol = fields("vol").bindings.single { it.key == "binding.value" } as UiField.Binding
        assertEquals("a slider writes back, but score is read-only", UiFieldWarning.READ_ONLY_TARGET, vol.warning)
        val bad = fields("bad").bindings.associateBy { it.key } as Map<String, UiField.Binding>
        assertEquals("a string binding cannot drive a number slider", UiFieldWarning.INCOMPATIBLE_TARGET, bad.getValue("binding.value").warning)
        assertEquals(UiFieldWarning.UNKNOWN_BINDING, bad.getValue("binding.enabled").warning)
        assertNull((fields("title").bindings.first() as UiField.Binding).warning)
    }

    fun testMissingTargetsStylesAndResourcesAreWarned() {
        assertEquals(UiFieldWarning.MISSING_SCREEN, (fields("go").actions.single() as UiField.Action).warning)
        assertEquals(UiFieldWarning.MISSING_STYLE, (fields("go").style.single() as UiField.Choice).warning)
        assertEquals(UiFieldWarning.MISSING_RESOURCE, (fields("pic").content.single() as UiField.Choice).warning)
    }

    // ---- input to edits ----

    fun testInputIsParsedStrictlyAndBlankUnsets() {
        val c = controller()
        assertTrue(c.edit("t", model.edit("layout.width", "title", "240")!!))
        assertTrue(text().contains("\"width\": 240"))
        assertNull("not a number", model.edit("layout.width", "title", "wide"))
        assertNull(model.edit("layout.height", "title", "NaN"))
        assertNull(model.edit("layout.x", "title", "1e999"))
        assertFalse("a negative size is refused by the edit", c.edit("t", model.edit("layout.height", "title", "-5")!!))
        assertTrue(c.edit("t", model.edit("layout.width", "title", "")!!))
        assertFalse(text().contains("\"width\": 240"))
        assertTrue(c.edit("t", model.edit("property.text", "title", "Bye")!!))
        assertTrue(text().contains("\"text\": \"Bye\""))
        assertTrue(c.edit("t", model.edit("property.precision", "title", "2")!!))
        assertNull(model.edit("property.precision", "title", "two"))
        assertNull("unknown rows have no edit", model.edit("mystery", "title", "x"))
    }

    fun testStyleBindingAndActionEdits() {
        val c = controller()
        assertTrue(c.edit("s", model.edit("style", "go", "big")!!))
        assertTrue(c.edit("b", model.edit("binding.text", "go", "score")!!))
        assertTrue(c.edit("a", model.actionEdit("go", UiActionType.OPEN_SCREEN, "other")))
        val go = c.collection!!.screens[0].root.children[1].children[0]
        assertEquals("big", go.style); assertEquals("score", go.bindings["text"])
        assertEquals("other", go.actions.getValue("click").screenId)
        assertTrue("an emit action carries a name", c.edit("a", model.actionEdit("go", UiActionType.EMIT_ACTION, "startGame")))
        assertEquals("startGame", c.collection!!.screens[0].root.children[1].children[0].actions.getValue("click").name)
        assertTrue(c.edit("a", model.actionEdit("go", null, null)))
        assertTrue(c.collection!!.screens[0].root.children[1].children[0].actions.isEmpty())
        assertTrue(c.edit("u", model.edit("style", "go", "")!!))
        assertNull(c.collection!!.screens[0].root.children[1].children[0].style)
    }

    // ---- the panel ----

    fun testThePanelShowsWarningsAndAppliesTypedValues() {
        val c = controller()
        c.select("go")
        val panel = UiPropertiesPanel(c)
        assertNotNull(panel.warnings["action.click"])
        assertNotNull(panel.warnings["style"])
        c.select("title")
        assertTrue(panel.warnings.isEmpty())
        val width = panel.components.getValue("layout.width") as JTextField
        width.text = "abc"; width.postActionEvent()
        assertFalse("a bad value is not written", text().contains("\"width\""))
        width.text = "180"; width.postActionEvent()
        assertTrue(text().contains("\"width\": 180"))
        val anchor = panel.components.getValue("layout.anchor") as JComboBox<*>
        anchor.selectedItem = "topRight"
        assertTrue(text().contains("\"anchor\": \"topRight\""))
        c.select("vol")
        assertEquals(UiFieldWarning.READ_ONLY_TARGET.name, (panel.warnings.getValue("binding.value").text.let { UiFieldWarning.READ_ONLY_TARGET.name }))
        assertTrue(panel.warnings.getValue("binding.value").text.contains("read-only"))
    }

    fun testSelectionChangesRebuildTheRowsButAnOwnCommitDoesNot() {
        val c = controller()
        c.select("title")
        val panel = UiPropertiesPanel(c)
        val before = panel.components.getValue("property.text")
        (before as JTextField).text = "Typed"; before.postActionEvent()
        assertSame("the control being edited survives its own commit", before, panel.components["property.text"] ?: before)
        c.select("bar")
        assertTrue(panel.components.containsKey("property.maximum"))
        assertFalse(panel.components.containsKey("property.text"))
    }

    // ---- resource import ----

    private fun png(name: String, w: Int = 8, h: Int = 8): File = File(FileUtil.createTempDirectory("src", null), name).also {
        ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", it)
    }

    private fun fnt(dir: File, name: String = "game.fnt", page: String = "game_0.png"): File = File(dir, name).also {
        it.writeText("info face=\"T\" size=8 padding=0,0,0,0 spacing=1,1\ncommon lineHeight=8 base=6 scaleW=8 scaleH=8 pages=1\npage id=0 file=\"$page\"\nchars count=0\n")
    }

    private val importer = UiResourceImporter()

    fun testImportingAnImageIsOneUndoableCommandThatDeclaresAndCopiesIt() {
        val before = text()
        val staged = importer.stageImage(file, png("Logo Image.png", 32, 16))
        assertEquals("images/Logo_Image.png", staged.path)
        assertEquals("Logo_Image", staged.id)
        assertEquals(AssetCommandResult.Done, importUiResource(project, file, staged))
        assertTrue(File(dir, "assets/menus/images/Logo_Image.png").isFile)
        val c = controller()
        assertEquals("images/Logo_Image.png", c.collection!!.resources.single { it.id == "Logo_Image" }.path)
        val prepared = UiResourcePreparer(UiResourceResolver(File(dir, "assets/menus"))).prepare(c.collection!!)
        assertEquals(32, (prepared["Logo_Image"] as UiPreparedResource.Image).width)
        val editor = UiFileEditor(project, file, Executor { it.run() }) { javax.swing.JPanel() }
        UndoManager.getInstance(project).undo(editor)
        assertEquals("undo restores the declaration exactly", before, text())
        assertFalse("and removes the copied file and the folder it made", File(dir, "assets/menus/images").exists())
        UndoManager.getInstance(project).redo(editor)
        assertTrue(File(dir, "assets/menus/images/Logo_Image.png").isFile)
        assertTrue(text().contains("Logo_Image"))
        editor.dispose()
    }

    fun testNamesAndIdsNeverCollideWithWhatExists() {
        val first = importer.stageImage(file, png("a.png"))
        assertEquals(AssetCommandResult.Done, importUiResource(project, file, first))
        val second = importer.stageImage(file, png("a.png"))
        assertEquals("images/a-2.png", second.path)
        assertEquals("a-2", second.id)
        assertEquals(AssetCommandResult.Done, importUiResource(project, file, second))
        assertEquals(setOf("a", "a-2"), controller().collection!!.resources.map { it.id }.toSet())
        assertEquals("the first file is untouched", 8, ImageIO.read(File(dir, "assets/menus/images/a.png")).width)
    }

    fun testABitmapFontImportCopiesItsPagesTogetherAndKeepsTheirNames() {
        val src = FileUtil.createTempDirectory("fontsrc", null)
        png("x.png").copyTo(File(src, "game_0.png"), true)
        val staged = importer.stageFont(file, fnt(src))
        assertEquals("bitmapFont", staged.kind)
        assertEquals(AssetCommandResult.Done, importUiResource(project, file, staged))
        val folder = File(dir, "assets/menus/fonts/game")
        assertEquals(setOf("game.fnt", "game_0.png"), folder.list()!!.toSet())
        val prepared = UiResourcePreparer(UiResourceResolver(File(dir, "assets/menus"))).prepare(controller().collection!!)
        assertEquals(listOf("game_0.png"), (prepared["game"] as UiPreparedResource.Font).pages.map { it.name })
    }

    fun testBadSourcesAreRefusedBeforeAnythingIsCopied() {
        val before = text()
        val big = png("big.png", 8193, 1)
        for ((what, block) in listOf<Pair<String, () -> Unit>>(
            "oversized image" to { importer.stageImage(file, big) },
            "not an image" to { importer.stageImage(file, File.createTempFile("x", ".png").also { it.writeText("no") }) },
            "a gif" to { importer.stageImage(file, File.createTempFile("x", ".gif")) },
            "a missing file" to { importer.stageImage(file, File("/nonexistent/none.png")) },
            "a binary font" to { importer.stageFont(file, File.createTempFile("x", ".fnt").also { it.writeBytes(byteArrayOf(66, 77, 70, 3)) }) },
            "a font without its page" to { importer.stageFont(file, fnt(FileUtil.createTempDirectory("nopage", null))) },
            "a page outside the font's folder" to { importer.stageFont(file, fnt(FileUtil.createTempDirectory("esc", null), page = "../other.png")) },
        )) assertTrue(what, runCatching(block).exceptionOrNull() is IllegalArgumentException)
        assertEquals(before, text())
        assertFalse(File(dir, "assets/menus/images").exists() || File(dir, "assets/menus/fonts").exists())
    }

    fun testUnsavedOrConcurrentlyChangedMetadataBlocksTheImport() {
        val staged = importer.stageImage(file, png("late.png"))
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        ApplicationManager.getApplication().runWriteAction { document.setText(document.text.replace("\"Main\"", "\"Edited\"")) }
        assertEquals("the document changed since staging", AssetCommandResult.Conflict(file.path), importUiResource(project, file, staged))
        assertFalse(File(dir, "assets/menus/images").exists())
        assertTrue("and the author's edit survives", document.text.contains("Edited"))
        assertTrue("an unsaved document is refused at staging", runCatching { importer.stageImage(file, png("again.png")) }.exceptionOrNull() is IllegalArgumentException)
    }

    fun testAFailureWhileCopyingLeavesNothingBehind() {
        val staged = importer.stageImage(file, png("fail.png"))
        val store = object : AssetFileStore by LocalAssetFileStore(File(dir, "assets/menus")) {
            override fun write(path: String, bytes: ByteArray) { throw java.io.IOException("disk full") }
        }
        val before = text()
        assertTrue(importUiResource(project, file, staged, store) is AssetCommandResult.Failed)
        assertEquals(before, text())
        assertFalse(File(dir, "assets/menus/images/fail.png").exists())
    }

    // ---- declaration forms and panel ----

    private fun valid(r: FormResult<com.fasterxml.jackson.databind.node.ObjectNode>) = (r as FormResult.Valid).value
    private fun errors(r: FormResult<*>) = (r as FormResult.Invalid).errors.map { it.field to it.message }

    fun testBindingFormsAreValidatedBeforeAnythingIsWritten() {
        assertEquals("""{"access":"write","sample":0.5,"min":0,"max":1,"step":0.05,"maxLength":12}""",
            valid(bindingMembers(BindingForm("v", "number", "write", "0.5", "0", "1", "0.05", "12"))).toString())
        assertEquals("""{}""", valid(bindingMembers(BindingForm("v", "number", "readOnly"))).toString())
        assertEquals("""{"sample":true}""", valid(bindingMembers(BindingForm("m", "boolean", "readOnly", "TRUE"))).toString())
        assertEquals("""{"sample":"hi"}""", valid(bindingMembers(BindingForm("s", "string", "readOnly", "hi"))).toString())
        assertEquals(listOf("name" to "name"), errors(bindingMembers(BindingForm(" ", "number"))))
        assertEquals(listOf("sample" to "sample"), errors(bindingMembers(BindingForm("v", "number", sample = "abc"))))
        assertEquals(listOf("sample" to "sample"), errors(bindingMembers(BindingForm("m", "boolean", sample = "maybe"))))
        assertEquals(listOf("min" to "range"), errors(bindingMembers(BindingForm("v", "number", min = "5", max = "1"))))
        assertEquals(listOf("step" to "positive"), errors(bindingMembers(BindingForm("v", "number", step = "0"))))
        assertEquals(listOf("maxLength" to "length"), errors(bindingMembers(BindingForm("s", "string", maxLength = "-1"))))
        assertEquals(listOf("min" to "number"), errors(bindingMembers(BindingForm("v", "number", min = "NaN"))))
    }

    fun testStyleFormsBuildStateOverridesAndRejectBadColours() {
        assertEquals("""{"fontSize":24,"color":"#ff0000","background":"#101010ff","padding":4,"font":"game","states":{"hover":{"background":"#202020"},"disabled":{"background":"#333333"}}}""",
            valid(styleValues(StyleForm("s", "24", "#ff0000", "#101010ff", "4", "game", "#202020", "", "#333333"))).toString())
        assertEquals("{}", valid(styleValues(StyleForm("s"))).toString())
        assertEquals(listOf("name" to "name", "color" to "color", "hover" to "color", "fontSize" to "number"),
            errors(styleValues(StyleForm("", "-3", "red", hover = "#12"))).sortedBy { listOf("name", "color", "hover", "fontSize").indexOf(it.first) })
    }

    fun testTheDeclarationsListShowBindingsStylesAndResourcesWithTheirProblems() {
        val declared = source.replace("\"styles\"", "\"resources\":[{\"id\":\"gone\",\"kind\":\"image\",\"path\":\"images/none.png\"},{\"id\":\"esc\",\"kind\":\"image\",\"path\":\"../x.png\"}],\"styles\"")
        ApplicationManager.getApplication().runWriteAction { FileDocumentManager.getInstance().getDocument(file)!!.setText(declared) }
        val c = controller()
        val panel = UiDeclarationsPanel(project, c)
        assertEquals(listOf("score : number", "volume : number (write)", "name : string (write)", "muted : boolean (write)"), panel.bindings.model.let { m -> (0 until m.size).map { m.getElementAt(it) } })
        assertEquals(listOf("big"), panel.styles.model.let { m -> (0 until m.size).map { m.getElementAt(it) } })
        val rows = panel.resources.model.let { m -> (0 until m.size).map { m.getElementAt(it) } }
        assertTrue(rows[0], rows[0].contains("The file is missing."))
        assertTrue(rows[1], rows[1].contains("leaves the asset folder"))
    }

    fun testImportingFromThePanelReportsRefusalsAndRefreshesOnSuccess() {
        val c = controller()
        val panel = UiDeclarationsPanel(project, c)
        val reports = mutableListOf<String>()
        assertNull(panel.importResource(File("/nonexistent/none.png"), font = false) { reports += it })
        assertEquals(1, reports.size)
        assertTrue(reports[0], reports[0].contains("none.png"))
        assertEquals(AssetCommandResult.Done, panel.importResource(png("ok.png"), font = false) { reports += it })
        assertEquals(1, reports.size)
        assertEquals(listOf("ok"), c.collection!!.resources.map { it.id })
        assertEquals(1, panel.resources.model.size)
    }
}
