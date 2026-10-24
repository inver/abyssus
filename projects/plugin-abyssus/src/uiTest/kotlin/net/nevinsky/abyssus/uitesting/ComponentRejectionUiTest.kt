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
 * Component values and references the editors refuse (CMP-04, CMP-05, CMP-07): an unknown entity, text, a self reference
 * and a parent cycle leave the document, the saved file and the undo history exactly as they were, and a reference is
 * cleared by removing its component. The physics kinds are not offered while physics is off.
 */
class ComponentRejectionUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val mapper = ObjectMapper()

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun openEntity(id: Int): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        frame.openProperties()
        val name = SceneFacts.read(scene).entityName(id)!!
        tree.clickRow(name)
        LifecycleHarness.awaitState("Properties show entity $id", name) { frame.properties().headerTitle() }
        return tree
    }

    private fun component(id: Int, kind: String): JsonNode? = mapper.readTree(Files.readString(scene))["ecs"]?.get(id.toString())?.get("components")?.get(kind)

    /** Opens the section [key] when its field [name] is not drawn (a section can come back collapsed after a rebuild); a few tries, as a user would click again. */
    private fun showField(name: String, key: String) {
        repeat(4) {
            if (frame.properties().exists(name)) return
            frame.properties().clickChevron(key)
            Thread.sleep(1000)
        }
    }

    private fun undoText() = reset.historyText(scene.toString())

    /** Enters [bad] into [field] and proves the reason is shown while the text, the file and the undo history stay as they were. */
    private fun assertRefused(kind: String, field: String, bad: String, savedText: String, undoBefore: String) {
        frame.properties().enter("field-$kind-$field", bad)
        LifecycleHarness.awaitState("'$bad' for $field is explained", "refused") {
            val reason = runCatching { frame.properties().errorText(kind, field) }
            if (reason.getOrDefault("").isNotBlank()) "refused"
            else "no reason shown (${reason.exceptionOrNull()?.javaClass?.simpleName}); document ${if (Files.readString(scene) == savedText) "unchanged" else "CHANGED"}, field ${runCatching { frame.properties().fieldText("field-$kind-$field") }}, error labels ${runCatching { frame.properties().labelTexts("error") }}, sections ${runCatching { frame.properties().sectionTitles() }}"
        }
        assertEquals("'$bad' wrote nothing", savedText, Files.readString(scene))
        assertEquals("'$bad' added no undo step", undoBefore, undoText())
    }

    @Test
    fun parentReferencesAndCyclesAreRefusedWithoutAChangeAndAValidOneIsSaved() {
        val tree = openEntity(4)
        val row = SceneFacts.read(scene).entityName(4)!!
        tree.rightClickRow(row)
        frame.clickMenuItem("Add Component...")
        frame.choosePopupItem("Parent")
        LifecycleHarness.awaitState("Parent is added to entity 4", true) { component(4, "ParentComponent") != null }
        LifecycleHarness.awaitState("its section is shown", true) { frame.properties().hasRemove("ParentComponent") }
        val kind = "ParentComponent"
        // a section the panel drew collapsed has no field yet: open it, as a user does
        showField("field-$kind-parentEntityId", "4.$kind")
        LifecycleHarness.awaitState("the field is shown", true) { frame.properties().exists("field-$kind-parentEntityId") }
        val saved = Files.readString(scene)
        val undo = undoText()

        // entity 3 is parented to entity 4: making 4 a child of 3 (or of itself) would close a cycle
        for (bad in listOf("999", "abc", "-1", "4", "3")) assertRefused(kind, "parentEntityId", bad, saved, undo)

        // a valid parent is saved by the same editor (ComponentLifecycleUiTest); here nothing was written at all
        assertEquals("the refusals left the document as it was after the addition", saved, Files.readString(scene))
        assertEquals("and the history", undo, undoText())
    }

    @Test
    fun removingAParentComponentClearsTheReferenceAndUndoRestoresTheExactText() {
        val original = Files.readString(scene)
        assertEquals("entity 3 is parented to entity 4", 4, component(3, "ParentComponent")?.get("parentEntityId")?.asInt())
        openEntity(3)
        LifecycleHarness.awaitState("the Parent section has a Remove button", true) { frame.properties().hasRemove("ParentComponent") }
        frame.properties().clickRemove("ParentComponent")
        LifecycleHarness.awaitState("the reference is gone", true) { component(3, "ParentComponent") == null }
        assertTrue("only entity 3 changed", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/3"))
        val cleared = Files.readString(scene)
        undoThroughSceneDocument(scene, original)
        assertEquals("Undo restores the exact text", original, Files.readString(scene))
        assertFalse(cleared == original)
    }

    @Test
    fun invalidLightNumbersAreRefusedAndTheDocumentAndHistoryStayUnchanged() {
        openEntity(8)
        val kind = "LightComponent"
        LifecycleHarness.awaitState("the Light section is shown", true) { frame.properties().hasRemove(kind) }
        showField("field-$kind-intensity", "8.$kind")
        val saved = Files.readString(scene)
        val undo = undoText()
        for ((field, bad) in listOf("intensity" to "abc", "range" to "abc", "range" to "-3", "coneAngle" to "abc", "coneAngle" to "-5", "edgeSoftness" to "abc", "edgeSoftness" to "-1", "color.r" to "abc")) {
            assertRefused(kind, field, bad, saved, undo)
        }
    }

    @Test
    fun physicsKindsAreNotOfferedWhilePhysicsIsOff() {
        val tree = openEntity(7)
        tree.rightClickRow(SceneFacts.read(scene).entityName(7)!!)
        frame.clickMenuItem("Add Component...")
        val offered = frame.popupItems()
        frame.dismissPopup()
        for (physics in listOf("Rigid", "Collider", "Constraint")) {
            assertFalse("$physics is offered with physics off: $offered", offered.any { it.startsWith(physics) })
        }
    }
}
