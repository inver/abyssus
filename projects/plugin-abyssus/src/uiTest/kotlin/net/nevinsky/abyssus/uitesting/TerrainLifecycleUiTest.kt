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
import java.security.MessageDigest
import java.time.Duration

/**
 * New Terrain and terrain regeneration (TER-01..06). The dialog is driven through its named editors (`new-terrain-name`,
 * `terrain-gen-<setting>`, `terrain-preview`, `terrain-message` ...) the way a user types into them; the written files
 * are compared with what the preview promised, and Undo/Redo are taken from the focused Abyssus tree.
 */
class TerrainLifecycleUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val assets: Path get() = project.dir.resolve("assets")
    private val dialogTitle = "New Terrain"
    private val mapper = ObjectMapper()

    @After
    fun closeDialogs() {
        runCatching { frame.closeStrayDialogs() }
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun assetNames(): Set<String> = Files.list(assets).use { s -> s.map { it.fileName.toString() }.toList().toSet() }

    private fun assetsTree(): AbyssusTree {
        runCatching { frame.reopenAbyssusView() }
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        LifecycleHarness.awaitState("the tree shows Assets", true) { tree.rows().any { it.startsWith("Assets") } }
        return tree
    }

    private fun openDialog(tree: AbyssusTree = assetsTree()): IdeDialog {
        tree.rightClickRow("Assets")
        frame.clickMenuPath("New", "New Terrain...")
        return frame.dialog(dialogTitle).awaitOpen()
    }

    /** A small terrain keeps the software-rendered preview quick. */
    private fun IdeDialog.geometry(size: String = "200", resolution: String = "32") {
        typeNamed("new-terrain-size", size)
        typeNamed("new-terrain-resolution", resolution)
    }

    private fun IdeDialog.awaitPreview() {
        clickNamed("terrain-preview")
        LifecycleHarness.awaitState("the preview finished", true) { namedText("terrain-message").contains("Preview ready") }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun json(path: Path): JsonNode = mapper.readTree(Files.readString(path))

    @Test
    fun aMatchingPreviewCreatesANativeTerrainWithUndoRedoAndReopen() {
        val before = assetNames()
        val tree = assetsTree()
        val dialog = openDialog(tree)
        assertFalse("Create waits for a preview", dialog.isButtonEnabled("Create"))
        dialog.typeNamed("new-terrain-name", "ridge")
        dialog.geometry()
        assertFalse("a name and geometry alone are not enough", dialog.isButtonEnabled("Create"))
        dialog.awaitPreview()
        assertTrue("a matching preview makes Create available", dialog.isButtonEnabled("Create"))
        assertEquals("a preview writes nothing", before, assetNames())
        dialog.click("Create")
        dialog.awaitClosed()
        // a refusal opens an error dialog with the same title
        runCatching { Thread.sleep(1500) }
        check(!frame.openDialogs(dialogTitle)) { "Create was refused: ${frame.dialog(dialogTitle).texts()}" }

        val dir = assets.resolve("ridge")
        LifecycleHarness.awaitState("the terrain folder exists", true) { Files.exists(dir.resolve("meta.json")) }
        val meta = json(dir.resolve("meta.json"))
        assertEquals("abyssus", meta["format"].asText())
        assertEquals(1, meta["formatVersion"].asInt())
        assertEquals("TERRAIN", meta["type"].asText())
        assertEquals(200, meta["additional"]["size"].asInt())
        val data = Files.readAllBytes(dir.resolve("terrain.data"))
        assertEquals("one big-endian float per sample of a 32 x 32 grid", 32 * 32 * 4, data.size)
        val recipe = json(dir.resolve("abyssus-terrain.recipe.json"))
        assertEquals("the recipe describes these heights", sha256(data), recipe["heightsSha256"].asText())
        assertEquals(32, recipe["resolution"].asInt())
        assertEquals(200, recipe["size"].asInt())
        val uuid = meta["uuid"].asText()
        val files = listOf("meta.json", "terrain.data", "abyssus-terrain.recipe.json").associateWith { Files.readAllBytes(dir.resolve(it)) }
        LifecycleHarness.awaitState("the new asset is selected in the tree", true) { tree.selectedRows().any { it.startsWith("ridge") } }

        // Undo removes the whole folder, Redo brings the same bytes back
        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo removes the folder", false) { Files.exists(dir) }
        assertEquals("nothing else changed", before, assetNames())
        tree.focus()
        frame.redo()
        LifecycleHarness.awaitState("Redo brings the folder back", true) { Files.exists(dir.resolve("terrain.data")) }
        for ((name, bytes) in files) assertTrue("$name has the same bytes after Redo", bytes.contentEquals(Files.readAllBytes(dir.resolve(name))))

        reset.reopenProject()
        assertEquals("the identity survives reopening", uuid, json(dir.resolve("meta.json"))["uuid"].asText())
        val reopened = assetsTree()
        reopened.expandAssets()
        LifecycleHarness.awaitState("the reopened tree shows the terrain", true) { reopened.rows().any { it.startsWith("ridge") } }
    }

    @Test
    fun changingAnInputOrTheSeedInvalidatesAPreviewButANameChangeDoesNot() {
        val dialog = openDialog()
        dialog.typeNamed("new-terrain-name", "dune")
        dialog.geometry()
        dialog.awaitPreview()
        assertTrue(dialog.isButtonEnabled("Create"))

        // a valid name-only change keeps the finished preview
        dialog.typeNamed("new-terrain-name", "dune2")
        assertTrue("renaming does not invalidate the heights", dialog.isButtonEnabled("Create"))

        // each generation setting, the geometry and the seed drop it
        val changes: List<Pair<String, () -> Unit>> = listOf(
            "the size" to { dialog.typeNamed("new-terrain-size", "300") },
            "the resolution" to { dialog.typeNamed("new-terrain-resolution", "40") },
            "the octaves" to { dialog.typeNamed("terrain-gen-octaves", "3") },
            "the feature size" to { dialog.typeNamed("terrain-gen-featureSize", "150") },
        )
        for ((what, change) in changes) {
            change()
            assertFalse("changing $what drops the preview", dialog.isButtonEnabled("Create"))
            assertTrue("and says a preview is needed", dialog.namedText("terrain-message").contains("preview"))
            dialog.awaitPreview()
            assertTrue("a new matching preview makes Create available again after $what", dialog.isButtonEnabled("Create"))
        }
        val seedBefore = dialog.namedText("terrain-gen-seed")
        dialog.clickNamed("terrain-randomize")
        assertTrue("randomizing changes the seed", dialog.namedText("terrain-gen-seed") != seedBefore)
        assertFalse("and drops the preview", dialog.isButtonEnabled("Create"))
        dialog.awaitPreview()
        assertTrue(dialog.isButtonEnabled("Create"))
        dialog.click("Cancel")
        dialog.awaitClosed()
    }

    @Test
    fun invalidNamesGeometryAndSettingsAreExplainedAndCancelWritesNothing() {
        val before = assetNames()
        val existing = before.first { Files.isDirectory(assets.resolve(it)) }
        val dialog = openDialog()
        dialog.geometry()
        dialog.typeNamed("new-terrain-name", "hill")
        dialog.awaitPreview()
        assertTrue(dialog.isButtonEnabled("Create"))

        dialog.typeNamed("new-terrain-name", existing)
        assertFalse("a taken folder name blocks Create", dialog.isButtonEnabled("Create"))
        assertTrue("with a reason: ${dialog.namedText("new-terrain-error")}", dialog.namedText("new-terrain-error").contains("already exists"))

        dialog.typeNamed("new-terrain-name", "bad/name")
        assertTrue("a name with a separator is refused: ${dialog.namedText("new-terrain-error")}", dialog.namedText("new-terrain-error").isNotBlank())
        dialog.typeNamed("new-terrain-name", "hill")
        assertEquals("", dialog.namedText("new-terrain-error"))

        dialog.typeNamed("new-terrain-size", "0")
        assertTrue("a zero size is refused: ${dialog.namedText("new-terrain-error")}", dialog.namedText("new-terrain-error").contains("world size"))
        assertFalse("and nothing can be previewed or created", dialog.isNamedEnabled("terrain-preview") || dialog.isButtonEnabled("Create"))
        dialog.typeNamed("new-terrain-size", "200")
        dialog.typeNamed("new-terrain-resolution", "1")
        assertTrue("a resolution below 2 is refused: ${dialog.namedText("new-terrain-error")}", dialog.namedText("new-terrain-error").contains("resolution"))
        dialog.typeNamed("new-terrain-resolution", "300")
        assertTrue("a resolution above 255 is refused", dialog.namedText("new-terrain-error").contains("resolution"))
        dialog.typeNamed("new-terrain-resolution", "32")

        // a value that parses but is out of range is explained and blocks previewing; a non-number is refused where it is typed
        dialog.typeNamed("terrain-gen-octaves", "99")
        assertTrue("an out-of-range setting is explained: ${dialog.namedText("terrain-message")}", dialog.namedText("terrain-message").contains("Octaves must be 1 to 8"))
        assertFalse("and nothing can be previewed or created", dialog.isNamedEnabled("terrain-preview") || dialog.isButtonEnabled("Create"))
        dialog.typeNamed("terrain-gen-octaves", "abc")
        dialog.typeNamed("terrain-gen-persistence", "abc")
        assertTrue("a non-number shows its reason: ${dialog.namedText("terrain-error-persistence")}", dialog.namedText("terrain-error-persistence").contains("number"))
        assertTrue("and the field returns to the last good value", dialog.namedText("terrain-gen-persistence") != "abc")
        dialog.typeNamed("terrain-gen-octaves", "4")

        dialog.click("Cancel")
        dialog.awaitClosed()
        assertEquals("cancelling writes nothing", before, assetNames())

        // closing a dialog with a finished preview discards it the same way
        val again = openDialog()
        again.geometry()
        again.typeNamed("new-terrain-name", "hill")
        again.awaitPreview()
        again.click("Cancel")
        again.awaitClosed()
        assertEquals("a cancelled preview wrote nothing", before, assetNames())
    }

    // ---- regeneration of the fixture terrain (TER-04..06) ----

    private val fixtureTerrain: String get() = assets.toFile().listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }
        .first { Regex("\"type\"\\s*:\\s*\"TERRAIN\"").containsMatchIn(Files.readString(it.toPath().resolve("meta.json"))) }.name
    private val terrainDir: Path get() = assets.resolve(fixtureTerrain)

    private fun openFixtureTerrain(): AbyssusTree {
        val tree = assetsTree()
        frame.openProperties()
        tree.expandAssets()
        tree.clickRow(fixtureTerrain)
        LifecycleHarness.awaitState("the regeneration controls are shown", true) { frame.properties().exists("terrain-preview") }
        return tree
    }

    private fun PropertiesPanel.awaitPreview() {
        click("terrain-preview")
        LifecycleHarness.awaitState("the preview finished", true) { labelText("terrain-message").contains("Preview ready") }
    }

    private fun bytesOf(vararg names: String) = names.associateWith { Files.readAllBytes(terrainDir.resolve(it)) }

    private fun sameBytes(expected: Map<String, ByteArray>) = expected.all { (n, b) -> b.contentEquals(Files.readAllBytes(terrainDir.resolve(n))) }

    @Test
    fun regeneratingTheFixtureTerrainChangesOnlyHeightsAndRecipeAndUndoRedoRestoreThem() {
        val files = listOf("meta.json", "terrain.data", "abyssus-terrain.recipe.json")
        val before = bytesOf(*files.toTypedArray())
        val tree = openFixtureTerrain()
        val p = frame.properties()
        assertFalse("Apply waits for a preview", p.isEnabled("terrain-apply"))

        p.enter("terrain-gen-octaves", "3")
        assertFalse("changing a setting keeps Apply unavailable", p.isEnabled("terrain-apply"))
        assertTrue("and nothing was written by editing", sameBytes(before))
        p.awaitPreview()
        assertTrue("a matching preview makes Apply available", p.isEnabled("terrain-apply"))
        assertTrue("a preview writes nothing", sameBytes(before))
        p.click("terrain-apply")
        LifecycleHarness.awaitState("Apply writes new heights", false) { before.getValue("terrain.data").contentEquals(Files.readAllBytes(terrainDir.resolve("terrain.data"))) }
        val applied = bytesOf(*files.toTypedArray())
        assertTrue("the metadata is untouched", before.getValue("meta.json").contentEquals(applied.getValue("meta.json")))
        assertEquals("the resolution is kept", before.getValue("terrain.data").size, applied.getValue("terrain.data").size)
        val recipe = json(terrainDir.resolve("abyssus-terrain.recipe.json"))
        assertEquals(3, recipe["settings"]["octaves"].asInt())
        assertEquals("the recipe describes the new heights", sha256(applied.getValue("terrain.data")), recipe["heightsSha256"].asText())

        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo restores the exact bytes", true) { sameBytes(before) }
        tree.focus()
        frame.redo()
        LifecycleHarness.awaitState("Redo reapplies them", true) { sameBytes(applied) }
    }

    @Test
    fun applyRefusesToOverwriteHeightsOrMetadataThatChangedAfterThePreview() {
        val tree = openFixtureTerrain()
        val p = frame.properties()
        p.enter("terrain-gen-octaves", "2")
        p.awaitPreview()
        assertTrue(p.isEnabled("terrain-apply"))

        // newer heights written outside the panel after the preview
        val newer = Files.readAllBytes(terrainDir.resolve("terrain.data")).also { it[7] = (it[7] + 1).toByte() }
        Files.write(terrainDir.resolve("terrain.data"), newer)
        ide.refresh(terrainDir.toString())
        p.click("terrain-apply")
        LifecycleHarness.awaitState("Apply is refused with a reason", true) {
            runCatching { frame.properties().labelText("terrain-message") }.getOrDefault("").let { it.contains("changed") }
        }
        assertTrue("the newer heights stay", newer.contentEquals(Files.readAllBytes(terrainDir.resolve("terrain.data"))))
        assertFalse("and Apply is unavailable until a new preview", frame.properties().isEnabled("terrain-apply"))
        frame.properties().awaitPreview()
        assertTrue("a preview of the changed terrain can be applied", frame.properties().isEnabled("terrain-apply"))

        // changed metadata after a preview is refused the same way and kept
        val meta = Files.readString(terrainDir.resolve("meta.json"))
        val edited = meta.replace("\"uv\": 60.0", "\"uv\": 61.0")
        assertTrue("the metadata edit was prepared", edited != meta)
        Files.writeString(terrainDir.resolve("meta.json"), edited)
        ide.refresh(terrainDir.toString())
        frame.properties().click("terrain-apply")
        LifecycleHarness.awaitState("Apply is refused after the metadata changed", true) {
            runCatching { frame.properties().labelText("terrain-message") }.getOrDefault("").let { it.contains("changed") }
        }
        assertEquals("the newer metadata stays", edited, Files.readString(terrainDir.resolve("meta.json")))
        assertTrue(newer.contentEquals(Files.readAllBytes(terrainDir.resolve("terrain.data"))))
        tree.focus()
    }

    @Test
    fun anUnusableGridOrAMalformedRecipeShowsADiagnosticOrDefaults() {
        // a malformed recipe: the settings fall back to defaults and the status says why
        Files.writeString(terrainDir.resolve("abyssus-terrain.recipe.json"), "{ not json")
        ide.refresh(terrainDir.toString())
        openFixtureTerrain()
        LifecycleHarness.awaitState("the status reports the recipe", true) { frame.properties().labelText("terrain-recipe-status").contains("cannot be read") }
        assertTrue("and still offers a preview of the defaults", frame.properties().isEnabled("terrain-preview"))

        // heights that are not a square grid cannot be regenerated: a note replaces the controls
        val data = terrainDir.resolve("terrain.data")
        val original = Files.readAllBytes(data)
        Files.write(data, original.copyOf(original.size - 4))
        ide.refresh(terrainDir.toString())
        val tree = assetsTree()
        tree.expandAssets()
        tree.clickRow("Scenes")
        tree.clickRow(fixtureTerrain)
        LifecycleHarness.awaitState("the unusable note is shown", true) { frame.properties().exists("terrain-unusable") }
        assertFalse("and there is nothing to apply", frame.properties().exists("terrain-apply"))
        assertTrue("the file is untouched", Files.readAllBytes(data).size == original.size - 4)
    }

    @Test
    fun aPreviewStartedBeforeTheSelectionChangedCannotBeAppliedToTheNewSelection() {
        val before = bytesOf("meta.json", "terrain.data", "abyssus-terrain.recipe.json")
        val tree = openFixtureTerrain()
        val p = frame.properties()
        p.enter("terrain-gen-octaves", "6")
        p.click("terrain-preview")
        // the selection leaves the terrain while its preview may still be running, and comes back to a fresh panel
        tree.clickRow("Scenes")
        tree.clickRow(fixtureTerrain)
        LifecycleHarness.awaitState("the terrain's controls are shown again", true) { frame.properties().exists("terrain-preview") }
        assertFalse("a superseded preview is not applicable to the new panel", frame.properties().isEnabled("terrain-apply"))
        assertTrue("and nothing was written", sameBytes(before))
    }
}
