package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.remoterobot.utils.waitFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Per-instance optical overrides of a PBR model (OPT-01..03): transmission in percent, IOR, their ranges and the
 * isolation of one instance from another and from the model's files. Edits go through the Render section of
 * Properties; Undo and Redo go through the scene document.
 */
class OpticalMaterialUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun openEntity(row: String): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        frame.openProperties()
        tree.clickRow(row)
        LifecycleHarness.awaitState("Properties show $row", row) { frame.properties().headerTitle() }
        return tree
    }

    private fun entity(id: Int): JsonNode? = ObjectMapper().readTree(Files.readString(scene))["ecs"]?.get(id.toString())

    /** The material the panel offers overrides for: the suffix of its `optics-transmission-<id>` field. */
    private fun material(): String {
        waitFor(Duration.ofSeconds(20), Duration.ofMillis(500), "the optics section lists a material") {
            frame.properties().fieldNames("optics-transmission-").isNotEmpty()
        }
        return frame.properties().fieldNames("optics-transmission-").first().removePrefix("optics-transmission-")
    }

    private fun override(id: Int, field: String): String? = entity(id)?.findValues(field)?.firstOrNull()?.asText()

    @Test
    fun transmissionPercentAndIorRoundTripWithUndoAndRedo() {
        val original = Files.readString(scene)
        openEntity("Model 6")
        val id = material()
        val p = frame.properties()

        p.enter("optics-transmission-$id", "25")
        LifecycleHarness.awaitState("transmission is stored as a fraction", "0.25") { override(6, "transmission") }
        val afterTransmission = Files.readString(scene)
        assertTrue("only entity 6 changed", LifecycleHarness.sameExcept(original, afterTransmission, "/ecs/6"))

        p.enter("optics-ior-$id", "1.7")
        LifecycleHarness.awaitState("ior is stored", "1.7") { override(6, "ior") }
        val afterIor = Files.readString(scene)
        assertTrue("only entity 6 changed", LifecycleHarness.sameExcept(original, afterIor, "/ecs/6"))
        assertEquals("the panel shows percent", "25", p.fieldText("optics-transmission-$id"))

        // reopen: the stored values are what the panel shows
        frame.saveAll()
        reset.reopenTextEditor(scene.toString())
        assertEquals(afterIor, Files.readString(scene))

        undoThroughSceneDocument(scene, afterTransmission)
        undoThroughSceneDocument(scene, original)
        LifecycleHarness.awaitState("both overrides are gone after two Undos", null) { override(6, "transmission") }
        assertEquals("the source model and every asset are untouched", emptySet<String>(), project.differences() - setOf(scenePath))
    }

    @Test
    fun outOfRangeAndNonNumericValuesAreRefusedAndWriteNothing() {
        val original = Files.readString(scene)
        openEntity("Model 6")
        val id = material()
        val p = frame.properties()
        for ((field, bad) in listOf("transmission" to "101", "transmission" to "-1", "transmission" to "abc", "ior" to "0.5", "ior" to "4", "ior" to "NaN")) {
            val name = "optics-$field-$id"
            val shown = p.fieldText(name)
            p.enter(name, bad)
            LifecycleHarness.awaitState("'$bad' for $field is refused with a reason", true) { opticsError(id).isNotEmpty() }
            assertEquals("'$bad' wrote nothing", original, Files.readString(scene))
            assertEquals("the previous display value returns", shown, p.fieldText(name))
        }
        assertEquals("refusals leave no history", "", reset.historyText(scene.toString()))
    }

    private fun opticsError(id: String): String =
        runCatching { frame.properties().labelTexts("optics-error-$id").firstOrNull().orEmpty() }.getOrDefault("")

    @Test
    fun editingOneInstanceLeavesAnotherInstanceOfTheModelAndTheModelFilesAlone() {
        // setup, not the operation under test: entity 9's asset is unresolved, so point it at entity 6's model on disk
        val model = entity(6)!!["components"]["RenderComponent"].findValue("assetName").asText()
        Files.writeString(scene, Files.readString(scene).replace("model_extra500", model))
        ide.refresh(scene.toString())
        val original = Files.readString(scene)
        openEntity("Model 6")
        val id = material()
        val sharing = entity(9)!!["components"]["RenderComponent"].findValue("assetName").asText()
        assertEquals("entity 9 shares entity 6's model", model, sharing)

        frame.properties().enter("optics-transmission-$id", "40")
        LifecycleHarness.awaitState("instance 6 has the override", "0.4") { override(6, "transmission") }
        assertEquals("the other instance has none", null, override(9, "transmission"))
        assertTrue("only entity 6 changed", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/6"))

        // back to the default: the override members are omitted again
        frame.properties().enter("optics-transmission-$id", "0")
        LifecycleHarness.awaitState("the default is stored without an override member", null) { override(6, "transmission") }
        assertEquals("the other instance is untouched", null, override(9, "transmission"))
        assertEquals("entity 6 is as before the edits", ObjectMapper().readTree(original)["ecs"]["6"], entity(6))
        assertEquals("the model's files are untouched", setOf(scenePath), project.differences())
    }

    @Test
    fun anOverrideNamingAMaterialThatIsGoneStaysVisibleAndSurvivesEditsToTheRealOne() {
        openEntity("Model 6")
        val id = material()
        frame.properties().enter("optics-transmission-$id", "25")
        LifecycleHarness.awaitState("the override is stored", "0.25") { override(6, "transmission") }
        frame.saveAll()
        // setup, not the operation under test: the stored override now names a material the model does not have
        val ghost = "ghost_material"
        val renamed = Files.readString(scene).replace("\"$id\"", "\"$ghost\"")
        assertTrue("the override key was found", renamed != Files.readString(scene))
        Files.writeString(scene, renamed)
        ide.refresh(scene.toString())
        reset.closeAllEditors()
        reset.discardUnsavedDocuments()
        val tree = openEntity("Model 6")
        tree.clickRow("Model 2")
        tree.clickRow("Model 6")
        LifecycleHarness.awaitState("the unresolved identity is named", true) { frame.properties().exists("optics-unresolved-$ghost") }
        assertEquals("looking at it wrote nothing", renamed, Files.readString(scene))
        assertTrue("the real material is still editable", frame.properties().fieldNames("optics-transmission-").isNotEmpty())

        val real = material()
        frame.properties().enter("optics-ior-$real", "1.6")
        LifecycleHarness.awaitState("the real material's edit is stored", "1.6") { override(6, "ior") }
        assertTrue("the unresolved override is preserved by the edit", Files.readString(scene).contains("\"$ghost\""))
        assertEquals("with its stored value", "0.25", entity(6)!!.findValue(ghost)?.get("transmission")?.asText())
    }

    @Test
    fun aModelThatCannotBeReadOffersNoOpticalEditsAndExplainsWhy() {
        val original = Files.readString(scene)
        openEntity("Model 9")
        val p = frame.properties()
        Thread.sleep(2000)
        assertTrue("no optical editor is offered: ${p.fieldNames("optics-")}", p.fieldNames("optics-transmission-").isEmpty() && p.fieldNames("optics-ior-").isEmpty())
        assertEquals("looking wrote nothing", original, Files.readString(scene))
        assertEquals("and left no history", "", reset.historyText(scene.toString()))
    }

    @Test
    fun anOlderUncommittedValueCannotOverwriteANewerOverride() {
        openEntity("Model 6")
        val id = material()
        val p = frame.properties()
        p.enter("optics-transmission-$id", "25")
        LifecycleHarness.awaitState("the first value is stored", "0.25") { override(6, "transmission") }
        frame.saveAll()

        // an older editor holds 40 while another editor stores 30
        p.type("optics-transmission-$id", "40")
        val newer = Files.readString(scene).replace("0.25", "0.3")
        Files.writeString(scene, newer)
        ide.refresh(scene.toString())
        Thread.sleep(1500)
        p.focus("optics-ior-$id")
        Thread.sleep(2000)
        frame.saveAll()
        assertEquals("the newer override stays", "0.3", override(6, "transmission"))
        assertTrue("and the older value was never written", !Regex("\"transmission\"\\s*:\\s*0\\.4").containsMatchIn(Files.readString(scene)))
    }
}
