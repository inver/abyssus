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
 * Component add, edit and remove cycles on one entity (CMP-01..): every write comes from the Abyssus tree or the
 * Properties panel, every Undo and Redo from the IDE, and each stage is compared with the document and the saved file.
 *
 * The entity is `Directional Light 7` (entity 7), which holds only Name, Type and Position, so every other modeled kind
 * can be added to it.
 */
class ComponentLifecycleUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val entityRow = "Directional Light 7"

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun openEntity(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        frame.openProperties()
        tree.clickRow(entityRow)
        LifecycleHarness.awaitState("Properties show the entity", entityRow) { frame.properties().headerTitle() }
        return tree
    }

    private fun components(id: Int): JsonNode? = ObjectMapper().readTree(Files.readString(scene))["ecs"]?.get(id.toString())?.get("components")

    private fun component(kind: String): JsonNode? = components(7)?.get(kind)

    private fun addFromTree(tree: AbyssusTree, label: String) {
        tree.rightClickRow(entityRow)
        frame.clickMenuItem("Add Component...")
        frame.choosePopupItem(label)
    }

    private fun awaitAdded(kind: String) =
        LifecycleHarness.awaitState("$kind is saved on entity 7", true) { component(kind) != null }

    private fun awaitRemoved(kind: String) {
        LifecycleHarness.awaitState("$kind is gone from entity 7", true) { component(kind) == null }
        LifecycleHarness.awaitState("its Properties section is gone", false) { frame.properties().hasRemove(kind) }
    }

    /** The IDE's Undo text for the scene: the command name the user would see on Edit > Undo. */
    private fun undoText() = reset.historyText(scene.toString())

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

    private fun assertOnlyEntity7Changed(before: String, stage: String) =
        Files.readString(scene).let { after ->
            assertTrue("$stage touched something outside entity 7: ${LifecycleHarness.lineDiff(before, after)}", LifecycleHarness.sameExcept(before, after, "/ecs/7"))
        }

    /** Adds [kind] from the tree, edits [field] through Properties, removes the section with its button; every stage is checked. */
    private fun cycleWithPropertiesRemove(tree: AbyssusTree, label: String, kind: String, field: String, value: String, jsonField: String) {
        val before = Files.readString(scene)
        addFromTree(tree, label)
        awaitAdded(kind)
        assertOnlyEntity7Changed(before, "Add $label")
        LifecycleHarness.awaitState("the section of $kind shows a Remove button", true) { frame.properties().hasRemove(kind) }

        val added = Files.readString(scene)
        frame.properties().enter("field-$kind-$field", value)
        LifecycleHarness.awaitState("$jsonField is saved", true) { component(kind)?.findValue(jsonField.substringAfterLast('.')) != null }
        assertOnlyEntity7Changed(added, "editing $jsonField")
        val edited = Files.readString(scene)

        frame.properties().clickRemove(kind)
        awaitRemoved(kind)
        assertOnlyEntity7Changed(before, "Remove $label")
        assertEquals("entity 7 is as it was before $label was added", entity7(before), entity7(Files.readString(scene)))
        val removed = Files.readString(scene)
        assertEquals("the Undo the user sees names the removal", "_Undo Remove Component | Undo Remove Component", undoText())

        // one Undo brings the edited component back, one Redo removes it again
        undoThroughSceneDocument(scene, edited)
        LifecycleHarness.awaitState("Undo brings $kind back", true) { component(kind) != null }
        redoThroughSceneDocument(removed)
        awaitRemoved(kind)
    }

    private fun entity7(text: String): JsonNode? = ObjectMapper().readTree(text)["ecs"]?.get("7")

    @Test
    fun parentCameraAndLightCyclesFromTheTree() {
        val original = Files.readString(scene)
        val tree = openEntity()
        cycleWithPropertiesRemove(tree, "Parent", "ParentComponent", "parentEntityId", "1", "parentEntityId")
        cycleWithPropertiesRemove(tree, "Camera", "CameraComponent", "camera.far", "500", "far")
        cycleWithPropertiesRemove(tree, "Light", "LightComponent", "intensity", "2.5", "intensity")
        assertTrue("the scene is as before the cycles", LifecycleHarness.sameExcept(original, Files.readString(scene), "/ecs/7"))
        assertEquals("no asset changed", emptySet<String>(), project.differences() - setOf(scenePath))
    }

    @Test
    fun addedComponentIsNotOfferedAgainAndLightIsOfferedForAModel() {
        val tree = openEntity()
        addFromTree(tree, "Camera")
        awaitAdded("CameraComponent")
        val saved = Files.readString(scene)

        // a kind the entity holds is not offered again
        tree.rightClickRow(entityRow)
        frame.clickMenuItem("Add Component...")
        val offered = frame.popupItems()
        frame.dismissPopup()
        assertFalse("Camera is offered twice: $offered", offered.any { it.startsWith("Camera") })
        assertTrue("Parent is still offered: $offered", offered.any { it.startsWith("Parent") })
        assertEquals("choosing nothing writes nothing", saved, Files.readString(scene))

        // Light is offered for a model entity (JRN-02 relies on it)
        tree.rightClickRow("Model 0")
        frame.clickMenuItem("Add Component...")
        val modelOffer = frame.popupItems()
        frame.dismissPopup()
        assertTrue("Light is not offered for Model 0: $modelOffer", modelOffer.any { it.startsWith("Light") })
    }

    @Test
    fun renderChoiceTypeEditorAndTreeRowRemoval() {
        val before = Files.readString(scene)
        val tree = openEntity()
        val kind = "RenderComponent"
        addFromTree(tree, "Render")
        frame.choosePopupItem("tree")
        awaitAdded(kind)
        assertEquals("the Undo the user sees names the addition", "_Undo Add Component | Undo Add Component", undoText())
        assertEquals("the chosen model is the asset", "tree", component(kind)?.findValue("assetName")?.asText())

        // a text field with an optional value, committed from the UI; wait for the section the rebuilt panel draws
        LifecycleHarness.awaitState("the Render section is shown", true) { frame.properties().hasRemove(kind) }
        if (frame.properties().isCollapsed("0.$kind".replace("0.", "7."))) frame.properties().clickChevron("7.$kind")
        LifecycleHarness.awaitState("the shaderKey field is shown", true) { frame.properties().exists("field-$kind-shaderKey") }
        frame.properties().enter("field-$kind-shaderKey", "defaultShader")
        LifecycleHarness.awaitState("shaderKey is saved", "defaultShader") { component(kind)?.findValue("shaderKey")?.asText() }

        // the asset type choice: pick the next item with the keyboard
        val saved = Files.readString(scene)
        val other = frame.properties().pickOther("field-$kind-assetType")
        LifecycleHarness.awaitState("the pick is saved", other) { component(kind)?.findValue("type")?.asText() }
        assertOnlyEntity7Changed(saved, "the asset type pick")

        // remove from the tree: expand the entity and use the component row's menu
        tree.expand(entityRow)
        val row = tree.rows().firstOrNull { it.startsWith("Render") }
            ?: throw AssertionError("no component row for Render: ${tree.rows()}")
        tree.rightClickRow(row)
        frame.clickMenuItem("Remove Component")
        awaitRemoved(kind)
        assertOnlyEntity7Changed(before, "Remove Component")
    }

    @Test
    fun thePropertiesAddButtonAddsAKindWithOneUndoStep() {
        val before = Files.readString(scene)
        val tree = openEntity()
        assertTrue("the Add button is enabled for an entity with kinds left", frame.properties().isEnabled("add-component"))
        frame.properties().click("add-component")
        frame.choosePopupItem("Parent")
        awaitAdded("ParentComponent")
        assertOnlyEntity7Changed(before, "Add from Properties")
        assertEquals("the Undo the user sees names the addition", "_Undo Add Component | Undo Add Component", undoText())
        LifecycleHarness.awaitState("the new section can be removed from Properties", true) { frame.properties().hasRemove("ParentComponent") }
        val added = Files.readString(scene)
        undoThroughSceneDocument(scene, before)
        assertEquals("one Undo takes the whole addition back", before, Files.readString(scene))
        redoThroughSceneDocument(added)
        tree.focus()
    }

    /** Entity 7 with a path arrangement of the `tree` model: the one modeled kind with a boolean and a path asset, authored in Scene View. */
    private fun prepareArrangement() {
        val mapper = ObjectMapper()
        val root = mapper.readTree(Files.readString(scene)) as com.fasterxml.jackson.databind.node.ObjectNode
        val comps = root["ecs"]["7"]["components"] as com.fasterxml.jackson.databind.node.ObjectNode
        comps.set<JsonNode>("PathArrangementComponent", mapper.readTree(
            """{"points":[{"x":0,"y":0,"z":0},{"x":20,"y":0,"z":0}],"closed":false,"asset":{"type":"MODEL","assetName":"tree"},"spacing":10.0,"offset":0.0,"sides":"CENTER","alignToPath":true,"yawOffset":0.0,"slots":[]}"""))
        // the fixture's own style (`"key": value`), so the first plugin edit has no reason to reformat the file
        Files.writeString(scene, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root).replace("\" : ", "\": ") + "\n")
        ide.refresh(scene.toString())
    }

    /** Every entity of [before] other than 7, and every scene-level member, is as it was: the arrangement's copies are new entities. */
    private fun assertOthersUnchanged(before: String, stage: String) {
        val mapper = ObjectMapper()
        val old = mapper.readTree(before)
        val now = mapper.readTree(Files.readString(scene))
        for (id in old["ecs"].fieldNames().asSequence().filter { it != "7" }) {
            assertEquals("$stage changed entity $id", old["ecs"][id], now["ecs"][id])
        }
        for (name in old.fieldNames().asSequence().filter { it != "ecs" }) assertEquals("$stage changed $name", old[name], now[name])
    }

    @Test
    fun floatChoiceBooleanAndAssetEditorsCommitOmitDefaultsAndRefuseInvalidValues() {
        prepareArrangement()
        resetOpenDocumentsAfterPreparation()
        val kind = "PathArrangementComponent"
        openEntity()
        LifecycleHarness.awaitState("the arrangement section is shown", true) { frame.properties().hasRemove(kind) }
        if (frame.properties().isCollapsed("7.$kind")) frame.properties().clickChevron("7.$kind")
        val prepared = Files.readString(scene)
        val p = { frame.properties() }

        // FLOAT with an exclusive minimum: zero and text are refused, a positive value is saved
        for (bad in listOf("0", "-1", "abc")) {
            p().enter("field-$kind-spacing", bad)
            LifecycleHarness.awaitState("'$bad' is explained", true) { p().errorText(kind, "spacing").isNotBlank() }
            assertEquals("a refused spacing writes nothing", prepared, Files.readString(scene))
        }
        p().enter("field-$kind-spacing", "5")
        LifecycleHarness.awaitState("spacing is saved", 5.0) { component(kind)?.get("spacing")?.asDouble() }
        assertOthersUnchanged(prepared, "spacing")

        // FLOAT with an inclusive minimum: a negative offset is refused
        p().enter("field-$kind-offset", "-2")
        LifecycleHarness.awaitState("a negative offset is explained", true) { p().errorText(kind, "offset").isNotBlank() }
        assertEquals(0.0, component(kind)?.get("offset")?.asDouble() ?: 0.0, 0.0)

        // CHOICE
        val choice = p().pickOther("field-$kind-sides")
        LifecycleHarness.awaitState("the side is saved", choice) { component(kind)?.get("sides")?.asText() }

        // BOOLEAN: unchecking writes false, checking again leaves the default (true) either absent or true
        assertTrue("alignToPath starts checked", p().checked("field-$kind-alignToPath"))
        p().clickCheck("field-$kind-alignToPath")
        LifecycleHarness.awaitState("alignToPath false is saved", false) { component(kind)?.get("alignToPath")?.asBoolean(true) ?: true }
        p().clickCheck("field-$kind-alignToPath")
        LifecycleHarness.awaitState("the default is back and omitted from the file", true) { component(kind)?.get("alignToPath")?.asBoolean(true) ?: true }

        // ASSET_NAME: another model asset; one Undo brings the previous one back
        val beforeAsset = Files.readString(scene)
        val asset = p().pickOther("field-$kind-asset.assetName")
        LifecycleHarness.awaitState("the asset is saved", asset) { component(kind)?.get("asset")?.get("assetName")?.asText() }
        assertEquals("the points are untouched", ObjectMapper().readTree(prepared)["ecs"]["7"]["components"][kind]["points"], component(kind)?.get("points"))
        assertOthersUnchanged(prepared, "the editors")
        undoThroughSceneDocument(scene, beforeAsset)
        LifecycleHarness.awaitState("Undo restores the previous asset", "tree") { component(kind)?.get("asset")?.get("assetName")?.asText() }
    }

    /** The scene text changed on disk: no open buffer may keep the old text or history for the cases that follow. */
    private fun resetOpenDocumentsAfterPreparation() {
        reset.closeAllEditors()
        reset.discardUnsavedDocuments()
        ide.refresh(scene.toString())
        reset.dropUndoHistory()
        ide.refreshProjectView()
    }

    @Test
    fun renderOffersTerrainAssetsAndPoint2PointTakesEntityReferences() {
        val before = Files.readString(scene)
        val tree = openEntity()

        // a terrain asset is offered to Render beside the models
        addFromTree(tree, "Render")
        frame.choosePopupItem("terrain terrain")
        awaitAdded("RenderComponent")
        assertEquals("the terrain asset type is saved", "TERRAIN", component("RenderComponent")?.findValue("type")?.asText())
        assertOnlyEntity7Changed(before, "Render with a terrain")
        LifecycleHarness.awaitState("Render can be removed from Properties", true) { frame.properties().hasRemove("RenderComponent") }
        frame.properties().clickRemove("RenderComponent")
        awaitRemoved("RenderComponent")
        assertEquals("entity 7 is as before", entity7(before), entity7(Files.readString(scene)))

        // Point2Point: two entity references, an unknown entity and text are refused with the document unchanged
        val kind = "Point2PointPositionComponent"
        addFromTree(tree, "Point")
        awaitAdded(kind)
        LifecycleHarness.awaitState("the section is shown", true) { frame.properties().hasRemove(kind) }
        if (frame.properties().isCollapsed("7.$kind")) frame.properties().clickChevron("7.$kind")
        val added = Files.readString(scene)
        for (bad in listOf("999", "abc", "-1")) {
            frame.properties().enter("field-$kind-entity1Id", bad)
            LifecycleHarness.awaitState("'$bad' is explained", true) { frame.properties().errorText(kind, "entity1Id").isNotBlank() }
            assertEquals("a refused reference writes nothing", added, Files.readString(scene))
        }
        frame.properties().enter("field-$kind-entity1Id", "1")
        LifecycleHarness.awaitState("the first endpoint is saved", 1) { component(kind)?.get("entity1Id")?.asInt() }
        frame.properties().enter("field-$kind-entity2Id", "0")
        assertOnlyEntity7Changed(before, "Point2Point references")
        frame.properties().clickRemove(kind)
        awaitRemoved(kind)
        assertEquals("entity 7 is as before", entity7(before), entity7(Files.readString(scene)))
    }
}
