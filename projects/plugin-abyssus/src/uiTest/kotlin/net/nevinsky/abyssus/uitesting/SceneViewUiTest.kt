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
        // an open Properties window (1250 px) would squeeze the editor to nothing
        if (frame.propertiesWindow() != null) frame.closeProperties()
        frame.normalizeLayout(ide)
        ide.openSceneView(path)
        val toolbar = frame.sceneToolbar().also { it.awaitShown() }
        waitFor(Duration.ofSeconds(20), Duration.ofMillis(300), "the editor is wide again") {
            toolbar.component.callJs<Int>("component.getWidth()", runInEdt = true) > 1100
        }
        return toolbar
    }

    /** The scene must not stay open: its tool state would leak into the next test. */
    @After
    fun closeScene() {
        runCatching { ide.closeFile(project.resolve(scenePath).toString()) }
        runCatching { ide.setToolWindowWidth("Project", IdeFrame.PROJECT_WIDTH) }
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
        // no OpenGL here: every control is disabled and nothing can be written
        assertTrue("enabled controls: ${expected.filter { toolbar.isEnabled(it) }}", expected.none { toolbar.isEnabled(it) })
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

    private fun editorTab(name: String) = frame.find<ComponentFixture>(byXpath("//div[@class='TabLabel' and @accessiblename='$name']"), Duration.ofSeconds(10))

    @Test
    fun theIdeTabsSwitchBetweenTextAndSceneViewWithoutWritingOnTheirOwn() {
        val path = project.resolve(scenePath).toString()
        val toolbar = openScene()
        // opened from the plugin's action: Scene View is the selected tab and Text is the other one
        editorTab("Scene View")
        editorTab("Text").click()
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), "the text editor shows the scene") { ide.editorHasFocus() || ide.documentText(path) != null }
        Thread.sleep(1500) // the text editor may format an unformatted scene once, as it always did
        val shown = ide.documentText(path)
        editorTab("Scene View").click()
        toolbar.awaitShown()
        assertEquals("switching back writes nothing", shown, ide.documentText(path))
        editorTab("Text").click()
        Thread.sleep(1500)
        assertEquals("switching again writes nothing", shown, ide.documentText(path))
        editorTab("Scene View").click()
        toolbar.awaitShown()
    }
}
