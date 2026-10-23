package net.nevinsky.abyssus.uitesting

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * New Weather Preset from Sky (IMP-05): a snapshot of a procedural sky's cloud source becomes a new CLOUDS asset, leaves
 * the sky and cloud files alone, and Undo/Redo bring the same bytes back. The project gets a prepared cloud source and a
 * sky that references it before the IDE reads them.
 */
class WeatherPresetLifecycleUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val assets get() = project.resolve("assets")
    private val skyMeta get() = assets.resolve("skybox_physical/meta.json")
    private val cloudsId = "00000000-0000-0000-0000-0000000000c1"
    private val dialogTitle = "New Weather Preset from Sky"

    @After
    fun closeDialogs() {
        runCatching { frame.closeStrayDialogs() }
    }

    private fun prepareSource(reference: String = cloudsId) {
        val clouds = assets.resolve("clouds_main")
        Files.createDirectories(clouds)
        Files.writeString(
            clouds.resolve("meta.json"),
            """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"uuid":"$cloudsId","type":"CLOUDS","additional":{"low":{"level":"LOW","type":"CUMULUS"}}}""" + "\n",
        )
        val sky = SceneJson().parseObject(Files.readString(skyMeta))
        (sky.get("additional") as com.fasterxml.jackson.databind.node.ObjectNode).put("clouds", reference)
        Files.writeString(skyMeta, SceneJson().pretty(sky))
        ide.refresh(project.dir.toString())
        ide.refreshProjectView()
    }

    private fun openSkyRow(): AbyssusTree {
        runCatching { frame.reopenAbyssusView() }
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        LifecycleHarness.awaitState("the tree shows Assets", true) { tree.rows().any { it.startsWith("Assets") } }
        tree.expandAssets()
        LifecycleHarness.awaitState("the sky row is shown", true) { tree.rows().any { it == "skybox_physical" || it.startsWith("skybox_physical ") } }
        return tree
    }

    /** Opens the sky row's context menu and picks the action; the tree is rebuilt and the menu asked for again when the action is missing. */
    private fun chooseAction(first: AbyssusTree) {
        var tree = first
        repeat(3) { attempt ->
            tree.rightClickRow(tree.rows().first { it == "skybox_physical" || it.startsWith("skybox_physical ") })
            val found = runCatching { frame.clickMenuItem("New Weather Preset from Sky...") }.isSuccess
            if (found) return
            frame.dismissPopup()
            ide.refresh(project.dir.toString())
            ide.refreshProjectView()
            runCatching { frame.reopenAbyssusView() }
            tree = openSkyRow()
        }
        frame.clickMenuItem("New Weather Preset from Sky...")
    }

    private fun openDialog(tree: AbyssusTree): IdeDialog {
        chooseAction(tree)
        val dialog = frame.dialog(dialogTitle).awaitOpen()
        val texts = dialog.texts()
        check(texts.none { it.contains("Cannot create the weather preset") }) { "the source was refused: $texts" }
        return dialog
    }

    private fun presetDir() = assets.resolve("weather_skybox_physical")

    @Test
    fun aSnapshotIsCreatedLeavesTheSourcesAloneAndRedoKeepsItsBytes() {
        prepareSource()
        val skyBefore = Files.readAllBytes(skyMeta)
        val cloudsBefore = Files.readAllBytes(assets.resolve("clouds_main/meta.json"))
        val tree = openSkyRow()
        val dialog = openDialog(tree)
        dialog.click("Create")
        dialog.awaitClosed()
        LifecycleHarness.awaitState("the preset folder exists", true) { Files.exists(presetDir().resolve("meta.json")) }
        val created = Files.readAllBytes(presetDir().resolve("meta.json"))
        val meta = SceneJson().parseObject(String(created))
        assertEquals("CLOUDS", meta.get("type").asText())
        assertTrue("the snapshot has its own identity", meta.get("uuid").asText() != cloudsId)
        assertTrue("the source sky is byte-identical", skyBefore.contentEquals(Files.readAllBytes(skyMeta)))
        assertTrue("the source clouds are byte-identical", cloudsBefore.contentEquals(Files.readAllBytes(assets.resolve("clouds_main/meta.json"))))
        LifecycleHarness.awaitState("the new asset has a row", true) { tree.rows().any { it.startsWith("weather_skybox_physical") } }

        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo removes the new asset folder", false) { Files.exists(presetDir()) }
        tree.focus()
        frame.redo()
        LifecycleHarness.awaitState("Redo brings the folder back", true) { Files.exists(presetDir().resolve("meta.json")) }
        assertTrue("with the same identity, timestamp and bytes", created.contentEquals(Files.readAllBytes(presetDir().resolve("meta.json"))))
        assertTrue("the sky is still untouched", skyBefore.contentEquals(Files.readAllBytes(skyMeta)))
    }

    @Test
    fun cancelAndANameCollisionCreateNothing() {
        prepareSource()
        val tree = openSkyRow()
        val first = openDialog(tree)
        first.click("Cancel")
        first.awaitClosed()
        assertFalse("cancel creates nothing", Files.exists(presetDir()))

        Files.createDirectories(presetDir())
        ide.refresh(project.dir.toString())
        ide.refreshProjectView()
        val second = openDialog(openSkyRow())
        assertFalse("Create is unavailable while the folder name is taken", second.isButtonEnabled("Create"))
        second.click("Cancel")
        second.awaitClosed()
        assertTrue("the existing folder is untouched", Files.isDirectory(presetDir()) && Files.list(presetDir()).use { it.count() } == 0L)
    }

    @Test
    fun aMissingCloudSourceIsRefusedWithAReasonAndNothingIsCreated() {
        prepareSource(reference = "00000000-0000-0000-0000-00000000dead")
        val tree = openSkyRow()
        chooseAction(tree)
        val refusal = frame.dialog(dialogTitle).awaitOpen()
        assertTrue("the refusal gives a reason: ${refusal.texts()}", refusal.texts().joinToString(" ").contains("Cannot create the weather preset"))
        refusal.click("OK")
        refusal.awaitClosed()
        assertFalse("no preset folder was left behind", Files.exists(presetDir()))
    }
}
