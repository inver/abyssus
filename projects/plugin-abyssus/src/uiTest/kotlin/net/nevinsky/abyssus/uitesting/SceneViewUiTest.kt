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
 * The Scene View editor's toolbar: its controls and order, its layout when narrow, its pixels, the view-only toggles and
 * the shared Show FPS preference. The container renders through Mesa software GL, so the view has a context and the toolbar
 * is enabled; where no context exists the cases that need one are skipped (never passed). The 3D viewport is never captured.
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
        ViewportDriver(frame, ide).awaitContext(toolbar)
        val always = listOf("Select (Q)", "Move (W)", "Rotate (E)", "Snap 0.5", "Grid", "Frame selection (F)")
        assertTrue("disabled controls: ${always.filterNot { toolbar.isEnabled(it) }}", always.all { toolbar.isEnabled(it) })
        shot("scene-toolbar-default", toolbar.component, focusTree = false)
        assertEquals("opening the view writes nothing", original, Files.readString(project.resolve(scenePath)))
    }

    @Test
    fun theToolbarWrapsAtANarrowEditor() {
        val toolbar = openScene()
        ViewportDriver(frame, ide).awaitContext(toolbar)
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

    private val viewport get() = ViewportDriver(frame, ide)

    private fun pressed(toolbar: SceneViewToolbar, vararg names: String) = names.associateWith { toolbar.isPressed(it) }

    private fun comboShows(toolbar: SceneViewToolbar, text: String) = runCatching {
        toolbar.component.findAll<ComponentFixture>(byXpath("//div[@class='ComboBoxButton' and @visible_text='$text']")).isNotEmpty()
    }.getOrDefault(false)

    /** Chooses [item] in the drop-down showing [current], matching the popup's rows by contained text and naming them on failure. */
    private fun choose(toolbar: SceneViewToolbar, current: String, item: String) {
        var seen = emptyList<String>()
        repeat(4) {
            try {
                toolbar.component.find<ComponentFixture>(byXpath("//div[@class='ComboBoxButton' and @visible_text='$current']"), Duration.ofSeconds(10)).click()
                val list = frame.find<com.intellij.remoterobot.fixtures.JListFixture>(
                    byXpath("//div[@class='HeavyWeightWindow']//div[@class='MyList' or @class='JBList']"), Duration.ofSeconds(10),
                )
                seen = list.collectItems()
                // a narrow popup truncates its rows ("Wo..."): match by what is left of the text
                val index = seen.indexOfFirst { row -> row.removeSuffix("...").let { it.isNotEmpty() && (item.startsWith(it) || it.startsWith(item)) } }
                require(index >= 0) { "no row '$item' in $seen" }
                list.clickItemAtIndex(index)
                return
            } catch (e: Exception) {
                Thread.sleep(700)
                runCatching { viewport.press(java.awt.event.KeyEvent.VK_ESCAPE) }
            }
        }
        error("no row '$item' in the '$current' drop-down; its rows: $seen")
    }

    private fun until(what: String, condition: () -> Boolean) =
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(200), what) { runCatching(condition).getOrDefault(false) }

    @After
    fun resetShowFps() {
        runCatching { reset.clearShowFps() }
    }

    @Test
    fun viewOnlyTogglesFollowTheirStateAndWriteNothing() {
        val original = Files.readString(project.resolve(scenePath))
        val abss = project.resolve("Untitled.abss")
        val abssBefore = Files.readString(abss)
        val toolbar = openScene()
        viewport.awaitContext(toolbar)
        val toggles = listOf("Shadows", "Fog", "Grid", "Snap 0.5")
        val initial = pressed(toolbar, *toggles.toTypedArray())
        for (name in toggles.filter { toolbar.isEnabled(it) }) {
            toolbar.click(name)
            until("$name flips") { toolbar.isPressed(name) != initial.getValue(name) }
            toolbar.click(name)
            until("$name returns") { toolbar.isPressed(name) == initial.getValue(name) }
        }
        assertEquals("every toggle is back in its initial state", initial, pressed(toolbar, *toggles.toTypedArray()))

        choose(toolbar, "World", "Local")
        until("the Local space is shown") { comboShows(toolbar, "Local") }
        until("the stats strip names the space") { viewport.stats().contains("Local") }
        choose(toolbar, "Local", "World")
        until("World is back") { comboShows(toolbar, "World") && viewport.stats().contains("World") }

        choose(toolbar, "Lit", "Unlit")
        until("Unlit is shown") { comboShows(toolbar, "Unlit") }
        choose(toolbar, "Unlit", "Lit")
        until("Lit is back") { comboShows(toolbar, "Lit") }

        toolbar.click("Snap 0.5")
        until("the stats strip shows the snap step") { viewport.stats().contains("Snap") }
        toolbar.click("Snap 0.5")
        until("the stats strip drops the snap") { !viewport.stats().contains("Snap") }

        frame.saveAll()
        assertEquals("view-only toggles write nothing", original, Files.readString(project.resolve(scenePath)))
        assertEquals("nor to the project file", abssBefore, Files.readString(abss))
    }

    /** Ticks or clears Show FPS in the project details of Abyssus Properties (the project row selected). */
    private fun setShowFps(on: Boolean) {
        val tree = frame.selectAbyssusView()
        until("the project row is shown") { tree.rows().any { it.startsWith("Untitled.abss") } }
        tree.clickRow("Untitled.abss")
        frame.openProperties()
        val box = frame.find<ComponentFixture>(byXpath("//div[@class='JBCheckBox' and @text='Show FPS']"), Duration.ofSeconds(10))
        if (box.callJs<Boolean>("component.isSelected()", runInEdt = true) != on) box.click()
        until("the Show FPS box shows $on") { box.callJs<Boolean>("component.isSelected()", runInEdt = true) == on }
        frame.closeProperties()
    }

    /** Whether Show FPS is ticked in the project details; [thenSet] then ticks or clears it in the same visit. */
    private fun showFpsChecked(thenSet: Boolean? = null): Boolean {
        val tree = frame.selectAbyssusView()
        until("the project row is shown after the reopen") { tree.rows().any { it.startsWith("Untitled.abss") } }
        // a reopened project may already have the project row selected, which raises no selection change: move off it first
        runCatching { tree.clickRow("Scenes") }
        tree.clickRow("Untitled.abss")
        frame.openProperties()
        val box = frame.find<ComponentFixture>(byXpath("//div[@class='JBCheckBox' and @text='Show FPS']"), Duration.ofSeconds(10))
        val was = box.callJs<Boolean>("component.isSelected()", runInEdt = true)
        if (thenSet != null && thenSet != was) {
            // the box is only clickable once the freshly opened Properties window has been laid out
            until("the Show FPS box is showing") { box.callJs<Boolean>("component.isShowing()", runInEdt = true) }
            box.click()
        }
        return was.also { frame.closeProperties() }
    }

    @Test
    fun showFpsIsOneProjectPreferenceThatSurvivesHidingAndReopeningAndWritesNoFile() {
        val original = Files.readString(project.resolve(scenePath))
        val abss = project.resolve("Untitled.abss")
        val abssBefore = Files.readString(abss)
        val toolbar = openScene()
        viewport.awaitContext(toolbar)
        assertFalse("the frame rate is off by default (${viewport.stats()})", viewport.stats().contains(" fps"))

        setShowFps(true)
        until("the open view shows the frame rate") { viewport.stats().contains(" fps") }
        // hidden and shown: the Text tab hides the view, the Scene View tab shows it again
        editorTab("Text").click()
        Thread.sleep(1000)
        editorTab("Scene View").click()
        toolbar.awaitShown()
        until("resumed sampling shows the frame rate again") { viewport.stats().contains(" fps") }

        // a view opened later follows the preference
        ide.closeFile(project.resolve(scenePath).toString())
        val again = openScene()
        viewport.awaitContext(again)
        until("a view opened later shows the frame rate") { viewport.stats().contains(" fps") }

        frame.saveAll()
        assertEquals("the preference is not stored in the scene", original, Files.readString(project.resolve(scenePath)))
        assertEquals("nor in the project file", abssBefore, Files.readString(abss))

        // a project reopen keeps it
        ide.closeFile(project.resolve(scenePath).toString())
        reset.reopenProject()
        until("the project is shown again") { frame.findAll<ComponentFixture>(byXpath("//div[@class='EyeTree']")).isNotEmpty() || ide.openProjectPaths().isNotEmpty() }
        // retained: a view opened in the reopened project follows the preference, and sampling starts afresh
        val reopened = openScene()
        viewport.awaitContext(reopened)
        until("the reopened project's view shows the frame rate") { viewport.stats().contains(" fps") }
        // the @After puts the preference back to off for the next scenario
    }
}
