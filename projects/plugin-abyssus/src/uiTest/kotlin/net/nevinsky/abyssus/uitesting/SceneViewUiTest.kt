package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * The Scene View editor's toolbar: its controls and order, its layout when narrow, and its pixels. The container has no
 * usable OpenGL, so the view shows the unavailable message with the toolbar disabled above it; that is the state captured
 * (the tools' behavior is covered by the plugin tests). The 3D viewport is never captured.
 */
class SceneViewUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun openScene(): SceneViewToolbar {
        val path = project.resolve(scenePath).toString()
        frame.normalizeLayout(ide)
        ide.openSceneView(path)
        return frame.sceneToolbar().also { it.awaitShown() }
    }

    /** The scene must not stay open: its tool state would leak into the next test. */
    @After
    fun closeScene() {
        runCatching { ide.closeFile(project.resolve(scenePath).toString()) }
    }

    @Test
    fun theToolbarShowsTheMockupsControlsInOrder() {
        val original = Files.readString(project.resolve(scenePath))
        val toolbar = openScene()
        val tooltips = toolbar.buttonTooltips()
        val expected = listOf("Select (Q)", "Move (W)", "Rotate (E)", "Snap 0.5", "Shadows", "Fog", "Grid", "Frame selection (F)")
        assertEquals("controls in order: $tooltips", expected, tooltips.filter { it in expected })
        assertTrue("Move is the pressed tool", toolbar.isPressed("Move (W)"))
        assertFalse(toolbar.isPressed("Rotate (E)"))
        assertEquals("Move \u00b7 World", toolbar.status())
        // no OpenGL here: every control is disabled and nothing can be written
        assertTrue(expected.none { toolbar.isEnabled(it) })
        shot("scene-toolbar-default", toolbar.component, focusTree = false)
        assertEquals("opening the view writes nothing", original, Files.readString(project.resolve(scenePath)))
    }

    @Test
    fun theToolbarWrapsAtANarrowEditor() {
        val toolbar = openScene()
        ide.setToolWindowWidth("Project", IdeFrame.FRAME_WIDTH - 900)
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "the editor is about 900 px wide") {
            toolbar.component.callJs<Int>("component.getWidth()", runInEdt = true) < 1000
        }
        val boxes = toolbar.component.findAll<ComponentFixture>(byXpath("//div[contains(@class, 'ActionButton')]"))
            .map { it.callJs<String>("var p = component.getLocationOnScreen(); p.x + ',' + p.y + ',' + component.getWidth() + ',' + component.getHeight()", runInEdt = true) }
            .map { it.split(',').map(String::toInt) }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val (ax, ay, aw, ah) = boxes[i]
            val (bx, by, bw, bh) = boxes[j]
            val overlap = ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah
            assertFalse("buttons $i and $j overlap: ${boxes[i]} ${boxes[j]}", overlap)
        }
        shot("scene-toolbar-narrow", toolbar.component, focusTree = false)
    }
}
