package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * The tree eye and the section `Enabled` box are one action (spec `abyssus-project-view`, `object-properties-panel`). The
 * Scene View cannot draw in the container (no OpenGL), so that a disabled entity is not picked is covered by plugin tests.
 */
class PropertiesEnabledUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val scenePath = "scenes/Main Scene.scene"
    private fun scene() = SceneFacts.read(project.resolve(scenePath))

    private fun tree(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.openProperties()
        tree.expand("Scenes")
        tree.expand(scene().treeLabel)
        tree.expand("ecs")
        frame.normalizeLayout(ide)
        return tree
    }

    private fun await(description: String, check: () -> Boolean) = waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), description) { check() }

    private fun disk() = Files.readString(project.resolve(scenePath))

    @Test
    fun theSectionBoxWritesTheFlagAndOneUndoRestoresTheBytes() {
        val original = disk()
        val name = scene().entityName(0)!!
        tree().clickRow(name)
        await("entity header") { frame.properties().subtitle().startsWith("Entity (0) ·") }
        assertTrue(frame.properties().enabledChecked("0.RenderComponent"))
        frame.properties().clickEnabled("0.RenderComponent")
        await("flag on disk") { disk().contains("\"enabled\": false") }
        assertEquals(1, Regex("\"enabled\"").findAll(disk()).count())
        await("box follows") { !frame.properties().enabledChecked("0.RenderComponent") }
        undoThroughSceneDocument(project.resolve(scenePath), original)
        assertEquals(original, disk())
    }

    @Test
    fun theTreeEyeAndTheSectionBoxStayInSync() {
        val tree = tree()
        val name = scene().entityName(0)!!
        tree.expand(name)
        tree.clickRowEye(name)
        await("entity flag on disk") { disk().contains("\"enabled\": false") }
        tree.clickRow(name)
        await("entity header") { frame.properties().subtitle().startsWith("Entity (0) ·") }
        // the entity is off; its components keep their own box state
        assertTrue(frame.properties().enabledChecked("0.RenderComponent"))
        assertFalse(frame.properties().hasEnabledBox("0.NameComponent"))
        tree.clickRowEye(name)
        await("flag removed") { !disk().contains("\"enabled\"") }
    }
}
