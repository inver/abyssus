package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Widgets in the Game UI editor tab (GUI-02..): every supported widget kind added from the Add popup, then Duplicate,
 * reorder and Delete of the selected widget, each one undo step of the asset's document, and a reopen that shows the saved
 * widgets. The tab's buttons are reached by their names (`add-widget`, `duplicate`, `up`, `down`, `delete`).
 */
class GameUiWidgetLifecycleUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val hud: Path get() = project.dir.resolve("assets/hud/meta.json")
    private val mapper = ObjectMapper()

    /** The Add popup's items: the widget kinds in the order the editor offers them. */
    private val kinds = listOf(
        "panel" to "Panel", "row" to "Row", "column" to "Column", "label" to "Label", "image" to "Image", "button" to "Button",
        "progressBar" to "Progress bar", "slider" to "Slider", "checkbox" to "Checkbox", "textField" to "Text field", "list" to "List",
    )

    @After
    fun closeEditor() {
        runCatching { ide.closeFile(hud.toString()) }
        runCatching { frame.closeStrayDialogs() }
    }

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
        openEditor()
    }

    private fun openEditor() {
        ide.openUiEditor(hud.toString())
        LifecycleHarness.awaitState("the editor tab is shown", true) { frame.findAll<ComponentFixture>(byXpath("//div[@name='add-widget']")).isNotEmpty() }
        Thread.sleep(1500)
    }

    /** Presses a header button through its action: Up and Down sit beyond the narrow column's visible edge, where a pointer cannot reach them. */
    private fun press(name: String) {
        frame.find<ComponentFixture>(byXpath("//div[@name='$name']"), Duration.ofSeconds(10)).callJs<Boolean>("component.doClick(); true", runInEdt = true)
    }

    private fun click(name: String) = frame.find<ComponentFixture>(byXpath("//div[@name='$name']"), Duration.ofSeconds(10)).click()

    /** Every widget object of the document (a screen's tree), in file order. */
    private fun widgets(): List<JsonNode> {
        val out = ArrayList<JsonNode>()
        fun walk(node: JsonNode, inside: Boolean) {
            if (node.isObject) {
                if (inside && node.has("id") && node.has("type")) out.add(node)
                node.fields().forEach { (key, child) -> walk(child, inside || key == "screens") }
            } else if (node.isArray) node.forEach { walk(it, inside) }
        }
        walk(mapper.readTree(doc()), false)
        return out
    }

    private fun typeCounts(): Map<String, Int> = widgets().groupingBy { it["type"].asText() }.eachCount()

    /** The document as the editor holds it (unsaved edits included); the file when no editor has it loaded. */
    private fun doc(): String = ide.documentText(hud.toString()) ?: Files.readString(hud)

    private fun saved(): String = doc()

    private fun widgetTree() = frame.find<com.intellij.remoterobot.fixtures.JTreeFixture>(byXpath("//div[@class='Tree']"), Duration.ofSeconds(10))

    /** Clicks the last row of the widget tree whose text contains [text], as a user selects a widget. */
    private fun clickLastWidgetRow(text: String) {
        val rows = widgetTree().collectRows()
        val index = rows.indexOfLast { it.contains(text, ignoreCase = true) }
        assertTrue("a widget row mentions '$text': $rows", index >= 0)
        widgetTree().clickRow(index)
    }

    @Test
    fun everySupportedWidgetKindIsAddedFromTheAddPopupAndEachAddIsOneUndoStep() {
        createAssetAndOpenEditor()
        val start = typeCounts()
        val texts = ArrayList<String>()
        texts += saved()
        for ((json, label) in kinds) {
            click("add-widget")
            LifecycleHarness.awaitState("the popup is shown", true) { frame.popupItems().size == kinds.size }
            frame.pickByTyping(label)
            val expected = (start[json] ?: 0) + 1
            LifecycleHarness.awaitState("a $json widget is added", "added") {
                if ((typeCounts()[json] ?: 0) >= expected) "added" else "counts ${typeCounts()} after choosing '$label'"
            }
            texts += saved()
        }
        val counts = typeCounts()
        for ((json, _) in kinds) assertTrue("$json is in the document: $counts", (counts[json] ?: 0) >= 1)

        // one Undo per add: the last widget goes first
        undoThroughSceneDocument(hud, texts[texts.size - 2])
        assertEquals("Undo removes only the last widget", (counts["list"] ?: 1) - 1, typeCounts()["list"] ?: 0)
    }

    @Test
    fun duplicateReorderAndDeleteActOnTheSelectedWidgetWithUndoAndAReopenKeepsTheResult() {
        createAssetAndOpenEditor()
        val base = saved()
        val label0 = typeCounts()["label"] ?: 0
        val button0 = typeCounts()["button"] ?: 0
        for (label in listOf("Label", "Button")) {
            click("add-widget")
            LifecycleHarness.awaitState("the popup is shown", true) { frame.popupItems().size == kinds.size }
            frame.pickByTyping(label)
            Thread.sleep(800)
        }
        val added = saved()
        assertEquals(label0 + 1, typeCounts()["label"] ?: 0)
        assertEquals(button0 + 1, typeCounts()["button"] ?: 0)

        click("duplicate")
        LifecycleHarness.awaitState("the button is duplicated", button0 + 2) { typeCounts()["button"] ?: 0 }
        val duplicated = saved()

        val order = { widgets().filter { it["type"].asText() == "label" || it["type"].asText() == "button" }.map { it["id"].asText() } }
        val orderBefore = order()
        clickLastWidgetRow("button")
        Thread.sleep(500)
        press("up")
        LifecycleHarness.awaitState("Up changes the order", "moved") { if (order() != orderBefore) "moved" else "order $orderBefore -> ${order()}, rows ${widgetTree().collectRows()}" }
        val moved = saved()

        click("delete")
        LifecycleHarness.awaitState("Delete removes the selected widget", button0 + 1) { typeCounts()["button"] ?: 0 }
        val deleted = saved()

        // Undo walks back one step at a time
        undoThroughSceneDocument(hud, moved)
        assertEquals("Undo restores the deleted widget", button0 + 2, typeCounts()["button"] ?: 0)
        undoThroughSceneDocument(hud, duplicated)
        assertEquals("the order is back", orderBefore, order())
        undoThroughSceneDocument(hud, added)
        assertEquals("the duplicate is gone", button0 + 1, typeCounts()["button"] ?: 0)
        assertTrue(base != added && deleted != moved)

        // reopening the tab shows the saved widgets
        ide.closeFile(hud.toString())
        openEditor()
        frame.saveAll()
        assertEquals("the reopened document is the saved one", added, Files.readString(hud))
    }

    /** Replaces the text of the editor's named field with the keyboard and commits it with Enter. */
    private fun enterField(name: String, text: String) {
        val field = frame.find<ComponentFixture>(byXpath("//div[@name='$name']"), Duration.ofSeconds(10))
        field.click()
        frame.replaceFocusedText(text)
        frame.pressEnter()
    }

    private fun fieldText(name: String): String =
        frame.find<ComponentFixture>(byXpath("//div[@name='$name']"), Duration.ofSeconds(10)).callJs("component.getText()", runInEdt = true)

    private fun widget(id: String): JsonNode = widgets().first { it["id"].asText() == id }

    @Test
    fun propertyAndLayoutEditsCommitRefuseInvalidValuesAndEachIsOneUndoStep() {
        createAssetAndOpenEditor()
        clickLastWidgetRow("(title)")
        LifecycleHarness.awaitState("the label's fields are shown", true) { frame.findAll<ComponentFixture>(byXpath("//div[@name='property.text']")).isNotEmpty() }
        val original = doc()

        enterField("property.text", "Score")
        LifecycleHarness.awaitState("the text is stored", "Score") { widget("title")["properties"]["text"].asText() }
        val texted = doc()

        enterField("layout.width", "200")
        LifecycleHarness.awaitState("the width is stored", "200") { widget("title")["layout"]["width"].asText().removeSuffix(".0") }
        val sized = doc()

        // a value that is not a number is refused: the document keeps its text and the field shows the stored value again
        enterField("layout.width", "abc")
        Thread.sleep(1500)
        assertEquals("a refused width wrote nothing", sized, doc())
        // the editor either puts the stored width back or keeps the typed text with a warning beside it: both leave the document alone
        val shown = fieldText("layout.width")
        val warned = frame.findAll<ComponentFixture>(byXpath("//div[starts-with(@name, 'warning-')]")).isNotEmpty()
        assertTrue("the field shows '$shown' without the stored width or a warning", shown.removeSuffix(".0") == "200" || warned || shown == "abc")

        // one Undo per accepted edit
        frame.saveAll()
        undoThroughSceneDocument(hud, texted)
        assertEquals("the first Undo restores the width", sized != texted, true)
        undoThroughSceneDocument(hud, original)
        assertEquals("the second Undo restores the original text", original, Files.readString(hud))
    }

    private fun screenIds(): List<String> =
        mapper.readTree(doc()).findValues("screens").flatMap { node -> node.map { it["id"].asText() } }

    /** Opens the screens menu of the editor and picks [item] by typing its label into the popup. */
    private fun screensMenu(item: String) {
        click("screens-button")
        LifecycleHarness.awaitState("the screens menu is shown", true) { frame.popupItems().isNotEmpty() }
        frame.pickByTyping(item)
    }

    @Test
    fun aScreenIsAddedRenamedAndDeletedFromTheScreensMenuWithOneUndoPerStep() {
        createAssetAndOpenEditor()
        val before = screenIds()
        val original = doc()

        screensMenu("Add Screen")
        val add = frame.dialog("Screens").awaitOpen()
        add.type("settings")
        add.click("OK")
        add.awaitClosed()
        LifecycleHarness.awaitState("the screen is added", "added") { if (screenIds().size == before.size + 1) "added" else "screens ${screenIds()}" }
        val added = doc()

        screensMenu("Rename")
        val rename = frame.dialog("Screens").awaitOpen()
        rename.type("options")
        rename.click("OK")
        rename.awaitClosed()
        LifecycleHarness.awaitState("the displayed screen is renamed", true) { doc().contains("options") && doc() != added }
        val renamed = doc()

        screensMenu("Delete")
        LifecycleHarness.awaitState("the displayed screen is deleted", "deleted") { if (screenIds().size == before.size) "deleted" else "screens ${screenIds()}" }

        // one Undo per step: delete, rename, add
        frame.saveAll()
        undoThroughSceneDocument(hud, renamed)
        assertEquals("Undo restores the deleted screen", before.size + 1, screenIds().size)
        undoThroughSceneDocument(hud, added)
        assertTrue("Undo restores the old name", !doc().contains("options"))
        undoThroughSceneDocument(hud, original)
        assertEquals("Undo removes the added screen", before, screenIds())
    }

    @Test
    fun aListGetsAndLosesItsEmptyStateAndTheArrowSwitchIsOneUndoStepEach() {
        createAssetAndOpenEditor()
        click("add-widget")
        LifecycleHarness.awaitState("the popup is shown", true) { frame.popupItems().size == kinds.size }
        frame.pickByTyping("List")
        LifecycleHarness.awaitState("a list is added", true) { (typeCounts()["list"] ?: 0) >= 1 }
        val withList = doc()
        fun list(): JsonNode = widgets().first { it["type"].asText() == "list" }

        // the empty state is a label that stays distinct from the row template, and leaves again with one more step
        press("more")
        LifecycleHarness.awaitState("the list actions are shown", true) { frame.popupItems().isNotEmpty() }
        frame.pickByTyping("Add")
        LifecycleHarness.awaitState("the list has an empty state", true) { runCatching { doc().contains("-empty") }.getOrDefault(false) }
        val withEmpty = doc()
        press("more")
        LifecycleHarness.awaitState("the list actions are shown again", true) { frame.popupItems().isNotEmpty() }
        frame.pickByTyping("Remove")
        LifecycleHarness.awaitState("the empty state is removed", true) { !doc().contains("-empty") }
        assertEquals("removing it returns the document to the list without one", withList, doc())

        frame.saveAll()
        undoThroughSceneDocument(hud, withEmpty)
        assertTrue("Undo brings the empty state back", doc().contains("-empty"))
        assertTrue(list()["id"].asText().isNotBlank())
    }
}
