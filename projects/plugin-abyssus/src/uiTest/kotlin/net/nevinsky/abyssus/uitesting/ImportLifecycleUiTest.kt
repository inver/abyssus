package net.nevinsky.abyssus.uitesting

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
 * Model import (IMP-01..): the file chooser, the settings dialog and Create, each driven as a user does with the local inputs
 * of `import-inputs` (see its README). The source file is never touched, a cancelled or refused import writes nothing, and
 * Create is one undo step.
 */
class ImportLifecycleUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val assets: Path get() = project.dir.resolve("assets")
    private val title = "Import Model"
    private lateinit var inputs: ImportInputs

    @After
    fun closeDialogs() {
        runCatching { frame.closeStrayDialogs() }
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun stageInputs(): ImportInputs = ImportInputs.stage(Files.createTempDirectory("abyssus-import-inputs")).also { inputs = it }

    private fun assetsTree(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        LifecycleHarness.awaitState("the tree shows Assets", true) { tree.rows().any { it.startsWith("Assets") } }
        return tree
    }

    /** Right-click Assets, Import Model; the pick is supplied by the seam (see [IdeReset.chooseNextFile]). */
    private fun chooseModel(tree: AbyssusTree, file: Path) {
        reset.chooseNextFile(file.toString())
        // with a scene view open the software-rendered canvas can keep the UI thread busy for a while: ask again until it answers
        com.intellij.remoterobot.utils.waitFor(Duration.ofSeconds(60), Duration.ofSeconds(3), "the tree answers a right-click") {
            runCatching { tree.rightClickRow("Assets") }.isSuccess
        }
        frame.clickMenuPath("Import Model", "Import Model...")
    }

    private fun assetNames(): Set<String> = Files.list(assets).use { s -> s.map { it.fileName.toString() }.toList().toSet() }

    private fun directory(path: Path): Map<String, String> = Files.walk(path).use { w ->
        w.filter { Files.isRegularFile(it) }.toList().associate { path.relativize(it).toString() to ImportInputs.sha256(Files.readAllBytes(it)) }
    }

    /** Waits for the settings dialog the chooser opens next: it is the one with the folder name field. */
    private fun settingsDialog(): IdeDialog {
        val dialog = frame.dialog(title)
        LifecycleHarness.awaitState("the settings dialog is shown", "shown") {
            if (dialog.isOpen() && runCatching { dialog.isNamedEnabled("import-model-name") }.getOrDefault(false)) "shown"
            else "not yet; a dialog '$title' is open: ${dialog.isOpen()}, its texts ${runCatching { dialog.texts().take(25) }.getOrNull()}, its text field '${runCatching { dialog.textFieldText() }.getOrNull()}'"
        }
        return dialog
    }

    @Test
    fun cancellingTheChooserOrTheSettingsWritesNothingAndLeavesTheSourceAlone() {
        val files = stageInputs()
        val sourceBefore = directory(files.folder)
        val before = project.hashes()
        val tree = assetsTree()

        tree.rightClickRow("Assets")
        frame.clickMenuPath("Import Model", "Import Model...")
        val chooser = frame.dialog(title).awaitOpen()
        chooser.click("Cancel")
        chooser.awaitClosed()
        assertEquals("a cancelled chooser wrote nothing", before, project.hashes())

        chooseModel(tree, files.model)
        val settings = settingsDialog()
        assertTrue("Create is available for a readable model", settings.isButtonEnabled("Create"))
        settings.click("Cancel")
        settings.awaitClosed()
        assertEquals("a cancelled import wrote nothing", before, project.hashes())
        assertEquals("the source folder is untouched", sourceBefore, directory(files.folder))
        assertEquals("a cancelled import leaves no history", "", reset.historyText(project.resolve("scenes/Main Scene.scene").toString()))
    }

    @Test
    fun aNameThatIsTakenIsRefusedBeforeCreateAndNothingIsWritten() {
        val files = stageInputs()
        val before = assetNames()
        val hashes = project.hashes()
        val tree = assetsTree()

        chooseModel(tree, files.model)
        val settings = settingsDialog()
        val folder = settings.namedText("import-model-name")
        assertTrue("the suggested folder is free: $folder", folder.isNotBlank() && folder !in before)
        assertTrue("Create is available with it", settings.isButtonEnabled("Create"))

        // a folder that exists is refused with a reason
        settings.typeNamed("import-model-name", "tree")
        LifecycleHarness.awaitState("the collision is explained", true) { settings.namedText("import-model-problems").isNotBlank() }
        assertFalse("Create waits for a free name", settings.isButtonEnabled("Create"))
        settings.click("Cancel")
        settings.awaitClosed()
        assertEquals("a refused name wrote nothing", hashes, project.hashes())
    }

    @Test
    fun aFreeNameCreatesTheAssetFolderWithUndoRedoAndReopen() {
        val files = stageInputs()
        val sourceBefore = directory(files.folder)
        val before = assetNames()
        val tree = assetsTree()

        chooseModel(tree, files.model)
        val settings = settingsDialog()
        settings.typeNamed("import-model-name", "model_crate_copy")
        LifecycleHarness.awaitState("Create is available", "ok") {
            if (settings.isButtonEnabled("Create")) "ok" else "reason '${settings.namedText("import-model-problems")}', name '${settings.namedText("import-model-name")}'"
        }
        assertEquals("choosing and editing wrote nothing", before, assetNames())
        settings.click("Create")
        settings.awaitClosed()

        val created = assets.resolve("model_crate_copy")
        LifecycleHarness.awaitState("the asset folder exists", "created") {
            if (Files.exists(created.resolve("meta.json"))) "created"
            else "missing; assets ${assetNames()}, dialog texts ${runCatching { frame.dialog(title).texts().take(20) }.getOrNull()}, tree ${runCatching { tree.rows().filter { it.startsWith("model") } }.getOrNull()}"
        }
        val names = Files.walk(created).use { w -> w.filter { Files.isRegularFile(it) }.map { created.relativize(it).toString().replace('\\', '/') }.toList() }
        assertEquals(ImportInputs.STAGED_MODEL_FILES.toSet(), names.toSet())
        val meta = ObjectMapper().readTree(Files.readString(created.resolve("meta.json")))
        assertEquals("abyssus", meta["format"].asText())
        assertEquals("MODEL", meta["type"].asText())
        assertEquals(ImportInputs.STAGED_MODEL_ADDITIONAL, meta["additional"].toString())
        val snapshot = directory(created)
        assertEquals("the source is preserved byte for byte and nothing was written next to it", sourceBefore, directory(files.folder))
        assertEquals("only the new folder was added", before + "model_crate_copy", assetNames())

        // one Undo takes the whole folder back, one Redo returns the same bytes
        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo removes the folder", false) { Files.exists(created) }
        assertEquals(before, assetNames())
        tree.focus()
        frame.redo()
        LifecycleHarness.awaitState("Redo brings it back", true) { Files.exists(created.resolve("meta.json")) }
        assertEquals("with the same bytes", snapshot, directory(created))

        reset.reopenProject()
        assertEquals("the asset survives a reopen", snapshot, directory(created))
        val reopened = assetsTree()
        reopened.expandAssets()
        LifecycleHarness.awaitState("the reopened tree lists the model", true) { reopened.rows().any { it.startsWith("model_crate_copy") } }
    }

    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)

    private fun entityIds(): Set<String> = ObjectMapper().readTree(Files.readString(scene))["ecs"].fieldNames().asSequence().toSet()

    @Test
    fun addToSceneCreatesTheAssetAndTheEntityAsOneUndoStep() {
        val files = stageInputs()
        val sceneBefore = Files.readString(scene)
        val before = assetNames()
        val entitiesBefore = entityIds()
        val tree = assetsTree()
        ide.openSceneView(scene.toString())
        frame.sceneToolbar().awaitShown()
        Thread.sleep(5000)
        try {
            chooseModel(tree, files.model)
            val settings = settingsDialog()
            LifecycleHarness.awaitState("Add to scene is offered with the open scene view", true) { settings.isNamedEnabled("import-model-add-to-scene") }
            settings.typeNamed("import-model-name", "model_crate_placed")
            if (!settings.isNamedSelected("import-model-add-to-scene")) settings.clickNamed("import-model-add-to-scene")
            assertTrue("Add to scene is ticked", settings.isNamedSelected("import-model-add-to-scene"))
            LifecycleHarness.awaitState("Create is available", true) { settings.isButtonEnabled("Create") }
            assertEquals("the dialog wrote nothing", sceneBefore, Files.readString(scene))
            settings.click("Create")
            settings.awaitClosed()

            val created = assets.resolve("model_crate_placed")
            LifecycleHarness.awaitState("the asset folder exists", true) { Files.exists(created.resolve("meta.json")) }
            frame.saveAll()
            LifecycleHarness.awaitState("one entity was added", 1) { (entityIds() - entitiesBefore).size }
            val entity = (entityIds() - entitiesBefore).single()
            val render = ObjectMapper().readTree(Files.readString(scene))["ecs"][entity]["components"]["RenderComponent"]
            assertEquals("the entity places the new model", "model_crate_placed", render.findValue("assetName").asText())
            assertTrue("the other entities are untouched", LifecycleHarness.sameExcept(sceneBefore, Files.readString(scene), "/ecs/$entity"))
            val placed = Files.readString(scene)

            // one Undo takes the entity and the folder back together, one Redo returns both
            tree.focus()
            frame.undo()
            LifecycleHarness.awaitState("Undo removes the folder", false) { Files.exists(created) }
            frame.saveAll()
            LifecycleHarness.awaitState("and the entity", sceneBefore) { Files.readString(scene) }
            assertEquals(before, assetNames())
            tree.focus()
            frame.redo()
            LifecycleHarness.awaitState("Redo brings the folder back", true) { Files.exists(created.resolve("meta.json")) }
            frame.saveAll()
            LifecycleHarness.awaitState("and the entity", placed) { Files.readString(scene) }
        } finally {
            runCatching { ide.closeFile(scene.toString()) }
        }
    }

    @Test
    fun aFileThatIsNotAModelShowsWhyAndCannotBeCreated() {
        val files = stageInputs()
        val before = project.hashes()
        val tree = assetsTree()
        chooseModel(tree, files.brokenModel)
        // the file has a model extension but no model in it: the dialog opens without a way to Create, or refuses it at once
        val texts = { runCatching { frame.dialog(title).texts() }.getOrDefault(emptyList()) }
        LifecycleHarness.awaitState("the reason is shown", true) { texts().any { it.contains("Cannot", ignoreCase = true) || it.contains("unreadable", ignoreCase = true) || it.contains("not a model", ignoreCase = true) || it.contains("Could not", ignoreCase = true) } }
        runCatching { assertFalse("Create is not offered", frame.dialog(title).isButtonEnabled("Create")) }
        frame.closeStrayDialogs()
        assertEquals("nothing was written", before, project.hashes())
    }

    // ---- FlightGear aircraft archives

    private val flightGearTitle = "Import FlightGear Aircraft"

    private fun chooseArchive(tree: AbyssusTree, file: Path) {
        reset.chooseNextFile(file.toString())
        tree.rightClickRow("Assets")
        frame.clickMenuPath("Import Model", "Import FlightGear Aircraft...")
    }

    private fun flightGearDialog(): IdeDialog {
        val dialog = frame.dialog(flightGearTitle)
        LifecycleHarness.awaitState("the aircraft dialog is shown", "shown") {
            if (dialog.isOpen() && runCatching { dialog.isNamedEnabled("import-flightgear-name") }.getOrDefault(false)) "shown"
            else "not yet; open: ${dialog.isOpen()}, texts ${runCatching { dialog.texts().take(25) }.getOrNull()}"
        }
        return dialog
    }

    @Test
    fun cancellingTheAircraftDialogWritesNothingAndLeavesTheArchiveAlone() {
        val files = stageInputs()
        val archive = files.flightGearArchive
        val sourceBefore = directory(files.folder)
        val before = project.hashes()
        val tree = assetsTree()

        chooseArchive(tree, archive)
        val dialog = flightGearDialog()
        assertTrue("Create is available for the fixture plane", dialog.isButtonEnabled("Create"))
        assertTrue("the aircraft's licence is stated: ${dialog.namedText("import-flightgear-license")}", dialog.namedText("import-flightgear-license").isNotBlank())
        dialog.click("Cancel")
        dialog.awaitClosed()
        assertEquals("a cancelled import wrote nothing", before, project.hashes())
        assertEquals("the archive is untouched", sourceBefore, directory(files.folder))
    }

    @Test
    fun aFileThatIsNotAnArchiveIsRefusedWithTheReasonAndWritesNothing() {
        val files = stageInputs()
        val before = project.hashes()
        val tree = assetsTree()
        chooseArchive(tree, files.brokenArchive)
        LifecycleHarness.awaitState("the refusal is shown", "shown", Duration.ofSeconds(40)) {
            val texts = runCatching { frame.dialog(flightGearTitle).texts() }.getOrDefault(emptyList())
            if (texts.any { it.contains("Cannot import the aircraft") }) "shown"
            else "texts $texts, dialog open ${runCatching { frame.openDialogs(flightGearTitle) }.getOrNull()}, any dialog open ${runCatching { frame.openDialogs("Import Model") }.getOrNull()}"
        }
        frame.dialog(flightGearTitle).also { it.click("OK") }.awaitClosed()
        assertEquals("a corrupt archive wrote nothing", before, project.hashes())
    }

    @Test
    fun aircraftSettingsAreValidatedAndCreateWritesOneUndoableModelAssetKeepingTheArchive() {
        val files = stageInputs()
        val archive = files.flightGearArchive
        val archiveBytes = Files.readAllBytes(archive)
        val before = assetNames()
        val tree = assetsTree()

        chooseArchive(tree, archive)
        val dialog = flightGearDialog()
        val suggested = dialog.namedText("import-flightgear-name")
        assertTrue("a free folder name is suggested: $suggested", suggested.isNotBlank() && suggested !in before)

        dialog.typeNamed("import-flightgear-name", "tree")
        LifecycleHarness.awaitState("a taken name is explained", true) { dialog.namedText("import-flightgear-name-error").isNotBlank() }
        assertFalse("Create waits for a free name", dialog.isButtonEnabled("Create"))
        dialog.typeNamed("import-flightgear-name", "model_fixture_plane")
        LifecycleHarness.awaitState("a free name clears the reason", true) { dialog.namedText("import-flightgear-name-error").isBlank() }

        dialog.clickNamed("import-flightgear-scaled")
        for (bad in listOf("abc", "0", "-3")) {
            dialog.typeNamed("import-flightgear-span", bad)
            LifecycleHarness.awaitState("'$bad' as a wingspan is explained", true) { dialog.namedText("import-flightgear-name-error").isNotBlank() }
            assertFalse("Create waits for a valid wingspan", dialog.isButtonEnabled("Create"))
        }
        dialog.typeNamed("import-flightgear-span", "12")
        LifecycleHarness.awaitState("a valid wingspan clears the reason", true) { dialog.namedText("import-flightgear-name-error").isBlank() }
        LifecycleHarness.awaitState("Create is available", true) { dialog.isButtonEnabled("Create") }
        assertEquals("editing the settings wrote nothing", before, assetNames())
        dialog.click("Create")
        dialog.awaitClosed()

        val created = assets.resolve("model_fixture_plane")
        LifecycleHarness.awaitState("the asset folder exists", "created") {
            if (Files.exists(created.resolve("meta.json"))) "created"
            else "missing; dialogs: ${runCatching { frame.dialog(flightGearTitle).texts().take(20) }.getOrNull()}, assets ${assetNames()}"
        }
        val meta = ObjectMapper().readTree(Files.readString(created.resolve("meta.json")))
        assertEquals("abyssus", meta["format"].asText())
        assertEquals("MODEL", meta["type"].asText())
        assertTrue("the model file is written", Files.exists(created.resolve("model.glb")))
        assertTrue("the provenance is kept beside it", Files.exists(created.resolve("source.json")))
        assertEquals("the archive is untouched", archiveBytes.toList(), Files.readAllBytes(archive).toList())
        assertEquals("only the new folder was added", before + "model_fixture_plane", assetNames())
        val snapshot = directory(created)

        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo removes the folder", false) { Files.exists(created) }
        assertEquals(before, assetNames())
        tree.focus()
        frame.redo()
        LifecycleHarness.awaitState("Redo brings it back", true) { Files.exists(created.resolve("meta.json")) }
        assertEquals("with the same bytes", snapshot, directory(created))

        reset.reopenProject()
        assertEquals("the asset survives a reopen", snapshot, directory(created))
    }
}
