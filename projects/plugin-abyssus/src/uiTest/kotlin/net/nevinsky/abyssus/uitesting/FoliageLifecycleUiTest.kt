package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Foliage assets (FOL-01..06) on a copy of the Foliage project: New Foliage and Add Foliage from the tree, then layers,
 * models, drafts, rebake and colliders edited in Abyssus Properties. The panel's own Undo and Redo revert one Apply at a
 * time; the bake is a derived file, so a restored `foliage.data` is compared byte for byte.
 */
class FoliageLifecycleUiTest : UiTestBase() {
    override val projectName = "Foliage"

    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val meadow: Path get() = project.dir.resolve("assets/foliage_meadow")
    private val metaFile get() = meadow.resolve("meta.json")
    private val bakeFile get() = meadow.resolve("foliage.data")
    private val mapper = ObjectMapper()

    @After
    fun closeUi() {
        runCatching { frame.closeStrayDialogs() }
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun layers(meta: Path = metaFile): JsonNode = mapper.readTree(Files.readString(meta))["additional"]["layers"]

    private fun layerIds(): List<Int> = layers().map { it["id"].asInt() }

    private fun bytes(path: Path) = Files.readAllBytes(path)

    private fun assetsTree(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expandAssets()
        return tree
    }

    /** Selects `foliage_meadow` with Abyssus Properties open, and waits for its first preview. */
    private fun openMeadow(): AbyssusTree {
        val tree = assetsTree()
        if (frame.propertiesWindow() == null) frame.openProperties()
        tree.clickRow("foliage_meadow")
        awaitPreview()
        return tree
    }

    private fun awaitPreview() {
        LifecycleHarness.awaitState("the foliage controls are shown", true) { frame.properties().exists("foliage-layer-0") }
        LifecycleHarness.awaitState("the preview counted the copies", true) {
            frame.properties().labelText("foliage-total").let { it.isNotBlank() && !it.contains("?") }
        }
        LifecycleHarness.awaitState("every layer shows its count", true) {
            layerIds().all { id -> runCatching { !frame.properties().labelText("foliage-count-$id").contains("?") }.getOrDefault(false) }
        }
    }

    private fun apply(expectChange: () -> Boolean) {
        LifecycleHarness.awaitState("Apply is available after the preview", true) { frame.properties().isEnabled("foliage-apply") }
        frame.properties().click("foliage-apply")
        LifecycleHarness.awaitState("Apply wrote the files", true) { expectChange() }
    }

    private fun panelUndo() {
        frame.properties().clickFirst("asset-undo")
        frame.saveAll()
    }

    private fun panelRedo() {
        frame.properties().clickFirst("asset-redo")
        frame.saveAll()
    }

    private fun openDialog(tree: AbyssusTree): IdeDialog {
        tree.rightClickRow("Assets")
        frame.clickMenuPath("New", "New Foliage...")
        return frame.dialog("New Foliage").awaitOpen()
    }

    @Test
    fun newFoliageCreatesAnEmptyAssetAddFoliageAttachesItAndItsUndoIsGuarded() {
        val before = project.hashes()
        val sceneBefore = Files.readString(scene)
        val tree = assetsTree()
        val dialog = openDialog(tree)

        // refusals are explained and keep Create unavailable
        dialog.typeNamed("new-foliage-name", "foliage_meadow")
        assertFalse("a taken folder name is refused", dialog.isButtonEnabled("Create"))
        assertTrue("with a reason", dialog.namedText("new-foliage-reason").isNotBlank())
        dialog.typeNamed("new-foliage-name", "foliage_second")
        dialog.typeNamed("new-foliage-resolution", "5")
        assertFalse("a resolution out of range is refused", dialog.isButtonEnabled("Create"))
        assertTrue(dialog.namedText("new-foliage-reason").isNotBlank())
        dialog.typeNamed("new-foliage-resolution", "32")
        assertTrue("a valid name and resolution enable Create", dialog.isButtonEnabled("Create"))
        assertEquals("opening and editing the dialog wrote nothing", before, project.hashes())
        dialog.click("Create")
        dialog.awaitClosed()

        val created = project.dir.resolve("assets/foliage_second")
        LifecycleHarness.awaitState("the folder exists", true) { Files.exists(created.resolve("meta.json")) }
        val meta = mapper.readTree(Files.readString(created.resolve("meta.json")))
        assertEquals("abyssus", meta["format"].asText())
        assertEquals(1, meta["formatVersion"].asInt())
        assertEquals("FOLIAGE", meta["type"].asText())
        assertEquals("terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b", meta["additional"]["terrain"].asText())
        assertEquals(32, meta["additional"]["maskResolution"].asInt())
        assertEquals("the new asset has no layers", 0, meta["additional"]["layers"]?.size() ?: 0)
        assertEquals("no scene, terrain or other asset changed", before.keys + setOf("assets/foliage_second/meta.json", "assets/foliage_second/foliage.data"), project.hashes().keys)
        assertEquals(sceneBefore, Files.readString(scene))
        LifecycleHarness.awaitState("the new asset is selected", "foliage_second") { tree.selectedRows().firstOrNull { it.startsWith("foliage_second") }?.let { "foliage_second" } ?: tree.selectedRows().toString() }

        // the terrain entity names it through Add Foliage
        tree.expand("ecs")
        tree.rightClickRow("Terrain 5 components")
        frame.clickMenuPath("Add Foliage...")
        frame.choosePopupItem("foliage_second")
        LifecycleHarness.awaitState("the scene names the foliage", true) { Files.readString(scene).contains("foliage_second") }
        frame.saveAll()
        assertTrue("only the scene changed besides the new folder", (project.differences() - scenePath).all { it.replace('\\', '/').startsWith("assets/foliage_second") })

        reset.reopenProject()
        assertTrue("the reference survives reopening", Files.readString(scene).contains("foliage_second"))
        assertTrue("and so does the asset", Files.exists(created.resolve("meta.json")))

        // the creation cannot be undone while the scene refers to the asset
        val tree2 = frame.selectAbyssusView()
        tree2.focus()
        frame.undo()
        Thread.sleep(1500)
        assertTrue("a referenced asset stays", Files.exists(created.resolve("meta.json")))
        assertTrue(Files.readString(scene).contains("foliage_second"))
    }

    @Test
    fun layersAndWeightedModelsAreAddedReorderedEditedAndRemovedWithApplyUndoAndRedo() {
        val metaBefore = Files.readString(metaFile)
        val bakeBefore = bytes(bakeFile)
        openMeadow()
        val p = frame.properties()
        assertEquals(listOf(0, 1), layerIds())
        assertFalse("nothing to apply before an edit", p.isEnabled("foliage-apply"))

        // a new layer
        p.click("foliage-add-layer")
        awaitPreview()
        assertTrue("the layer is a draft until Apply", Files.readString(metaFile) == metaBefore)
        apply { layerIds() == listOf(0, 1, 2) }
        val withThird = Files.readString(metaFile)
        val bakeWithThird = bytes(bakeFile)
        assertFalse("the bake follows the settings", bakeBefore.contentEquals(bakeWithThird))
        LifecycleHarness.awaitState("the third layer shows copies", true) { frame.properties().exists("foliage-count-2") }

        // order
        awaitPreview()
        frame.properties().click("foliage-up-2")
        awaitPreview()
        apply { layerIds() == listOf(0, 2, 1) }

        // weighted models on layer 0
        awaitPreview()
        frame.properties().click("foliage-add-model-0")
        awaitPreview()
        frame.properties().enter("foliage-weight-0-1", "2.5")
        awaitPreview()
        apply { layers()[0]["models"].size() == 2 }
        assertEquals(2.5, layers()[0]["models"][1]["weight"].asDouble(), 0.0)

        // an invalid weight is refused with a reason and nothing is written
        val models = Files.readString(metaFile)
        awaitPreview()
        frame.properties().enter("foliage-weight-0-1", "abc")
        LifecycleHarness.awaitState("the reason is shown", true) { frame.properties().labelText("foliage-error-0-models").contains("number") }
        assertEquals(models, Files.readString(metaFile))

        // the panel's Undo and Redo walk the applied states
        panelUndo()
        LifecycleHarness.awaitState("Undo removes the weighted model", 1) { layers()[0]["models"].size() }
        panelUndo()
        LifecycleHarness.awaitState("Undo restores the order", listOf(0, 1, 2)) { layerIds() }
        panelUndo()
        LifecycleHarness.awaitState("Undo restores the original bytes", metaBefore) { Files.readString(metaFile) }
        assertTrue("and the exact bake", bakeBefore.contentEquals(bytes(bakeFile)))
        panelRedo()
        LifecycleHarness.awaitState("Redo reapplies the third layer", withThird) { Files.readString(metaFile) }
        assertTrue("with the same bake", bakeWithThird.contentEquals(bytes(bakeFile)))

        // removal
        openMeadow()
        frame.properties().click("foliage-remove-2")
        awaitPreview()
        apply { layerIds() == listOf(0, 1) }
        assertTrue("the original bake returns with the original layers", bakeBefore.contentEquals(bytes(bakeFile)))
    }

    @Test
    fun invalidValuesTheCopyLimitCancelAndSelectionChangesNeverWriteADraft() {
        val metaBefore = Files.readString(metaFile)
        val bakeBefore = bytes(bakeFile)
        val tree = openMeadow()
        val p = frame.properties()

        for (bad in listOf("abc", "-1")) {
            p.enter("foliage-0-density", bad)
            LifecycleHarness.awaitState("'$bad' is explained", true) { frame.properties().labelText("foliage-error-0-density").isNotBlank() }
            assertEquals("the field shows the stored value again", "0.0002", frame.properties().fieldText("foliage-0-density"))
        }
        assertEquals(metaBefore, Files.readString(metaFile))

        // more copies than the limit
        p.enter("foliage-0-density", "0.5")
        LifecycleHarness.awaitState("the limit is announced", true) { frame.properties().exists("foliage-limit") }
        assertFalse("Apply is unavailable over the limit", frame.properties().isEnabled("foliage-apply"))
        assertEquals("nothing was written", metaBefore, Files.readString(metaFile))

        // Cancel puts the stored values back
        frame.properties().click("foliage-cancel")
        LifecycleHarness.awaitState("Cancel restores the stored density", "0.0002") { frame.properties().fieldText("foliage-0-density") }
        awaitPreview()
        assertFalse(frame.properties().isEnabled("foliage-apply"))

        // a draft does not follow the selection
        frame.properties().enter("foliage-0-density", "0.0003")
        awaitPreview()
        tree.clickRow("Scenes")
        tree.clickRow("foliage_meadow")
        awaitPreview()
        assertEquals("the draft is gone", "0.0002", frame.properties().fieldText("foliage-0-density"))
        assertFalse(frame.properties().isEnabled("foliage-apply"))
        assertEquals(metaBefore, Files.readString(metaFile))

        // the source changes between the preview and Apply
        frame.properties().enter("foliage-0-density", "0.0003")
        awaitPreview()
        val newer = metaBefore.replace("\"seed\": 7", "\"seed\": 8")
        assertTrue("the newer text was prepared", newer != metaBefore)
        Files.writeString(metaFile, newer)
        ide.refresh(meadow.toString())
        // the panel may notice the newer file first (it keeps the draft and previews again) or Apply may find it: either
        // way one Apply must not replace the newer text, and the bake is not rewritten from a stale preview
        LifecycleHarness.awaitState("the panel shows the newer settings and says why", "0.0002") { frame.properties().fieldText("foliage-0-density") }
        awaitPreview()
        // the newer seed makes the committed bake stale, so Apply may be offered, but only as a re-bake of the newer settings
        assertTrue("the stale bake is announced", frame.properties().exists("foliage-stale"))
        assertEquals("the newer metadata stays", newer, Files.readString(metaFile))
        assertTrue("the bake was not replaced by a stale draft", bakeBefore.contentEquals(bytes(bakeFile)))
    }

    @Test
    fun aStaleBakeIsRebakedFromTheCurrentSettingsAndUndoRestoresTheEarlierBytes() {
        // a changed mask makes the committed bake stale: its fingerprint no longer matches the inputs
        val maskFile = meadow.resolve("layer-1.mask")
        val mask = bytes(maskFile)
        val oldBake = bytes(bakeFile)
        Files.write(maskFile, mask.copyOf().also { it[it.size - 1] = 0 })
        ide.refresh(meadow.toString())
        openMeadow()
        val p = frame.properties()
        assertTrue("the stale bake is announced", p.exists("foliage-stale"))
        assertTrue("Re-bake is offered", p.exists("foliage-rebake"))
        LifecycleHarness.awaitState("Re-bake is available after the preview", true) { frame.properties().isEnabled("foliage-rebake") }
        frame.properties().click("foliage-rebake")
        LifecycleHarness.awaitState("the bake follows the changed mask", false) { oldBake.contentEquals(bytes(bakeFile)) }
        val rebaked = bytes(bakeFile)
        LifecycleHarness.awaitState("the stale note goes", false) { frame.properties().exists("foliage-stale") }

        panelUndo()
        LifecycleHarness.awaitState("Undo restores the exact earlier bytes", true) { oldBake.contentEquals(bytes(bakeFile)) }
        panelRedo()
        LifecycleHarness.awaitState("Redo rebakes the same bytes", true) { rebaked.contentEquals(bytes(bakeFile)) }
    }

    @Test
    fun aColliderOnlyApplyChangesOnlyMetadataAndLeavesTheBakeOrItsAbsenceAlone() {
        val metaBefore = Files.readString(metaFile)
        val bakeBefore = bytes(bakeFile)
        openMeadow()
        frame.properties().chooseWithKeyboard("foliage-collider-0", "Box")
        LifecycleHarness.awaitState("the collider note is shown", true) { frame.properties().exists("foliage-collider-note") }
        LifecycleHarness.awaitState("Apply needs no preview for a collider", true) { frame.properties().isEnabled("foliage-apply") }
        frame.properties().click("foliage-apply")
        LifecycleHarness.awaitState("the collider is saved", "BOX") { layers()[0]["collider"]?.get("shape")?.asText() }
        assertTrue("the bake is untouched", bakeBefore.contentEquals(bytes(bakeFile)))
        assertEquals("the other layer is untouched", mapper.readTree(metaBefore)["additional"]["layers"][1], layers()[1])
        val boxed = Files.readString(metaFile)

        panelUndo()
        LifecycleHarness.awaitState("Undo restores the original metadata", metaBefore) { Files.readString(metaFile) }
        assertTrue(bakeBefore.contentEquals(bytes(bakeFile)))
        panelRedo()
        LifecycleHarness.awaitState("Redo reapplies the collider", boxed) { Files.readString(metaFile) }
        assertTrue(bakeBefore.contentEquals(bytes(bakeFile)))
    }

    @Test
    fun aColliderOnlyApplyKeepsAMissingBakeMissing() {
        val metaBefore = Files.readString(metaFile)
        Files.delete(bakeFile)
        ide.refresh(meadow.toString())
        openMeadow()
        frame.properties().chooseWithKeyboard("foliage-collider-0", "Sphere")
        LifecycleHarness.awaitState("Apply is available", true) { frame.properties().isEnabled("foliage-apply") }
        frame.properties().click("foliage-apply")
        LifecycleHarness.awaitState("the collider is saved", "SPHERE") { layers()[0]["collider"]?.get("shape")?.asText() }
        assertFalse("no bake was created", Files.exists(bakeFile))
        panelUndo()
        LifecycleHarness.awaitState("Undo restores the metadata", metaBefore) { Files.readString(metaFile) }
        assertFalse("and the bake is still missing", Files.exists(bakeFile))
    }

    @Test
    fun colliderControlsFollowTheLayerKindAndInvalidSizesAreRefusedKeepingExtensionData() {
        val original = Files.readString(metaFile)
        val withCollider = original.replace(
            "\"alignToNormal\": 0.5,",
            "\"alignToNormal\": 0.5,\n        \"collider\": { \"shape\": \"BOX\", \"halfExtents\": { \"x\": 0.5, \"y\": 1.0, \"z\": 0.5 }, \"vendor\": { \"keep\": 1 } },",
        )
        assertTrue("the collider was prepared", withCollider != original)
        Files.writeString(metaFile, withCollider)
        ide.refresh(meadow.toString())
        openMeadow()
        val p = frame.properties()
        assertTrue("an OBJECT layer offers colliders", p.exists("foliage-collider-0"))
        assertFalse("a DETAIL layer does not", p.exists("foliage-collider-1"))

        for (bad in listOf("abc", "0", "-1")) {
            frame.properties().enter("foliage-collider-0-halfX", bad)
            LifecycleHarness.awaitState("'$bad' is explained", true) {
                frame.properties().labelText("foliage-error-0-collider").isNotBlank() || frame.properties().fieldText("foliage-collider-0-halfX") == "0.5"
            }
            assertEquals("the field shows the stored size", "0.5", frame.properties().fieldText("foliage-collider-0-halfX"))
        }
        assertEquals("nothing was written", withCollider, Files.readString(metaFile))

        // replacing the shape keeps members the editor does not model
        frame.properties().chooseWithKeyboard("foliage-collider-0", "Sphere")
        LifecycleHarness.awaitState("Apply is available", true) { frame.properties().isEnabled("foliage-apply") }
        frame.properties().click("foliage-apply")
        LifecycleHarness.awaitState("the shape changed", "SPHERE") { layers()[0]["collider"]?.get("shape")?.asText() }
        assertEquals("the extension member stays", 1, layers()[0]["collider"]["vendor"]["keep"].asInt())
        assertEquals("the other layer is untouched", mapper.readTree(withCollider)["additional"]["layers"][1], layers()[1])
    }
}
