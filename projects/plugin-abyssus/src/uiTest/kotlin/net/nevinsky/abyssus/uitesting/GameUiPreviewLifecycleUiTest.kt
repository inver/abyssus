package net.nevinsky.abyssus.uitesting

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
 * The Game UI preview (GUI-08..): Preview mode, the viewport size and its refusal of a bad size, sample values and the way
 * back to authoring. Nothing the preview does writes the asset: its text and its file stay as they were.
 */
class GameUiPreviewLifecycleUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val hud: Path get() = project.dir.resolve("assets/hud/meta.json")

    @After
    fun closeEditor() {
        runCatching { frame.closeStrayDialogs() }
        runCatching { ide.closeFile(hud.toString()) }
    }

    private fun doc(): String = ide.documentText(hud.toString()) ?: Files.readString(hud)

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

    private fun tab(title: String) {
        val panes = frame.findAll<ComponentFixture>(byXpath("//div[@class='JTabbedPane']"))
        val pane = panes.first { it.callJs<Int>("component.indexOfTab('$title')", runInEdt = true) >= 0 }
        pane.callJs<Boolean>("component.setSelectedIndex(component.indexOfTab('$title')); true", runInEdt = true)
        Thread.sleep(600)
    }

    private fun named(name: String) = frame.find<ComponentFixture>(byXpath("//div[@name='$name']"), Duration.ofSeconds(10))

    private fun enterField(name: String, text: String) {
        named(name).click()
        frame.replaceFocusedText(text)
    }

    private fun text(name: String): String = named(name).callJs("component.getText()", runInEdt = true)

    private fun toggle(name: String) = named(name).callJs<Boolean>("component.doClick(); true", runInEdt = true)

    @Test
    fun previewModeAndItsViewportDoNotWriteTheAssetAndABadSizeIsRefused() {
        createAssetAndOpenEditor()
        val before = doc()
        val fileBefore = Files.readString(hud)

        toggle("mode-preview")
        tab("Preview")
        LifecycleHarness.awaitState("the viewport controls are shown", true) { frame.findAll<ComponentFixture>(byXpath("//div[@name='preview-width']")).isNotEmpty() }

        enterField("preview-width", "1280")
        enterField("preview-height", "720")
        named("preview-apply").click()
        LifecycleHarness.awaitState("the viewport is set", "1280 × 720") { named("preview-size").callJs<String>("String(component.getSelectedItem())", runInEdt = true) }

        enterField("preview-width", "0")
        named("preview-apply").click()
        LifecycleHarness.awaitState("a bad size is explained", true) { text("preview-size-status").isNotBlank() }
        assertEquals("the viewport keeps the last good size", "1280 × 720", named("preview-size").callJs<String>("String(component.getSelectedItem())", runInEdt = true))

        named("preview-reset").click()
        toggle("mode-edit")
        Thread.sleep(800)
        assertEquals("the preview wrote nothing into the document", before, doc())
        frame.saveAll()
        assertEquals("nor into the file", fileBefore, Files.readString(hud))
        assertFalse("authoring is available again", frame.findAll<ComponentFixture>(byXpath("//div[@name='add-widget']")).isEmpty())
    }

    @Test
    fun aSampleValueIsEditedInThePreviewWithoutChangingTheDocument() {
        createAssetAndOpenEditor()
        tab("Declarations")
        tab("Bindings")
        named("add-binding").click()
        val add = frame.dialog("Binding").awaitOpen()
        add.typeNamed("form-name", "score")
        add.typeNamed("form-sample", "42")
        add.click("OK")
        add.awaitClosed()
        LifecycleHarness.awaitState("the binding is declared", true) { doc().contains("\"score\"") }
        frame.saveAll()
        val before = doc()

        toggle("mode-preview")
        tab("Preview")
        LifecycleHarness.awaitState("the sample field of the binding is shown", true) { frame.findAll<ComponentFixture>(byXpath("//div[@name='sample-score']")).isNotEmpty() }
        assertEquals("the sample shows the declared value", "42", text("sample-score"))
        enterField("sample-score", "99")
        frame.pressEnter()
        Thread.sleep(1000)
        assertEquals("a sample value is session state, not the document", before, doc())
        frame.saveAll()
        assertEquals(before, Files.readString(hud))
        assertTrue("the text shows what was typed", text("sample-score") == "99")
    }
}
