package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Declarations of a Game UI asset (GUI-05, GUI-06): bindings and styles added through their validated forms, and an image and
 * a bitmap font imported from the local `import-inputs`, each as one undoable edit of the asset. The forms are reached by
 * their names (`form-name`, `form-sample` ...) and the tabs of the editor's declarations panel.
 */
class GameUiDeclarationLifecycleUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val hud: Path get() = project.dir.resolve("assets/hud/meta.json")
    private val mapper = ObjectMapper()

    @After
    fun closeEditor() {
        runCatching { frame.closeStrayDialogs() }
        runCatching { ide.closeFile(hud.toString()) }
    }

    private fun doc(): String = ide.documentText(hud.toString()) ?: Files.readString(hud)

    private fun node(): JsonNode = mapper.readTree(doc())

    private fun named(parent: JsonNode, key: String): List<JsonNode> = parent.findValues(key).flatMap { v -> if (v.isArray) v.toList() else listOf(v) }

    private fun createAssetAndOpenEditor() {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        LifecycleHarness.awaitState("the tree shows Assets", true) { tree.rows().any { it.startsWith("Assets") } }
        tree.rightClickRow("Assets")
        frame.clickMenuPath("New", "New UI Asset...")
        val dialog = frame.dialog("New UI Asset").awaitOpen()
        dialog.type("hud")
        dialog.click("OK")
        dialog.awaitClosed()
        LifecycleHarness.awaitState("hud exists", true) { Files.exists(hud) }
        frame.saveAll()
        ide.openUiEditor(hud.toString())
        LifecycleHarness.awaitState("the editor tab is shown", true) { frame.findAll<ComponentFixture>(byXpath("//div[@name='add-widget']")).isNotEmpty() }
        Thread.sleep(1500)
    }

    /** Selects the tab titled [title] in whichever tabbed pane of the editor has it: the side panel's Declarations, then Bindings, Styles or Resources. */
    private fun tab(title: String) {
        val panes = frame.findAll<ComponentFixture>(byXpath("//div[@class='JTabbedPane']"))
        val pane = panes.first { it.callJs<Int>("component.indexOfTab('$title')", runInEdt = true) >= 0 }
        pane.callJs<Boolean>("component.setSelectedIndex(component.indexOfTab('$title')); true", runInEdt = true)
        Thread.sleep(600)
    }

    /** One Undo of the asset's document, through its text editor, then saved. */
    private fun undoOnce() {
        ide.openTextEditor(hud.toString())
        try {
            com.intellij.remoterobot.utils.waitFor(Duration.ofSeconds(20), Duration.ofMillis(200), "the asset's text editor has focus") { ide.editorHasFocus() }
            frame.undo()
            Thread.sleep(1500)
            frame.saveAll()
        } finally {
            ide.closeFile(hud.toString())
        }
    }

    private fun click(name: String) = frame.find<ComponentFixture>(byXpath("//div[@name='$name']"), Duration.ofSeconds(10)).click()

    @Test
    fun aBindingAndAStyleAreValidatedBeforeOkAndEachIsOneUndoStep() {
        createAssetAndOpenEditor()
        val original = doc()

        tab("Declarations")
        tab("Bindings")
        click("add-binding")
        val binding = frame.dialog("Binding").awaitOpen()
        assertFalse("OK waits for a name", binding.isButtonEnabled("OK"))
        binding.typeNamed("form-name", "score")
        binding.typeNamed("form-sample", "abc")
        LifecycleHarness.awaitState("a sample that does not match the type is refused", true) { !binding.isButtonEnabled("OK") }
        binding.typeNamed("form-sample", "42")
        LifecycleHarness.awaitState("a matching sample enables OK", true) { binding.isButtonEnabled("OK") }
        assertEquals("the form wrote nothing", original, doc())
        binding.click("OK")
        binding.awaitClosed()
        LifecycleHarness.awaitState("the binding is declared", true) { named(node(), "bindings").any { it["name"]?.asText() == "score" } }
        val withBinding = doc()

        tab("Styles")
        click("add-style")
        val style = frame.dialog("Style").awaitOpen()
        style.typeNamed("form-name", "big")
        style.typeNamed("form-color", "red")
        LifecycleHarness.awaitState("a colour that is not #rrggbb is refused", true) { !style.isButtonEnabled("OK") }
        style.typeNamed("form-color", "#ff0000")
        style.typeNamed("form-fontSize", "24")
        LifecycleHarness.awaitState("a valid style enables OK", true) { style.isButtonEnabled("OK") }
        style.click("OK")
        style.awaitClosed()
        LifecycleHarness.awaitState("the style is declared", true) { named(node(), "styles").any { it["name"]?.asText() == "big" } }

        frame.saveAll()
        undoThroughSceneDocument(hud, withBinding)
        assertTrue("the first Undo removes only the style", named(node(), "styles").none { it["name"]?.asText() == "big" } && named(node(), "bindings").any { it["name"]?.asText() == "score" })
        undoThroughSceneDocument(hud, original)
        assertTrue("the second Undo removes the binding", named(node(), "bindings").none { it["name"]?.asText() == "score" })
    }

    @Test
    fun anImageAndABitmapFontAreImportedFromLocalFilesAsOneUndoableEditEach() {
        val inputs = ImportInputs.stage(Files.createTempDirectory("abyssus-ui-inputs"))
        val sourceBefore = Files.walk(inputs.folder).use { w -> w.filter { Files.isRegularFile(it) }.toList().associate { it.toString() to ImportInputs.sha256(Files.readAllBytes(it)) } }
        createAssetAndOpenEditor()
        val original = doc()
        val folderBefore = Files.walk(project.dir.resolve("assets/hud")).use { w -> w.filter { Files.isRegularFile(it) }.map { it.toString() }.toList().toSet() }

        tab("Declarations")
        tab("Resources")
        reset.chooseNextFile(inputs.image.toString())
        click("import-image")
        LifecycleHarness.awaitState("the image is declared", true) { named(node(), "resources").any { it["kind"]?.asText() == "image" } }
        val withImage = doc()
        val imageFiles = Files.walk(project.dir.resolve("assets/hud")).use { w -> w.filter { Files.isRegularFile(it) }.map { it.toString() }.toList().toSet() } - folderBefore
        assertTrue("the image was copied into the asset folder: $imageFiles", imageFiles.any { it.endsWith("button.png") })

        // an import is refused while the asset's document has unsaved changes: save, as a user is told to
        frame.saveAll()
        reset.chooseNextFile(inputs.font.toString())
        click("import-font")
        LifecycleHarness.awaitState("the font is declared", "declared") {
            if (named(node(), "resources").any { it["kind"]?.asText() == "bitmapFont" }) "declared"
            else "resources ${named(node(), "resources")}, dialog open ${runCatching { frame.openDialogs("Resources") }.getOrNull()}, texts ${runCatching { frame.dialog("Resources").texts() }.getOrNull()}"
        }
        val allFiles = Files.walk(project.dir.resolve("assets/hud")).use { w -> w.filter { Files.isRegularFile(it) }.map { it.fileName.toString() }.toList() }
        assertTrue("the .fnt and its page were copied: $allFiles", allFiles.contains("ui-font.fnt") && allFiles.contains("ui-font.png"))
        assertEquals("the sources are untouched", sourceBefore, Files.walk(inputs.folder).use { w -> w.filter { Files.isRegularFile(it) }.toList().associate { it.toString() to ImportInputs.sha256(Files.readAllBytes(it)) } })

        // importing the same image again gets its own id and file name
        frame.saveAll()
        reset.chooseNextFile(inputs.image.toString())
        click("import-image")
        LifecycleHarness.awaitState("a second image is declared with its own id", true) { named(node(), "resources").count { it["kind"]?.asText() == "image" } == 2 }
        val ids = named(node(), "resources").map { it["id"].asText() }
        assertEquals("resource ids are unique", ids.size, ids.toSet().size)

        // Undo takes imports back newest first, and every step leaves declarations and copied files in agreement
        frame.saveAll()
        val declared = { named(node(), "resources").map { it["path"].asText() } }
        fun files() = Files.walk(project.dir.resolve("assets/hud")).use { w -> w.filter { Files.isRegularFile(it) }.map { project.dir.resolve("assets/hud").relativize(it).toString().replace('\\', '/') }.toList() }
        fun consistent() = declared().all { it in files() }
        assertEquals(3, declared().size)
        assertTrue("the imports are consistent before Undo", consistent())
        var steps = 0
        while (declared().isNotEmpty() && steps < 4) {
            val before = declared().size
            undoOnce()
            steps++
            assertTrue("Undo $steps removed an import ($before -> ${declared().size})", declared().size < before)
            assertTrue("Undo $steps left a declaration without its file: ${declared()} vs ${files()}", consistent())
        }
        assertTrue("Undo took back every import", declared().isEmpty())
        assertTrue("and every copied file", files().none { it.endsWith("button.png") || it.endsWith("ui-font.fnt") || it.endsWith("ui-font.png") })
        assertTrue(withImage != original)
    }

    /** Clicks the first row of the list shown in the active declarations tab, as a user selects an entry. */
    private fun selectFirstListRow() {
        val list = frame.findAll<ComponentFixture>(byXpath("//div[@class='JBList']")).first { it.callJs<Boolean>("component.isShowing() && component.getModel().getSize() > 0", runInEdt = true) }
        list.click(java.awt.Point(20, 10))
        Thread.sleep(500)
    }

    @Test
    fun aBindingIsEditedAndRemovedWithOneUndoStepEach() {
        createAssetAndOpenEditor()
        tab("Declarations")
        tab("Bindings")
        click("add-binding")
        val add = frame.dialog("Binding").awaitOpen()
        add.typeNamed("form-name", "score")
        add.typeNamed("form-sample", "42")
        add.click("OK")
        add.awaitClosed()
        LifecycleHarness.awaitState("the binding is declared", true) { named(node(), "bindings").any { it["name"]?.asText() == "score" } }
        val added = doc()

        selectFirstListRow()
        click("edit-binding")
        val edit = frame.dialog("Binding").awaitOpen()
        assertEquals("the form shows the stored name", "score", edit.namedText("form-name"))
        edit.typeNamed("form-sample", "7")
        LifecycleHarness.awaitState("OK is available", true) { edit.isButtonEnabled("OK") }
        edit.click("OK")
        edit.awaitClosed()
        LifecycleHarness.awaitState("the sample changed", "7") { named(node(), "bindings").first { it["name"].asText() == "score" }["sample"].asText() }
        val edited = doc()

        selectFirstListRow()
        click("remove-binding")
        LifecycleHarness.awaitState("the binding is removed", true) { named(node(), "bindings").none { it["name"]?.asText() == "score" } }

        frame.saveAll()
        undoThroughSceneDocument(hud, edited)
        assertTrue("the first Undo brings the edited binding back", named(node(), "bindings").any { it["name"]?.asText() == "score" })
        undoThroughSceneDocument(hud, added)
        assertEquals("the second Undo restores the first sample", "42", named(node(), "bindings").first { it["name"].asText() == "score" }["sample"].asText())
    }
}
