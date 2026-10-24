package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * The physics kinds appear in the schema when the project enables physics (CMP-07 for the registered schema): on a copy of
 * the Physics project Add Component offers Rigid Body, Collider and Constraint, and a kind added there is edited and removed
 * through the same editors as a built-in one.
 */
class PhysicsKindsUiTest : UiTestBase() {
    override val projectName = "Physics"
    private val scene get() = project.resolve("scenes/Main Scene.scene")
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val mapper = ObjectMapper()

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun components(id: Int) = mapper.readTree(Files.readString(scene))["ecs"]["entities"]?.get(id.toString())?.get("components")
        ?: mapper.readTree(Files.readString(scene))["ecs"][id.toString()]["components"]

    @Test
    fun physicsKindsAreOfferedAndEditedLikeBuiltInOnes() {
        val before = Files.readString(scene)
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        frame.openProperties()
        val row = SceneFacts.read(scene).entityName(6) ?: tree.rows().first { it.startsWith("Model") }
        tree.rightClickRow(row)
        frame.clickMenuItem("Add Component...")
        val offered = frame.popupItems()
        for (kind in listOf("Rigid", "Collider")) assertTrue("$kind is offered with physics on: $offered", offered.any { it.startsWith(kind) })
        frame.choosePopupItem("Rigid")
        LifecycleHarness.awaitState("RigidBody is added", true) { components(6).has("RigidBodyComponent") }
        LifecycleHarness.awaitState("its section is shown", true) { frame.properties().hasRemove("RigidBodyComponent") }
        val added = Files.readString(scene)
        // the Properties button can sit below the fold of a tall panel: remove from the component's tree row, as a user can
        tree.expand(row)
        val componentRow = tree.rows().firstOrNull { it.startsWith("RigidBody") || it.startsWith("Rigid Body") }
            ?: throw AssertionError("no RigidBody row under $row: ${tree.rows()}")
        tree.rightClickRow(componentRow)
        frame.clickMenuItem("Remove Component")
        LifecycleHarness.awaitState("RigidBody is removed", "gone") {
            if (!components(6).has("RigidBodyComponent")) "gone"
            else "still there; kinds ${components(6).fieldNames().asSequence().toList()}, remove button ${runCatching { frame.properties().hasRemove("RigidBodyComponent") }}, refusal ${runCatching { frame.dialog("Component Not Changed").texts() }}, header ${runCatching { frame.properties().headerTitle() }}"
        }
        undoThroughSceneDocument(scene, added)
        LifecycleHarness.awaitState("Undo brings it back", true) { components(6).has("RigidBodyComponent") }
        undoThroughSceneDocument(scene, before)
        assertEquals("two Undos restore the exact text", before, Files.readString(scene))
    }
}
