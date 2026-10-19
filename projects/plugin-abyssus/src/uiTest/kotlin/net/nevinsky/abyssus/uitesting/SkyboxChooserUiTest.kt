package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

class SkyboxChooserUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val title = "Choose a skybox"

    private fun skyboxAssets(): List<String> = project.dir.resolve("assets").toFile().listFiles()!!
        .filter { it.isDirectory && Regex("\"type\"\\s*:\\s*\"SKYBOX").containsMatchIn(Files.readString(it.toPath().resolve("meta.json"))) }
        .map { it.name }.sorted()

    private fun openChooser(): IdeDialog {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(project.resolve(scenePath)).treeLabel)
        assertTrue("the tree row paints only its eye, no Choose button: ${tree.actionBounds("skybox:")}", tree.actionBounds("skybox:").size <= 1)
        tree.clickRow(SceneFacts.read(project.resolve(scenePath)).treeLabel)
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), "Browse button in Properties") { frame.properties().hasBrowseSkybox() }
        frame.properties().clickBrowseSkybox()
        return frame.dialog(title).awaitOpen()
    }

    private fun waitForTexts(dialog: IdeDialog, description: String, check: (List<String>) -> Boolean) {
        try {
            waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), description) { check(dialog.texts()) }
        } catch (e: Exception) {
            throw AssertionError("$description; dialog texts: ${dialog.texts()}", e)
        }
    }

    @Test
    fun chooserListsSkyboxesFiltersAndCancels() {
        val original = Files.readString(project.resolve(scenePath))
        val assets = skyboxAssets()
        assertEquals(3, assets.size)
        val dialog = openChooser()
        waitForTexts(dialog, "None and every skybox listed") { texts -> "None" in texts && assets.all { it in texts } }
        dialog.awaitThumbnailsLoaded()
        shot("skybox-chooser", dialog.component, focusTree = false)

        dialog.typeIntoFocus("hdr")
        waitForTexts(dialog, "filter hdr keeps only the HDR skybox") { texts ->
            "skybox_hdr" in texts && "skybox_default" !in texts && "skybox_physical" !in texts
        }
        dialog.awaitThumbnailsLoaded()
        shot("skybox-chooser-hdr", dialog.component, focusTree = false)

        dialog.typeIntoFocus("no such skybox")
        waitForTexts(dialog, "no-match state") { texts -> "No asset of this type matches the filter." in texts }
        shot("skybox-chooser-nomatch", dialog.component, focusTree = false)

        dialog.click("Cancel")
        dialog.awaitClosed()
        assertEquals(original, Files.readString(project.resolve(scenePath)))
    }

    @Test
    fun assignChangesOnlySkyboxNameAndUndoRestoresTheBytes() {
        val original = Files.readString(project.resolve(scenePath))
        val facts = SceneFacts(original)
        val target = "skybox_default"
        assertTrue(facts.skyboxName != target && facts.skyboxEnabled)

        val dialog = openChooser()
        dialog.typeIntoFocus("default")
        waitForTexts(dialog, "only $target remains") { texts -> target in texts && "skybox_hdr" !in texts }
        dialog.clickListRow(1) // row 0 is None
        dialog.click("Assign")
        dialog.awaitClosed()

        val expected = original.replaceFirst("\"skyboxName\": \"${facts.skyboxName}\"", "\"skyboxName\": \"$target\"")
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), "disk shows only skyboxName changed") {
            Files.readString(project.resolve(scenePath)) == expected
        }
        assertTrue(SceneFacts(expected).skyboxEnabled)
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), "tree shows the new skybox") {
            frame.tree().rows().any { it.contains("skybox: $target") }
        }

        undoThroughSceneDocument(project.resolve(scenePath), original)
    }
}
