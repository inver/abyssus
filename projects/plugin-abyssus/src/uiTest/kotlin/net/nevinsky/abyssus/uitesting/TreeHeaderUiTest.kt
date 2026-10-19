package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** The Project tool window header of the Abyssus view: its buttons in order, Collapse All, and the unused filter. */
class TreeHeaderUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    @Test
    fun headerButtonsAreNamedAndOrdered() {
        frame.selectAbyssusView()
        val buttons = frame.headerButtons()
        val tail = buttons.map { it.first }.filter { it in listOf("Collapse All", "Options", "Hide") }
        assertEquals("header buttons $buttons", listOf("Collapse All", "Options", "Hide"), tail)
        frame.normalizeLayout(ide)
        shot("tree-header", frame.projectToolWindow)
    }

    @Test
    fun collapseAllFoldsTheTree() {
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        val before = tree.rows().size
        frame.clickHeaderButton("Collapse All")
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "tree collapsed") { frame.tree().rows().size < before }
    }

    @Test
    fun theUnusedFilterIsOfferedInTheOptionsMenuAndHidesUsedAssets() {
        val tree = frame.selectAbyssusView()
        tree.expandAssets()
        val all = tree.rows().size
        val item = "//*[contains(@text,'Show Only Unused Assets')]"
        fun toggleFilter() {
            frame.clickHeaderButton("Options")
            waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "filter in Options") { robot.findAll<ComponentFixture>(byXpath(item)).isNotEmpty() }
            robot.find<ComponentFixture>(byXpath(item)).click()
        }
        toggleFilter()
        try {
            waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "only unused assets listed") { frame.tree().rows().size < all }
            assertTrue(frame.tree().rowInfo().none { it.label == "Models" && it.text.endsWith(" 5") })
            frame.tree().expandAssets()
            frame.tree().clickRow("Untitled.abss")
            frame.normalizeLayout(ide)
            shot("tree-unused-filter", frame.projectToolWindow)
        } finally {
            toggleFilter()
        }
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "all assets counted again") { frame.tree().rows().any { it.startsWith("Assets 9") } }
    }
}
