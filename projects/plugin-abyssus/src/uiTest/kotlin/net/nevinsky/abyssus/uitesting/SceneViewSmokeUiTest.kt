package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Whether the canvas of the Scene View got an OpenGL context in this environment. Every toolbar and gesture scenario
 * depends on it. Without a context the view shows its `scene-unavailable` message and this test is skipped as
 * environment-blocked, so the scenarios that need GL are never reported as covered (design: "The canonical
 * environment must render a scene"). With one, the toolbar must stay enabled and opening the view must write nothing.
 */
class SceneViewSmokeUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    @After
    fun closeScene() {
        runCatching { ide.closeFile(project.resolve(scenePath).toString()) }
        runCatching { ide.setToolWindowWidth("Project", IdeFrame.PROJECT_WIDTH) }
    }

    private fun unavailableMessage(): String? =
        frame.findAll<ComponentFixture>(byXpath("//div[@name='scene-unavailable']")).firstOrNull()
            ?.callJs<String>("component.getText()", runInEdt = true)

    @Test
    fun theSceneViewKeepsAnOpenGlContext() {
        val original = Files.readString(project.resolve(scenePath))
        if (frame.propertiesWindow() != null) frame.closeProperties()
        frame.normalizeLayout(ide)
        ide.openSceneView(project.resolve(scenePath).toString())
        val toolbar = frame.sceneToolbar().also { it.awaitShown() }
        // the view tries its sample counts in turn, so the toolbar can be enabled for a moment before the last one fails:
        // settle on either the message or an enabled toolbar that stays enabled
        waitFor(Duration.ofSeconds(60), Duration.ofMillis(500), "the view settles on a context or the unavailable message") {
            unavailableMessage() != null || (toolbar.isEnabled("Move (W)").also { if (it) Thread.sleep(5000) } && toolbar.isEnabled("Move (W)"))
        }
        val message = unavailableMessage()
        assumeTrue("environment-blocked, no OpenGL context here: $message", message == null)
        assertEquals("opening and rendering the view writes nothing", original, Files.readString(project.resolve(scenePath)))
    }
}
