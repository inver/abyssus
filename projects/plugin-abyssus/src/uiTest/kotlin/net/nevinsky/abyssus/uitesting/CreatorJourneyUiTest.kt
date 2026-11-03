package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Continuous creator journeys (JRN-01..): each runs from the pristine project through several stages without resetting
 * the fixture in between, and compares the tree, the document and the saved file at every checkpoint.
 */
class CreatorJourneyUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val mapper = ObjectMapper()

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
        runCatching { ide.closeFile(scene.toString()) }
    }

    private fun entity(id: Int): JsonNode? = mapper.readTree(Files.readString(scene))["ecs"]?.get(id.toString())
    private fun saved() = Files.readString(scene)

    /** Types into [field] of section [kind], opening the section first when Properties drew it collapsed. */
    private fun enterField(key: String, field: String, text: String, kind: String = key) {
        val panel = frame.properties()
        repeat(3) { if (runCatching { panel.isCollapsed(key) }.getOrDefault(false)) { panel.clickChevron(key); Thread.sleep(800) } }
        panel.enter("field-$kind-$field", text)
    }

    private fun redoThroughSceneDocument(restored: String) {
        ide.openTextEditor(scene.toString())
        com.intellij.remoterobot.utils.waitFor(Duration.ofSeconds(20), Duration.ofMillis(200), "the scene text editor has focus") { ide.editorHasFocus() }
        frame.redo()
        LifecycleHarness.awaitState("Redo restores the document", restored) { ide.documentText(scene.toString()) }
        frame.saveAll()
        LifecycleHarness.awaitState("Redo is saved", restored) { saved() }
        ide.closeFile(scene.toString())
    }

    @Test
    fun jrn01ModelInstanceFromPlacementToRemoval() {
        val original = saved()
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        val sceneRow = SceneFacts.read(scene).treeLabel
        tree.expand(sceneRow)
        tree.expand("ecs")
        frame.openProperties()

        tree.rightClickRow(sceneRow)
        frame.clickMenuPath("Add", "Add Asset")
        frame.choosePopupItem("tree")
        LifecycleHarness.awaitState("the instance is saved as entity 11", "tree") { entity(11)?.get("components")?.get("RenderComponent")?.findValue("assetName")?.asText() }
        val created = saved()
        assertTrue("only entity 11 was added", LifecycleHarness.sameExcept(original, created, "/ecs/11"))

        tree.clickRow("Model 11")
        LifecycleHarness.awaitState("Properties show the instance", "Model 11") { frame.properties().headerTitle() }
        enterField("NameComponent", "name", "Oak")
        LifecycleHarness.awaitState("the rename is saved", "Oak") { entity(11)?.get("components")?.get("NameComponent")?.get("name")?.asText() }
        val renamed = saved()
        enterField("PositionComponent", "localPosition.x", "7.5")
        LifecycleHarness.awaitState("the move is saved", 7.5) { entity(11)?.get("components")?.get("PositionComponent")?.get("localPosition")?.get("x")?.asDouble() }
        val moved = saved()
        assertTrue("rename and move touch only entity 11", LifecycleHarness.sameExcept(original, moved, "/ecs/11"))

        undoThroughSceneDocument(scene, renamed)
        undoThroughSceneDocument(scene, created)
        redoThroughSceneDocument(renamed)
        redoThroughSceneDocument(moved)

        reset.reopenTextEditor(scene.toString())
        LifecycleHarness.awaitState("the reopened editor shows the edited instance", moved) { ide.documentText(scene.toString()) }
        ide.closeFile(scene.toString())

        tree.rightClickRow("Oak")
        frame.clickMenuItem("Remove Entity")
        LifecycleHarness.awaitState("the instance is removed", null) { entity(11)?.toString() }
        assertTrue("the scene is as before", LifecycleHarness.sameExcept(original, saved(), "/ecs/11"))

        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo of the removal restores the edited instance, not its defaults", moved) { ide.documentText(scene.toString()) }
        frame.saveAll()
        LifecycleHarness.awaitState("it is saved", moved) { saved() }
        frame.redo()
        LifecycleHarness.awaitState("Redo removes it again", original) { ide.documentText(scene.toString())?.takeIf { LifecycleHarness.sameExcept(original, it, "/ecs/11") }?.let { original } }
        frame.saveAll()
        reset.reopenTextEditor(scene.toString())
        assertFalse("the final reopen confirms absence", ide.documentText(scene.toString())!!.contains("\"Oak\""))
        assertEquals("the tree asset and unrelated content are unchanged", emptySet<String>(), project.differences() - setOf(scenePath))
    }

    @Test
    fun jrn09ClearADependencyBeforeRemovingItsTarget() {
        val original = saved()
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        frame.openProperties()

        tree.addComponentOnEcs("Camera")
        LifecycleHarness.awaitState("entity 11 is saved", true) { entity(11) != null }
        tree.addComponentOnEcs("Camera")
        LifecycleHarness.awaitState("entity 12 is saved", true) { entity(12) != null }
        tree.clickRow("Entity 12")
        LifecycleHarness.awaitState("Properties show entity 12", "Entity 12") { frame.properties().headerTitle() }
        tree.rightClickRow("Entity 12")
        frame.clickMenuItem("Add Component...")
        frame.choosePopupItem("Parent")
        LifecycleHarness.awaitState("Parent is added", true) { entity(12)?.get("components")?.has("ParentComponent") == true }
        LifecycleHarness.awaitState("its section is shown", true) { frame.properties().hasRemove("ParentComponent") }
        enterField("ParentComponent", "parentEntityId", "11")
        LifecycleHarness.awaitState("the parent link is saved", 11) { entity(12)?.get("components")?.get("ParentComponent")?.get("parentEntityId")?.asInt() }
        val linked = saved()

        reset.reopenTextEditor(scene.toString())
        LifecycleHarness.awaitState("the reopened editor shows the link", linked) { ide.documentText(scene.toString()) }
        ide.closeFile(scene.toString())

        // the referenced parent cannot be removed: nothing is written
        tree.rightClickRow("Entity 11")
        frame.clickMenuItem("Remove Entity")
        LifecycleHarness.awaitState("the refusal names the child", true) {
            runCatching { frame.dialog("Component Not Changed").texts().joinToString(" ").contains("12") }.getOrDefault(false)
        }
        frame.closeStrayDialogs()
        assertEquals("the blocked attempt writes nothing", linked, saved())

        // clear the reference, then the target can go
        tree.clickRow("Entity 12")
        LifecycleHarness.awaitState("Properties show entity 12 again", "Entity 12") { frame.properties().headerTitle() }
        LifecycleHarness.awaitState("the Parent section has a Remove button", true) { frame.properties().hasRemove("ParentComponent") }
        frame.properties().clickRemove("ParentComponent")
        LifecycleHarness.awaitState("the link is cleared", true) { entity(12)?.get("components")?.has("ParentComponent") == false }
        val cleared = saved()
        tree.rightClickRow("Entity 11")
        frame.clickMenuItem("Remove Entity")
        LifecycleHarness.awaitState("entity 11 is removed", null) { entity(11)?.toString() }
        assertTrue("the survivor is as it was after clearing", LifecycleHarness.sameExcept(cleared, saved(), "/ecs/11"))
        assertFalse("no reference to 11 is left", saved().contains("\"parentEntityId\": 11"))

        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo of the removal brings the target back, the link stays clear", cleared) { ide.documentText(scene.toString()) }
        frame.saveAll()
        assertEquals(cleared, saved())
        frame.redo()
        LifecycleHarness.awaitState("Redo removes it again", null) { entity(11)?.toString().also { frame.saveAll() } }
        assertTrue("the first entity left no trace", LifecycleHarness.sameExcept(original, saved(), "/ecs/12"))
    }

    @Test
    fun jrn02EditedComponentSurvivesRemovalUndo() {
        val original = saved()
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        frame.openProperties()
        tree.clickRow("Model 0")
        LifecycleHarness.awaitState("Properties show Model 0", "Model 0") { frame.properties().headerTitle() }
        fun light() = entity(0)?.get("components")?.get("LightComponent")

        tree.rightClickRow("Model 0")
        frame.clickMenuItem("Add Component...")
        frame.choosePopupItem("Light")
        LifecycleHarness.awaitState("the Light component is added", true) { light() != null }
        LifecycleHarness.awaitState("its section is shown", true) { frame.properties().hasRemove("LightComponent") }
        val added = saved()
        enterField("0.LightComponent", "intensity", "2.5", kind = "LightComponent")
        LifecycleHarness.awaitState("intensity is saved", 2.5) { light()?.findValue("intensity")?.asDouble() }
        val first = saved()
        enterField("0.LightComponent", "range", "12", kind = "LightComponent")
        LifecycleHarness.awaitState("range is saved", 12.0) { light()?.findValue("range")?.asDouble() }
        val second = saved()

        // an invalid value is explained and writes nothing
        frame.properties().enter("field-LightComponent-range", "abc")
        LifecycleHarness.awaitState("'abc' is explained", true) { runCatching { frame.properties().errorText("LightComponent", "range") }.getOrDefault("").isNotBlank() }
        assertEquals("the refused value writes nothing", second, saved())

        undoThroughSceneDocument(scene, first)
        undoThroughSceneDocument(scene, added)
        redoThroughSceneDocument(first)
        redoThroughSceneDocument(second)
        reset.reopenTextEditor(scene.toString())
        LifecycleHarness.awaitState("the reopened editor shows both values", second) { ide.documentText(scene.toString()) }
        ide.closeFile(scene.toString())

        frame.properties().clickRemove("LightComponent")
        LifecycleHarness.awaitState("the component is removed", true) { light() == null }
        val removed = saved()
        assertTrue("only the component went", LifecycleHarness.sameExcept(original, removed, "/ecs/0/components/LightComponent") || removed == original)
        undoThroughSceneDocument(scene, second)
        assertEquals("Undo of the removal brings back both edited values, not defaults", 12.0, light()?.findValue("range")?.asDouble() ?: -1.0, 0.0)
        assertEquals(2.5, light()?.findValue("intensity")?.asDouble() ?: -1.0, 0.0)
        redoThroughSceneDocument(removed)
        assertEquals("Redo removes only that component", removed, saved())
        assertTrue("the component is gone from Model 0", light() == null)
    }
}
