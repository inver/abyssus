package net.nevinsky.abyssus.uitesting

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Water surfaces (WAT-01..04): created from the tree's Add menu, edited in Abyssus Properties, removed from their row.
 * Surfaces live under `abyssus.waterSurfaces` of the scene, never as entities.
 */
class WaterLifecycleUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun openScene(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        return tree
    }

    /** Like the base helper, but a miss names the text the document held instead of only timing out. */
    private fun undoOrExplain(restored: String) {
        val path = scene.toString()
        ide.openTextEditor(path)
        try {
            com.intellij.remoterobot.utils.waitFor(Duration.ofSeconds(20), Duration.ofMillis(200), "the scene text editor has focus") { ide.editorHasFocus() }
            frame.undo()
            LifecycleHarness.awaitState("Undo restores the document", restored) { ide.documentText(path) }
            frame.saveAll()
            LifecycleHarness.awaitState("Undo is saved", restored) { Files.readString(scene) }
        } finally {
            ide.closeFile(path)
        }
    }

    private fun redoThroughSceneDocument(restored: String) {
        val path = scene.toString()
        ide.openTextEditor(path)
        try {
            com.intellij.remoterobot.utils.waitFor(Duration.ofSeconds(20), Duration.ofMillis(200), "the scene text editor has focus") { ide.editorHasFocus() }
            frame.redo()
            LifecycleHarness.awaitState("Redo restores the document", restored) { ide.documentText(path) }
            frame.saveAll()
            LifecycleHarness.awaitState("Redo is saved", restored) { Files.readString(scene) }
        } finally {
            ide.closeFile(path)
        }
    }

    private fun surfaces(text: String) = SceneJson().parseObject(text).get("abyssus")?.get("waterSurfaces")
    private fun surfaceIds(text: String): List<String> = surfaces(text)?.fieldNames()?.asSequence()?.toList().orEmpty()
    private fun hasRow(tree: AbyssusTree, name: String) = tree.rows().any { it == name || it.startsWith("$name ") }

    private fun addWater(tree: AbyssusTree, preset: String) {
        var last: Throwable? = null
        repeat(3) {
            try {
                tree.rightClickRow(SceneFacts.read(scene).treeLabel)
                frame.clickMenuPath("Add", "Add Water")
                frame.choosePopupItem(preset)
                return
            } catch (e: Throwable) {
                last = e
                runCatching { frame.dismissPopup() }
                Thread.sleep(700)
            }
        }
        throw last!!
    }

    /** Selects the surface row [name] again and waits for its water editors, which an Undo or Redo through the text editor may have dropped. */
    private fun reselect(tree: AbyssusTree, name: String) {
        LifecycleHarness.awaitState("the row '$name' is in the tree", true) { hasRow(tree, name) }
        tree.clickRow(name)
        LifecycleHarness.awaitState("the water editors of '$name' are shown", true) { frame.properties().exists("water-name") }
    }

    private fun pointerOf(text: String, id: String, vararg path: String): String? {
        var node = surfaces(text)?.get(id)
        for (p in path) node = node?.get(p)
        return node?.asText()
    }

    @Test
    fun lakeAndSeaAreCreatedEditedAndRemovedWithoutTouchingOtherData() {
        val original = Files.readString(scene)
        assertTrue("the fixture has no water", surfaceIds(original).isEmpty())
        val tree = openScene()
        frame.openProperties()

        // WAT-01: each preset gets its own id and defaults
        addWater(tree, "Lake")
        LifecycleHarness.awaitState("the Lake is saved", 1) { surfaceIds(Files.readString(scene)).size.also { if (it == 0) frame.saveAll() } }
        addWater(tree, "Sea")
        LifecycleHarness.awaitState("the Sea is saved", 2) { surfaceIds(ide.documentText(scene.toString())!!).size }
        val afterBoth = ide.documentText(scene.toString())!!
        val (lake, sea) = surfaceIds(afterBoth).let { ids -> ids.first { pointerOf(afterBoth, it, "preset") == "LAKE" } to ids.first { pointerOf(afterBoth, it, "preset") == "SEA" } }
        assertTrue("distinct identities", lake != sea)
        assertTrue("the entities and the rest of the scene are untouched", LifecycleHarness.sameExcept(original, afterBoth, "/abyssus"))
        LifecycleHarness.awaitState("the Lake row is in the tree", true) { hasRow(tree, pointerOf(afterBoth, lake, "name")!!) }
        LifecycleHarness.awaitState("the Sea row is in the tree", true) { hasRow(tree, pointerOf(afterBoth, sea, "name")!!) }

        // WAT-02: rename, resize and hide only the Lake
        tree.clickRow(pointerOf(afterBoth, lake, "name")!!)
        val panel = frame.properties()
        LifecycleHarness.awaitState("Properties show the lake", pointerOf(afterBoth, lake, "name")) { panel.headerTitle() }
        panel.enter("water-name", "Pond")
        LifecycleHarness.awaitState("renamed", "Pond") { pointerOf(ide.documentText(scene.toString())!!, lake, "name") }
        val renamed = ide.documentText(scene.toString())!!
        assertTrue("only the lake's name changed", LifecycleHarness.sameExcept(afterBoth, renamed, "/abyssus/waterSurfaces/$lake/name"))
        // Undo and Redo restore each accepted edit (Properties edits undo through the scene document)
        undoOrExplain(afterBoth)
        redoThroughSceneDocument(renamed)
        reselect(tree, "Pond")
        panel.enter("water-field-WIDTH", "42")
        LifecycleHarness.awaitState("resized", "42") { pointerOf(ide.documentText(scene.toString())!!, lake, "width")?.removeSuffix(".0") }
        val resized = ide.documentText(scene.toString())!!
        assertTrue("only the lake's width changed", LifecycleHarness.sameExcept(renamed, resized, "/abyssus/waterSurfaces/$lake/width"))
        undoOrExplain(renamed)
        redoThroughSceneDocument(resized)
        reselect(tree, "Pond")
        panel.clickCheck("water-enabled")
        LifecycleHarness.awaitState("hidden", "false") { pointerOf(ide.documentText(scene.toString())!!, lake, "enabled") }
        val hidden = ide.documentText(scene.toString())!!
        assertTrue("only the lake's visibility changed", LifecycleHarness.sameExcept(resized, hidden, "/abyssus/waterSurfaces/$lake/enabled"))
        undoOrExplain(resized)
        redoThroughSceneDocument(hidden)
        reselect(tree, "Pond")
        panel.enter("water-field-X", "5")
        LifecycleHarness.awaitState("moved", "5") { pointerOf(ide.documentText(scene.toString())!!, lake, "position", "x")?.removeSuffix(".0") }
        val moved = ide.documentText(scene.toString())!!
        assertTrue("only the lake's x changed", LifecycleHarness.sameExcept(hidden, moved, "/abyssus/waterSurfaces/$lake/position/x"))
        undoOrExplain(hidden)
        redoThroughSceneDocument(moved)
        assertEquals("the sea is as it was", pointerOf(afterBoth, sea, "name"), pointerOf(hidden, sea, "name"))

        // removal: only the Pond goes, and its Properties and selection clear
        tree.rightClickRow("Pond")
        assertFalse("a water row never offers entity deletion", frame.findAll<com.intellij.remoterobot.fixtures.ComponentFixture>(com.intellij.remoterobot.search.locators.byXpath("//div[@class='ActionMenuItem' and @text='Remove Entity']")).any { it.callJs<Boolean>("component.isShowing()", runInEdt = true) })
        frame.clickMenuItem("Remove Water")
        LifecycleHarness.awaitState("the pond is gone from the file", false) { surfaceIds(ide.documentText(scene.toString())!!).contains(lake) }
        val removed = ide.documentText(scene.toString())!!
        assertEquals("only the sea remains", listOf(sea), surfaceIds(removed))
        LifecycleHarness.awaitState("the pond row is gone", false) { hasRow(tree, "Pond") }
        assertFalse("Properties no longer edit the pond", panel.headerTitle() == "Pond")
        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo of the removal restores the edited state", moved) { ide.documentText(scene.toString()) }
        assertEquals("the water group never offers entity deletion", emptySet<String>(), project.differences() - setOf(scenePath))
    }

    @Test
    fun anInvalidExtentIsRefusedInlineAndTheSceneKeepsItsValue() {
        val tree = openScene()
        frame.openProperties()
        addWater(tree, "Lake")
        LifecycleHarness.awaitState("the lake is saved", 1) { surfaceIds(ide.documentText(scene.toString())!!).size }
        val created = ide.documentText(scene.toString())!!
        val id = surfaceIds(created).single()
        tree.clickRow(pointerOf(created, id, "name")!!)
        val panel = frame.properties()
        LifecycleHarness.awaitState("Properties show the lake", pointerOf(created, id, "name")) { panel.headerTitle() }
        panel.enter("water-field-WIDTH", "-5")
        LifecycleHarness.awaitState("the refusal is shown", true) { panel.labelText("water-error-WIDTH").isNotBlank() }
        assertEquals("the document keeps its value", created, ide.documentText(scene.toString()))
        panel.enter("water-field-WIDTH", "abc")
        LifecycleHarness.awaitState("a non-number is refused too", true) { panel.labelText("water-error-WIDTH").isNotBlank() }
        assertEquals("still unchanged", created, ide.documentText(scene.toString()))
        panel.enter("water-field-WIDTH", "30")
        LifecycleHarness.awaitState("a valid value recovers and commits", "30") { pointerOf(ide.documentText(scene.toString())!!, id, "width")?.removeSuffix(".0") }
        assertEquals("the error is gone", "", panel.labelText("water-error-WIDTH"))
    }

    @Test
    fun anIncompatibleWaterExtensionBlocksCreationAndIsNeverReplaced() {
        val text = Files.readString(scene)
        val root = SceneJson().parseObject(text)
        root.put("abyssus", "not an object")
        Files.writeString(scene, SceneJson().pretty(root))
        ide.refresh(scene.toString())
        val tree = openScene()
        tree.rightClickRow(SceneFacts.read(scene).treeLabel)
        frame.find<com.intellij.remoterobot.fixtures.ComponentFixture>(com.intellij.remoterobot.search.locators.byXpath("//div[@class='ActionMenu' and @text='Add']"), Duration.ofSeconds(10)).click()
        val item = frame.findAll<com.intellij.remoterobot.fixtures.ComponentFixture>(com.intellij.remoterobot.search.locators.byXpath("//div[@class='ActionMenuItem' and @text='Add Water']"))
        val offered = item.any { it.callJs<Boolean>("component.isShowing() && component.isEnabled()", runInEdt = true) }
        frame.dismissPopup()
        assertFalse("Add Water is not offered on an incompatible extension", offered)
        assertEquals("the incompatible value is not replaced", "not an object", SceneJson().parseObject(Files.readString(scene)).get("abyssus").asText())
    }

    @Test
    fun eachCreationIsItsOwnUndoStep() {
        val original = Files.readString(scene)
        val tree = openScene()
        addWater(tree, "Lake")
        LifecycleHarness.awaitState("the lake is in the document", 1) { surfaceIds(ide.documentText(scene.toString())!!).size }
        val oneLake = ide.documentText(scene.toString())!!
        addWater(tree, "Sea")
        LifecycleHarness.awaitState("the sea is in the document", 2) { surfaceIds(ide.documentText(scene.toString())!!).size }
        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("one Undo from the tree removes only the sea", oneLake) { ide.documentText(scene.toString()) }
        frame.undo()
        LifecycleHarness.awaitState("a second Undo removes the lake", true) { surfaceIds(ide.documentText(scene.toString())!!).isEmpty() }
        assertTrue("and the scene is as before", LifecycleHarness.sameExcept(original, ide.documentText(scene.toString())!!, "/abyssus"))
    }

    @Test
    fun aSurfaceWithInvalidSavedDataShowsItsStateAndStillAllowsRenameAndRemoval() {
        val root = SceneJson().parseObject(Files.readString(scene))
        val bad = root.putObject("abyssus").putObject("waterSurfaces").putObject("broken-1")
        bad.put("name", "Broken").put("preset", "LAKE").put("width", -1).put("length", 100).put("enabled", true)
        Files.writeString(scene, SceneJson().pretty(root))
        ide.refresh(scene.toString())
        val tree = openScene()
        frame.openProperties()
        tree.expand("Water")
        fun brokenRow() = tree.rows().firstOrNull { it.contains("Broken") || it.contains("broken-1") }
        LifecycleHarness.awaitState("the broken surface has a row (rows: ${tree.rows()})", true) { brokenRow() != null }
        val rowText = brokenRow()!!
        tree.clickRow(rowText)
        val panel = frame.properties()
        LifecycleHarness.awaitState("Properties show it", true) { panel.exists("water-name") }
        assertTrue("the invalid state is visible", panel.exists("water-invalid-note"))
        val before = ide.documentText(scene.toString())!!
        panel.enter("water-name", "Fixed name")
        LifecycleHarness.awaitState("rename stays usable", "Fixed name") { pointerOf(ide.documentText(scene.toString())!!, "broken-1", "name") }
        assertTrue("only the name changed", LifecycleHarness.sameExcept(before, ide.documentText(scene.toString())!!, "/abyssus/waterSurfaces/broken-1/name"))
        val renamedRow = tree.rows().first { it.contains("Fixed name") }
        tree.rightClickRow(renamedRow)
        frame.clickMenuItem("Remove Water")
        LifecycleHarness.awaitState("removal stays usable", false) { surfaceIds(ide.documentText(scene.toString())!!).contains("broken-1") }
    }
}
