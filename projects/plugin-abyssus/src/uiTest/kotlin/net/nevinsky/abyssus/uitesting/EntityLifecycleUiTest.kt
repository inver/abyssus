package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Entity creation and removal cycles from the Abyssus tree (ENT-01..06): every write and every Undo and Redo comes from
 * the UI, and each stage is compared with the tree, the Properties header, the document and the saved file.
 */
class EntityLifecycleUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
        runCatching { ide.closeFile(scene.toString()) }
    }

    private fun openEcs(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        return tree
    }

    private fun hasRow(tree: AbyssusTree, name: String) = tree.rows().any { it == name || it.startsWith("$name ") }

    @Test
    fun removeUndoRedoFromTreeClearsStaleSelection() {
        val original = Files.readString(scene)
        val path = scene.toString()
        val tree = openEcs()
        frame.openProperties()
        tree.clickRow("Model 0")
        LifecycleHarness.awaitState("Properties show the selected entity", "Model 0") { frame.properties().headerTitle() }

        // remove: only entity 0 goes, and nothing still points at it
        tree.rightClickRow("Model 0")
        frame.clickMenuItem("Remove Entity")
        LifecycleHarness.awaitState("entity 0 is gone from the file", null) { SceneFacts.read(scene).entityName(0) }
        val removed = Files.readString(scene)
        assertTrue("only entity 0 was removed", LifecycleHarness.sameExcept(original, removed, "/ecs/0"))
        LifecycleHarness.awaitState("the row is gone from the tree", false) { hasRow(tree, "Model 0") }
        assertFalse("no row of the removed entity stays selected: ${tree.selectedRows()}", tree.selectedRows().any { it.startsWith("Model 0") })
        assertFalse("Properties no longer edit the removed entity", frame.properties().headerTitle() == "Model 0")

        // undo from the tree, with the tree focused: the exact original text and the row come back
        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo from the tree restores the document", original) { ide.documentText(path) }
        LifecycleHarness.awaitState("the row is back after Undo", true) { hasRow(tree, "Model 0") }

        // redo from the tree: the same entity goes again, nothing else changes
        tree.focus()
        frame.redo()
        LifecycleHarness.awaitState("Redo from the tree removes the entity again", removed) { ide.documentText(path) }
        LifecycleHarness.awaitState("the row is gone after Redo", false) { hasRow(tree, "Model 0") }
        assertFalse("no stale selection after Redo: ${tree.selectedRows()}", tree.selectedRows().any { it.startsWith("Model 0") })
        frame.saveAll()
        LifecycleHarness.awaitState("the removal is saved", removed) { Files.readString(scene) }
        assertEquals("the assets are untouched", emptySet<String>(), project.differences() - setOf(scenePath))
    }

    /** Waits for the error dialog [title] to show every one of [parts] (case-insensitive); fails with every text it drew. */
    private fun awaitRefusal(stage: String, vararg parts: String) {
        val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
        var texts = emptyList<String>()
        while (System.nanoTime() < deadline) {
            texts = runCatching { frame.dialog("Component Not Changed").texts() }.getOrDefault(emptyList())
            val joined = texts.joinToString(" ").lowercase()
            if (parts.all { joined.contains(it.lowercase()) }) return
            Thread.sleep(300)
        }
        throw AssertionError("$stage: the dialog should mention ${parts.toList()} but drew $texts")
    }

    /** The new entity's row must be the tree's selection (spec: "the new entity's row SHALL be selected"); reports both selections if not. */
    private fun awaitNewRowSelected(tree: AbyssusTree, name: String) {
        try {
            LifecycleHarness.awaitState("the new row is selected", true) { tree.selectedRows().any { it.startsWith(name) } }
        } catch (e: AssertionError) {
            val header = runCatching { frame.properties().headerTitle() }.getOrElse { "n/a (${it.message})" }
            throw AssertionError("$name is not selected after it was created: tree selection ${tree.selectedRows()}, Properties header '$header', rows ${tree.rows()}", e)
        }
    }

    private fun sceneRoot() = com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(scene))

    private fun entity(id: Int): com.fasterxml.jackson.databind.JsonNode? = sceneRoot()["ecs"]?.get(id.toString())

    /** Removes the entity row [name] through its context menu and waits until entity [id] is gone from the file. */
    private fun removeEntityRow(tree: AbyssusTree, name: String, id: Int) {
        tree.rightClickRow(name)
        frame.clickMenuItem("Remove Entity")
        LifecycleHarness.awaitState("entity $id is removed from the file", null) { entity(id)?.toString() }
        LifecycleHarness.awaitState("row '$name' leaves the tree", false) { hasRow(tree, name) }
    }

    @Test
    fun newCameraFromEcsEditedReopenedAndRemoved() {
        val original = Files.readString(scene)
        val path = scene.toString()
        val tree = openEcs()
        frame.openProperties()

        tree.addComponentOnEcs("Camera")
        LifecycleHarness.awaitState("the new camera entity is saved", true) { entity(11)?.get("components")?.has("CameraComponent") == true }
        assertTrue("only entity 11 was added", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/11"))
        LifecycleHarness.awaitState("its row is in the tree", true) { hasRow(tree, "Entity 11") }
        awaitNewRowSelected(tree, "Entity 11")

        // edit through Properties: only the field of view changes
        val created = Files.readString(scene)
        tree.clickRow("Entity 11")
        LifecycleHarness.awaitState("Properties show the new entity", "Entity 11") { frame.properties().headerTitle() }
        frame.properties().enter("field-CameraComponent-camera.fieldOfView", "50")
        LifecycleHarness.awaitState("the edit is saved", 50) { entity(11)?.get("components")?.get("CameraComponent")?.findValue("fieldOfView")?.asInt() }
        val edited = Files.readString(scene)
        assertTrue("the edit touched nothing outside entity 11", LifecycleHarness.sameExcept(created, edited, "/ecs/11"))

        // reopen the editor: the edit is what the file holds
        reset.reopenTextEditor(path)
        LifecycleHarness.awaitState("the reopened editor shows the edit", edited) { ide.documentText(path) }

        // remove: the scene is as it was before the entity existed
        removeEntityRow(tree, "Entity 11", 11)
        assertTrue("unrelated data is as before", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/11"))
        assertFalse(Files.readString(scene).contains("\"11\""))
    }

    @Test
    fun newRenderFromEcsKeepsTheModelAssetWhenRemoved() {
        val original = Files.readString(scene)
        val tree = openEcs()
        tree.addComponentOnEcs("Render")
        frame.choosePopupItem("tree")
        LifecycleHarness.awaitState("the render entity is saved", "tree") { entity(11)?.get("components")?.get("RenderComponent")?.findValue("assetName")?.asText() }
        assertTrue("only entity 11 was added", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/11"))
        LifecycleHarness.awaitState("its row is in the tree", true) { hasRow(tree, "Entity 11") }
        awaitNewRowSelected(tree, "Entity 11")

        removeEntityRow(tree, "Entity 11", 11)
        assertTrue("the scene is as before", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/11"))
        assertEquals("the tree model asset is untouched", emptySet<String>(), project.differences() - setOf(scenePath))
    }

    @Test
    fun modelAndTerrainFromTheAddMenuKeepTheirAssetsWhenRemoved() {
        val original = Files.readString(scene)
        val tree = openEcs()
        val sceneRow = SceneFacts.read(scene).treeLabel

        tree.rightClickRow(sceneRow)
        frame.clickMenuPath("Add", "Add Asset")
        frame.choosePopupItem("tree")
        LifecycleHarness.awaitState("the model entity is saved", "tree") { entity(11)?.get("components")?.get("RenderComponent")?.findValue("assetName")?.asText() }
        LifecycleHarness.awaitState("its row is in the tree", true) { hasRow(tree, "Model 11") }

        tree.rightClickRow(sceneRow)
        frame.clickMenuPath("Add", "Add Asset")
        frame.choosePopupItem("terrain_")
        LifecycleHarness.awaitState("the terrain entity is saved", true) { entity(12)?.get("components")?.get("RenderComponent")?.findValue("type")?.asText() == "TERRAIN" }
        LifecycleHarness.awaitState("its row is in the tree", true) { hasRow(tree, "Terrain 12") }

        removeEntityRow(tree, "Terrain 12", 12)
        removeEntityRow(tree, "Model 11", 11)
        assertTrue("the scene is as before", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/11"))
        assertEquals("both source assets are untouched", emptySet<String>(), project.differences() - setOf(scenePath))
    }

    @Test
    fun everyLightChoiceFromTheAddMenuCanBeRemoved() {
        val original = Files.readString(scene)
        val tree = openEcs()
        val sceneRow = SceneFacts.read(scene).treeLabel
        val expected = listOf("Directional" to "Directional Light", "Sun" to "Sun", "Spot" to "Spot Light")

        expected.forEachIndexed { index, (choice, label) ->
            val id = 11 + index
            tree.rightClickRow(sceneRow)
            frame.clickMenuPath("Add", "Add Light")
            frame.choosePopupItem(choice)
            LifecycleHarness.awaitState("light $choice is saved as entity $id", true) { entity(id)?.get("components")?.has("LightComponent") == true }
            LifecycleHarness.awaitState("its row '$label $id' is in the tree", true) { hasRow(tree, "$label $id") }
        }
        expected.indices.reversed().forEach { index ->
            val (_, label) = expected[index]
            removeEntityRow(tree, "$label ${11 + index}", 11 + index)
        }
        assertTrue("the scene is as before", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/11"))
    }

    private fun toolbarButton(name: String) = frame.find<ComponentFixture>(byXpath("//div[@name='$name' and @class='JButton']"), Duration.ofSeconds(30))

    @Test
    fun lightAndAssetFromTheSceneToolbarCreateEntitiesLikeTheTreeMenu() {
        val original = Files.readString(scene)
        val tree = openEcs()
        ide.openSceneView(scene.toString())
        frame.sceneToolbar().also { it.awaitShown() }
        LifecycleHarness.awaitState("Add Light is enabled", true, Duration.ofSeconds(60)) {
            toolbarButton("add-light").callJs<Boolean>("component.isEnabled()", runInEdt = true)
        }
        toolbarButton("add-light").click()
        frame.choosePopupItem("Directional")
        LifecycleHarness.awaitState("the toolbar light is saved as entity 11", true) { entity(11)?.get("components")?.has("LightComponent") == true }
        LifecycleHarness.awaitState("its row is in the tree", true) { hasRow(tree, "Directional Light 11") }

        toolbarButton("add-asset").click()
        frame.choosePopupItem("tree")
        LifecycleHarness.awaitState("the toolbar model is saved as entity 12", "tree") { entity(12)?.get("components")?.get("RenderComponent")?.findValue("assetName")?.asText() }
        LifecycleHarness.awaitState("its row is in the tree", true) { hasRow(tree, "Model 12") }

        removeEntityRow(tree, "Model 12", 12)
        removeEntityRow(tree, "Directional Light 11", 11)
        assertTrue("the scene is as before", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/11"))
        assertEquals("both source assets are untouched", emptySet<String>(), project.differences() - setOf(scenePath))
    }

    @Test
    fun referencedEntitiesAreRefusedUntilTheReferencesAreCleared() {
        val original = Files.readString(scene)
        val path = scene.toString()
        val tree = openEcs()
        frame.openProperties()

        // entity 3 is Camera 4's look-at target: refused, byte-identical, naming the referring entity and field
        tree.rightClickRow("'Direction' handle")
        frame.clickMenuItem("Remove Entity")
        awaitRefusal("entity 3 is a look-at target", "entity 4", "lookAtId")
        frame.closeStrayDialogs()
        assertEquals("the scene is byte-identical after the refusal", original, Files.readString(scene))
        assertEquals(original, ide.documentText(path))

        // entity 4 is the parent of entity 3
        tree.rightClickRow("Camera 4")
        frame.clickMenuItem("Remove Entity")
        awaitRefusal("entity 4 is a parent", "entity 3", "parentEntityId")
        frame.closeStrayDialogs()
        assertEquals(original, Files.readString(scene))

        // clear the look-at through Properties; entity 3 is still the endpoint of entity 5
        tree.clickRow("Camera 4")
        LifecycleHarness.awaitState("Properties show Camera 4", "Camera 4") { frame.properties().headerTitle() }
        frame.properties().enter("field-PositionComponent-lookAtId", "-1")
        LifecycleHarness.awaitState("the look-at is cleared", true) { entity(4)?.get("components")?.get("PositionComponent")?.get("lookAtId")?.asInt(-1) != 3 }
        val cleared = Files.readString(scene)
        tree.rightClickRow("'Direction' handle")
        frame.clickMenuItem("Remove Entity")
        awaitRefusal("entity 3 is an endpoint", "entity 5", "Point2PointPositionComponent")
        frame.closeStrayDialogs()
        assertEquals("nothing else was deleted, detached or retargeted", cleared, Files.readString(scene))
    }
}
