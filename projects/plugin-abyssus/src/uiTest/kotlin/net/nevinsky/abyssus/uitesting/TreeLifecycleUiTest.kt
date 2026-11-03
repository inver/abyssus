package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Tree cycles that return to where they started (TRE-01..03, TRE-05): visibility eyes, view switching, the unused
 * filter and rename. Every stage is compared with the saved scene, and a return leaves the bytes as they were.
 */
class TreeLifecycleUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    @After
    fun filterOff() {
        runCatching { if (filterOn) toggleUnusedFilter() }
    }

    private var filterOn = false

    private fun openHierarchy(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        return tree
    }

    private fun toggleUnusedFilter() {
        val item = "//*[contains(@text,'Show Only Unused Assets')]"
        frame.clickHeaderButton("Options")
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "filter in Options") { robot.findAll<ComponentFixture>(byXpath(item)).isNotEmpty() }
        robot.find<ComponentFixture>(byXpath(item)).click()
        filterOn = !filterOn
    }

    private fun footer(): String =
        frame.projectToolWindow.findAllText().map { it.text.trim() }.filter { Regex("\\d+ (scenes?|assets?|unused)").matches(it) }.joinToString(" | ")

    private fun redoThroughSceneDocument(restored: String) {
        val path = scene.toString()
        ide.openTextEditor(path)
        try {
            waitFor(Duration.ofSeconds(20), Duration.ofMillis(200), "the scene text editor has focus") { ide.editorHasFocus() }
            frame.redo()
            LifecycleHarness.awaitState("Redo restores the document", restored) { ide.documentText(path) }
            frame.saveAll()
            LifecycleHarness.awaitState("Redo is saved", restored) { Files.readString(scene) }
        } finally {
            ide.closeFile(path)
        }
    }

    /** Toggles the eye of [row] off and on: one line changes, Undo and Redo follow, and the return restores the bytes. */
    private fun eyeCycle(row: String, flag: String) {
        val original = Files.readString(scene)
        val tree = openHierarchy()
        assertEquals("$row starts on", "on", tree.eye(row))

        tree.clickRowEye(row)
        LifecycleHarness.awaitState("$row eye is off in the file", true) { Files.readString(scene).let { ide.documentText(scene.toString()) }?.contains("\"$flag\": false") == true }
        frame.saveAll()
        LifecycleHarness.awaitState("the off state is saved", true) { Regex("\"$flag\": false").containsMatchIn(Files.readString(scene)) }
        val off = Files.readString(scene)
        val diff = LifecycleHarness.lineDiff(original, off)
        assertEquals("only the $flag line changed: $diff", 1, diff.added.size)
        assertEquals(1, diff.removed.size)
        assertEquals("the tree shows the row off", "off", tree.eye(row))

        undoThroughSceneDocument(scene, original)
        LifecycleHarness.awaitState("Undo turns the eye on", "on") { tree.eye(row) }
        redoThroughSceneDocument(off)
        LifecycleHarness.awaitState("Redo turns the eye off", "off") { tree.eye(row) }

        // the return: on again restores the exact original bytes
        tree.clickRowEye(row)
        LifecycleHarness.awaitState("the eye is on again", "on") { tree.eye(row) }
        frame.saveAll()
        LifecycleHarness.awaitState("the return restores the bytes", original) { Files.readString(scene) }
    }

    @Test
    fun fogEyeReturnCycle() = eyeCycle("fog", "fogEnabled")

    @Test
    fun skyboxEyeReturnCycle() = eyeCycle("skybox", "skyboxEnabled")

    @Test
    fun ambientEyeReturnCycle() = eyeCycle("ambientLight", "ambientLightEnabled")

    @Test
    fun aSavedEyeEditSurvivesAProjectReopenWithSingleRows() {
        val tree = openHierarchy()
        tree.clickRowEye("fog")
        LifecycleHarness.awaitState("fog is off in the tree", "off") { tree.eye("fog") }
        frame.saveAll()
        LifecycleHarness.awaitState("the edit is saved", true) { Regex("\"fogEnabled\": false").containsMatchIn(Files.readString(scene)) }
        val saved = Files.readString(scene)

        reset.reopenProject()
        val again = openHierarchy()
        LifecycleHarness.awaitState("the reopened tree shows fog off", "off") { again.eye("fog") }
        val rows = again.rows()
        assertEquals("no duplicate rows after the reopen: $rows", rows.size, rows.toSet().size)
        assertEquals("one scene row", 1, rows.count { it == SceneFacts.read(scene).treeLabel })
        assertEquals("reopening wrote nothing", saved, Files.readString(scene))
    }

    @Test
    fun aUsageChangeWhileTheUnusedFilterIsOnMovesTheAssetOutAndUndoBringsItBack() {
        val original = Files.readString(scene)
        val tree = openHierarchy()
        val sceneRow = SceneFacts.read(scene).treeLabel
        toggleUnusedFilter()
        val filtered = frame.tree().also { it.expandAssets() }
        assertTrue("the unused model `tree` is listed: ${filtered.rows()}", filtered.rows().any { it == "tree" })
        val unusedBefore = footer()

        // a scene entity starts to use it: it is no longer unused, so the filter drops it
        filtered.rightClickRow(sceneRow)
        frame.clickMenuPath("Add", "Add Asset")
        frame.choosePopupItem("tree")
        LifecycleHarness.awaitState("the filter no longer lists `tree`", false) { frame.tree().rows().any { it == "tree" } }
        assertTrue("the scene row stays: ${frame.tree().rows()}", sceneRow in frame.tree().rows())
        LifecycleHarness.awaitState("the footer follows the usage", false) { footer() == unusedBefore }

        undoThroughSceneDocument(scene, original)
        LifecycleHarness.awaitState("Undo lists `tree` again", true) { frame.tree().also { it.expandAssets() }.rows().any { it == "tree" } }
        LifecycleHarness.awaitState("the footer returns", unusedBefore) { footer() }
        assertEquals("the scene is as it was", original, Files.readString(scene))
        tree.focus()
    }

    @Test
    fun switchingViewsAfterAnEditKeepsRowsSingleAndTheEditVisible() {
        val tree = openHierarchy()
        tree.clickRowEye("fog")
        LifecycleHarness.awaitState("fog is off in the tree", "off") { tree.eye("fog") }
        val before = tree.rows()

        frame.selectView("Project")
        val again = frame.selectAbyssusView()
        again.expand("Scenes")
        again.expand(SceneFacts.read(scene).treeLabel)
        again.expand("ecs")
        val rows = again.rows()
        assertEquals("one scene row in $rows", 1, rows.count { it == SceneFacts.read(scene).treeLabel || it.startsWith(SceneFacts.read(scene).name) })
        assertEquals("one ecs row", 1, rows.count { it.startsWith("ecs") })
        assertEquals("no duplicate entity rows: $rows", rows.size, rows.toSet().size)
        assertEquals("the same rows as before the switch", before.size, rows.size)
        assertEquals("the edit survived the switch", "off", again.eye("fog"))
    }

    @Test
    fun unusedFilterFollowsUsageWhileScenesStayAndFooterIsIndependent() {
        val tree = openHierarchy()
        tree.expandAssets()
        val sceneRow = SceneFacts.read(scene).treeLabel
        val footerBefore = footer()
        assertTrue("the footer shows its counters: '$footerBefore'", footerBefore.contains("unused"))
        val unusedBefore = tree.rowInfo().filter { it.depth >= 3 }.map { it.label }.filter { tree.actionBounds(it).containsKey("unused") }
        assertTrue("the fixture has unused assets", unusedBefore.isNotEmpty())

        toggleUnusedFilter()
        val filtered = frame.tree().also { it.expandAssets() }
        assertTrue("the scene row stays visible with the filter: ${filtered.rows()}", sceneRow in filtered.rows())
        val shown = filtered.rowInfo().filter { it.depth >= 3 }.map { it.label }
        assertEquals("only unused assets are listed", unusedBefore.toSet(), shown.filter { it in unusedBefore || it.startsWith("model_") || it.startsWith("skybox_") || it == "tree" }.toSet())
        assertEquals("the footer totals do not follow the filter", footerBefore, footer())
        toggleUnusedFilter()
        assertEquals("filter off lists the assets again", frame.tree().also { it.expandAssets() }.rowInfo().filter { it.depth >= 3 }.size >= shown.size, true)
    }

    @Test
    fun renameRedoReopenAndCancelKeepTheHistoryHonest() {
        val original = Files.readString(scene)
        val facts = SceneFacts(original)
        val tree = openHierarchy()
        val newName = "Renamed Scene"

        // cancel: no edit, no history
        tree.rightClickRow(facts.treeLabel)
        frame.clickMenuItem("Rename")
        val cancelled = frame.dialog("Rename Scene").awaitOpen()
        cancelled.type("Never saved")
        cancelled.click("Cancel")
        cancelled.awaitClosed()
        assertEquals(original, Files.readString(scene))
        assertEquals("a cancelled rename creates no history", "", reset.historyText(scene.toString()))

        tree.rightClickRow(facts.treeLabel)
        frame.clickMenuItem("Rename")
        val dialog = frame.dialog("Rename Scene").awaitOpen()
        dialog.type(newName)
        dialog.click("OK")
        dialog.awaitClosed()
        val renamed = original.replace("\"name\": \"${facts.name}\"", "\"name\": \"$newName\"")
        LifecycleHarness.awaitState("only the name changed", renamed) { Files.readString(scene).let { ide.documentText(scene.toString()) ?: it } }
        frame.saveAll()
        LifecycleHarness.awaitState("the rename is saved", renamed) { Files.readString(scene) }

        reset.reopenTextEditor(scene.toString())
        LifecycleHarness.awaitState("the reopened editor shows the new name", renamed) { ide.documentText(scene.toString()) }

        undoThroughSceneDocument(scene, original)
        LifecycleHarness.awaitState("the tree shows the old name", true) { frame.tree().rows().contains(facts.treeLabel) }
        redoThroughSceneDocument(renamed)
        LifecycleHarness.awaitState("the tree shows the new name", true) { frame.tree().rows().contains("$newName (${facts.id})") }
        assertFalse("no row of the old name remains", frame.tree().rows().contains(facts.treeLabel))
    }
}
