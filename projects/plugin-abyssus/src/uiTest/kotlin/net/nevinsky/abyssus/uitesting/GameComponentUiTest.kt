package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Game components are read only (CMP-06): on a copy of the Custom project the plane component of entity 0 is shown with
 * its stored values, offers no editor and no Remove, and stays byte for byte as it was when a modeled kind is added to or
 * removed from the same entity.
 */
class GameComponentUiTest : UiTestBase() {
    override val projectName = "Custom"
    private val scene get() = project.resolve("scenes/Field.scene")
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val mapper = ObjectMapper()

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun plane() = mapper.readTree(Files.readString(scene))["ecs"]["0"]["components"]["PlaneComponent"]

    @Test
    fun aGameComponentIsShownReadOnlyAndSurvivesEditsToItsEntity() {
        val before = Files.readString(scene)
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        frame.openProperties()
        val row = tree.rows().first { it.startsWith("Plane") || it.contains("Plane") }
        tree.clickRow(row)
        LifecycleHarness.awaitState("the plane component is shown", true) { frame.properties().sectionTitles().any { it.contains("Plane", ignoreCase = true) } }
        assertFalse("a game component has no Remove", frame.properties().hasRemove("PlaneComponent"))
        assertFalse("and no editor for its values", frame.properties().exists("field-PlaneComponent-lineLength"))
        assertEquals("looking at it wrote nothing", before, Files.readString(scene))

        val stored = plane()
        assertEquals(22, stored["lineLength"].asInt())
        tree.rightClickRow(row)
        frame.clickMenuItem("Add Component...")
        frame.choosePopupItem("Camera")
        LifecycleHarness.awaitState("Camera is added", true) { mapper.readTree(Files.readString(scene))["ecs"]["0"]["components"].has("CameraComponent") }
        assertEquals("the game component is untouched by the addition", stored, plane())
        undoThroughSceneDocument(scene, before)
        assertEquals("Undo restores the exact text", before, Files.readString(scene))
    }
}
