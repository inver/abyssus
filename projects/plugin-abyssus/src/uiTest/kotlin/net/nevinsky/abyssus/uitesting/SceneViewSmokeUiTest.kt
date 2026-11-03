package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    /**
     * A real mouse click on the viewport that picks an entity: the Abyssus tree's selection follows it. The camera
     * frames the scene by default, so a coarse grid of clicks finds a model without knowing the projection.
     */
    private fun completeOnePick() {
        val tree = frame.selectAbyssusView()
        val before = tree.selectedRows()
        val canvas = frame.find<ComponentFixture>(byXpath("//div[contains(@javaclass, 'SceneGlLifecycle')]"), Duration.ofSeconds(10))
        val (w, h) = canvas.callJs<String>("component.getWidth() + ',' + component.getHeight()", runInEdt = true).split(',').map(String::toInt)
        val points = (1..7).flatMap { row -> (1..7).map { col -> java.awt.Point(w * col / 8, h * row / 8) } }
        val picked = points.firstNotNullOfOrNull { point ->
            canvas.click(point)
            runCatching { waitFor(Duration.ofMillis(1500), Duration.ofMillis(250), "the tree follows the pick") { tree.selectedRows() != before } }
            tree.selectedRows().takeIf { it != before && it.isNotEmpty() }
        }
        assertNotNull("a click on the viewport picked an entity (selection stayed ${before})", picked)
    }

    @Test
    fun theSceneViewKeepsAnOpenGlContext() {
        val original = Files.readString(project.resolve(scenePath))
        if (frame.propertiesWindow() != null) frame.closeProperties()
        frame.normalizeLayout(ide)
        ide.openSceneView(project.resolve(scenePath).toString())
        val toolbar = frame.sceneToolbar().also { it.awaitShown() }
        // the view tries its sample counts in turn, so the toolbar can be enabled for a moment before the last one fails:
        // settle on either the message or an enabled toolbar that stays enabled
        // (a lookup that fails while the canvas is being replaced counts as "not yet")
        fun moveEnabled() = runCatching { toolbar.isEnabled("Move (W)") }.getOrDefault(false)
        waitFor(Duration.ofSeconds(60), Duration.ofMillis(500), "the view settles on a context or the unavailable message") {
            unavailableMessage() != null || (moveEnabled().also { if (it) Thread.sleep(5000) } && moveEnabled())
        }
        val message = unavailableMessage()
        assumeTrue("environment-blocked, no OpenGL context here: $message", message == null)
        completeOnePick()
        assertEquals("opening and rendering the view writes nothing", original, Files.readString(project.resolve(scenePath)))
    }
}
