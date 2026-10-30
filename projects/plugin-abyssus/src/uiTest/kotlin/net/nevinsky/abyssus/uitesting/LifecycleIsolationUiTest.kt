package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * DOC-05: a scenario that created content and left an editor open with unsaved text must not leak into the next one.
 * The reset closes editors, restores the files and drops stale buffers; this proves it with the real IDE.
 */
class LifecycleIsolationUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun assertPristine(stage: String, original: String) {
        assertEquals("$stage: scene on disk", original, Files.readString(scene))
        assertEquals("$stage: scene document", original, ide.documentText(scene.toString()) ?: original)
        assertEquals("$stage: unsaved documents", emptyList<String>(), reset.unsavedDocuments())
        assertEquals("$stage: open editors", emptyList<String>(), ide.openEditorFiles())
        assertFalse("$stage: the scene's editor has nothing to undo", reset.undoAvailableInTextEditor(scene.toString()))
        assertTrue("$stage: project differs from the fixture in ${project.differences()}", project.differences().isEmpty())
    }

    @Test
    fun aSecondScenarioStartsPristineAfterContentAndAnOpenEditor() {
        val original = Files.readString(scene)
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")

        tree.addComponentOnEcs("Camera")
        LifecycleHarness.awaitState("the new camera entity is saved", true) { Files.readString(scene).contains("\"Entity 11\"") }

        // leave the scene open as text with unsaved typing in it
        ide.openTextEditor(scene.toString())
        waitFor(Duration.ofSeconds(20), Duration.ofMillis(200), "the scene text editor has focus") { ide.editorHasFocus() }
        frame.keyboard { enterText(" ") }
        LifecycleHarness.awaitState("the typing is unsaved", true) { scene.toString() in reset.unsavedDocuments() }
        assertTrue("the scene's editor has history to undo before the reset", reset.undoAvailableInTextEditor(scene.toString()).also { ide.openTextEditor(scene.toString()) })

        resetScenario()

        assertPristine("after reset", original)
        val rows = frame.selectAbyssusView().also {
            it.expand("Scenes"); it.expand(SceneFacts.read(scene).treeLabel); it.expand("ecs")
        }.rows()
        assertFalse("the created entity must be gone from the tree: $rows", rows.any { it.startsWith("Entity 11") })
    }

    @Test
    fun everyScenarioStartsPristine() {
        assertPristine("at start", Files.readString(scene))
    }
}
