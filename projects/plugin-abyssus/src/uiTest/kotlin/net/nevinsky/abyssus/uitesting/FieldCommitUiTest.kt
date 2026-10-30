package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * How a Properties text field commits (FLD-01..05), on the component fields of `Model 0`: Enter, leaving the field,
 * no second commit, rejection and recovery, and submissions that change nothing. Undo history is read from the IDE,
 * and a scenario starts with none, so "no history" proves that nothing was written.
 */
class FieldCommitUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val name = "field-NameComponent-name"
    private val posX = "field-PositionComponent-localPosition.x"

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun openModel(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        frame.openProperties()
        tree.clickRow("Model 0")
        LifecycleHarness.awaitState("Properties show Model 0", "Model 0") { frame.properties().headerTitle() }
        return tree
    }

    private fun entity0() = ObjectMapper().readTree(Files.readString(scene))["ecs"]["0"]["components"]

    private fun storedName() = entity0()["NameComponent"]["name"].asText()

    private fun history() = reset.historyText(scene.toString())

    @Test
    fun enterCommitsOnceAndOneUndoRestoresThePriorText() {
        val original = Files.readString(scene)
        openModel()
        frame.properties().enter(name, "Renamed")
        LifecycleHarness.awaitState("Enter saves the name", "Renamed") { storedName() }
        assertTrue("only entity 0 changed", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/0"))

        // focus leaves the field after Enter: no second edit
        frame.properties().focus(posX)
        Thread.sleep(1000)
        assertEquals("the name is still what Enter saved", "Renamed", storedName())

        undoThroughSceneDocument(scene, original)
        assertEquals("one Undo restored the prior text", original, Files.readString(scene))
        assertEquals("nothing is left to undo: Enter and the focus loss were one edit", "", history())
    }

    @Test
    fun leavingTheFieldCommitsWithoutEnter() {
        openModel()
        frame.properties().type(name, "Moved")
        frame.properties().focus(posX)
        LifecycleHarness.awaitState("losing focus saves the name", "Moved") { storedName() }
        LifecycleHarness.awaitState("the field shows what the file holds", "Moved") { frame.properties().fieldText(name) }
    }

    @Test
    fun rejectionShowsTheReasonAndAValidValueRecovers() {
        val original = Files.readString(scene)
        openModel()
        val stored = frame.properties().fieldText(posX)
        frame.properties().enter(posX, "abc")
        LifecycleHarness.awaitState("the refusal is shown", true) { frame.properties().errorText("PositionComponent", "localPosition.x").isNotEmpty() }
        assertEquals("a refused value writes nothing", original, Files.readString(scene))
        assertEquals("the stored value is put back", stored, frame.properties().fieldText(posX))
        assertEquals("a refused value creates no history", "", history())

        frame.properties().enter(posX, "2.5")
        LifecycleHarness.awaitState("the valid value is saved", 2.5) { entity0()["PositionComponent"]["localPosition"]["x"].asDouble() }
        LifecycleHarness.awaitState("the error is cleared", "") { frame.properties().errorText("PositionComponent", "localPosition.x") }
    }

    @Test
    fun noOpSubmissionsAndSelectionChangesWriteNothing() {
        val original = Files.readString(scene)
        val tree = openModel()
        frame.properties().enter(name, frame.properties().fieldText(name))
        frame.properties().enter(posX, frame.properties().fieldText(posX))
        Thread.sleep(1000)
        assertEquals("submitting the stored values changes nothing", original, Files.readString(scene))
        assertEquals("and creates no edit", "", history())

        // selecting other content and coming back rebuilds the panel without an edit
        tree.clickRow("Model 2")
        LifecycleHarness.awaitState("Properties show Model 2", "Model 2") { frame.properties().headerTitle() }
        tree.clickRow("Model 0")
        LifecycleHarness.awaitState("Properties show Model 0 again", "Model 0") { frame.properties().headerTitle() }
        assertEquals(original, Files.readString(scene))
        assertEquals("selection created no edit", "", history())
    }
}
