package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/** Painted states: selection, a disabled row and its children, an unused asset, and the `game` badge. */
class TreeStatesUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun scene() = SceneFacts.read(project.resolve(scenePath))

    private fun open(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        tree.expand(scene().treeLabel)
        tree.expandAssets()
        return tree
    }

    @Test
    fun selectedRowIsFlaggedSelected() {
        val tree = open()
        tree.clickRow("Assets")
        val rows = tree.rowInfo()
        assertEquals(listOf("Assets"), rows.filter { it.selected }.map { it.label })
    }

    @Test
    fun disabledFogRowTurnsGrayWithTheCrossedEye() {
        val tree = open()
        val enabled = tree.rowInfo("fog")
        assertEquals("on", tree.eye("fog"))
        tree.clickRowEye("fog")
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), "fog row disabled") { tree.eye("fog") == "off" }
        val disabled = frame.tree().rowInfo("fog")
        assertNotEquals("text colour of a disabled row differs from an enabled one", enabled.textColor, disabled.textColor)
        frame.tree().clickRow("fog")
        frame.normalizeLayout(ide)
        shot("tree-selected-disabled", frame.projectToolWindow)
    }

    @Test
    fun unusedAssetShowsTheAmberMarkAndAGrayRow() {
        val tree = open()
        val unused = tree.rowInfo().filter { r -> r.label.startsWith("model_") || r.label.startsWith("skybox_") || r.label == "tree" }
            .filter { r -> tree.actionBounds(r.text).containsKey("unused") }
        assertTrue("an unused asset in ${tree.rows()}", unused.isNotEmpty())
        val normal = tree.rowInfo().first { it.label == "Models" }
        unused.forEach { assertNotEquals("${it.text} is dimmed", normal.textColor, it.textColor) }
    }

    @Test
    fun gameComponentRowShowsTheGameBadge() {
        val original = Files.readString(project.resolve(scenePath))
        val plane = original.replaceFirst("\"NameComponent\"", "\"PlaneComponent\": {}, \"NameComponent\"")
        assertNotEquals(original, plane)
        Files.writeString(project.resolve(scenePath), plane)
        ide.refresh(project.dir.toString())
        frame.reopenAbyssusView()
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        tree.expand(scene().treeLabel)
        tree.expand("ecs")
        tree.expand(scene().entityName(0)!!)
        val row = tree.rowInfo("Plane")
        assertTrue("game badge on ${row.text}: ${row.fragments}", row.fragments.any { it.trim() == "game" })
        assertTrue("no badge on a built-in component", tree.rowInfo("Name").fragments.none { it.trim() == "game" })
    }
}
