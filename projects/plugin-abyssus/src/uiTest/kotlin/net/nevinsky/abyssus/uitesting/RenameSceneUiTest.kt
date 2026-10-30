package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

class RenameSceneUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val title = "Rename Scene"

    private fun openRenameDialog(sceneLabel: String): IdeDialog {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.rightClickRow(sceneLabel)
        frame.clickMenuItem("Rename")
        return frame.dialog(title).awaitOpen()
    }

    @Test
    fun cancelKeepsTheSceneByteIdentical() {
        val original = Files.readString(project.resolve(scenePath))
        val facts = SceneFacts(original)
        val dialog = openRenameDialog(facts.treeLabel)
        assertEquals(facts.name, dialog.textFieldText())
        shot("rename-scene-dialog", dialog.component, focusTree = false)
        dialog.type("Something else")
        dialog.click("Cancel")
        dialog.awaitClosed()
        assertEquals(original, Files.readString(project.resolve(scenePath)))
        assertTrue(frame.tree().rows().contains(facts.treeLabel))
    }

    @Test
    fun renameChangesOnlyTheNameAndUndoRestoresTheBytes() {
        val original = Files.readString(project.resolve(scenePath))
        val facts = SceneFacts(original)
        val newName = "Renamed Scene"
        assertEquals(1, Regex("\"name\": \"${facts.name}\"").findAll(original).count())

        val dialog = openRenameDialog(facts.treeLabel)
        dialog.type(newName)
        dialog.click("OK")
        dialog.awaitClosed()

        val expected = original.replace("\"name\": \"${facts.name}\"", "\"name\": \"$newName\"")
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), "disk shows only the scene name changed") {
            Files.readString(project.resolve(scenePath)) == expected
        }
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), "tree shows the new name") {
            "$newName (${facts.id})" in frame.tree().rows()
        }

        undoThroughSceneDocument(project.resolve(scenePath), original)
    }
}
