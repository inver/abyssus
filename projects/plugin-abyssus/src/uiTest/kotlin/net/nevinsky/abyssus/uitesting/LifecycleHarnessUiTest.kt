package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Exercises the shared lifecycle helpers once end to end: a UI edit, Undo, Redo, an editor reopen and a project reopen,
 * each stage compared with disk and the document. The scenario families build on these helpers.
 */
class LifecycleHarnessUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun document() = ide.documentText(scene.toString())

    @Test
    fun editUndoRedoReopenEditorThenReopenProject() {
        val original = Files.readString(scene)
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")

        // edit through the UI: only the new entity is added
        tree.addComponentOnEcs("Camera")
        LifecycleHarness.awaitState("edit is saved", true) { Files.readString(scene).contains("\"Entity 11\"") }
        val edited = Files.readString(scene)
        assertTrue("only entity 11 was added", LifecycleHarness.sameExcept(original, edited, "/ecs/11"))
        assertEquals("the document agrees with the disk", edited, document())

        // undo, redo (through the scene document: undo is tied to it)
        undoThroughSceneDocument(scene, original)
        assertEquals("undo restored the exact text", original, Files.readString(scene))
        ide.openTextEditor(scene.toString())
        waitFor(Duration.ofSeconds(20), Duration.ofMillis(200), "the scene text editor has focus") { ide.editorHasFocus() }
        frame.redo()
        LifecycleHarness.awaitState("redo restores the edit", edited) { document() }
        frame.saveAll()
        LifecycleHarness.awaitState("redo is saved", edited) { Files.readString(scene) }

        // editor reopen
        reset.reopenTextEditor(scene.toString())
        LifecycleHarness.awaitState("the reopened editor shows the edit", edited) { document() }

        // project reopen: the same IDE process, a new project instance
        reset.reopenProject()
        LifecycleHarness.awaitState("the project is open again", true, Duration.ofSeconds(60)) {
            ide.openProjectPaths().any { it.endsWith(project.dir.fileName.toString()) }
        }
        LifecycleHarness.awaitState("the edit survived the project reopen", edited, Duration.ofSeconds(30)) {
            ide.documentText(scene.toString())
        }
        assertEquals(edited, Files.readString(scene))
    }
}
