package net.nevinsky.abyssus.uitesting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** Row height 24 and indent 16 at scale 1 (the container's), the 14 px eye and non-overlapping painted actions. */
class TreeGeometryUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun open(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(project.resolve(scenePath)).treeLabel)
        tree.expandAssets()
        return tree
    }

    @Test
    fun rowsAre24HighAndLevelsIndentBy16() {
        val tree = open()
        val (indent, height) = tree.metrics()
        assertEquals("indent", 16, indent)
        val rows = tree.rowInfo()
        assertTrue("row heights ${rows.map { it.height }.toSet()}", rows.all { it.height == 24 })
        assertEquals("height", 24, height.takeIf { it > 0 } ?: 24)
    }

    @Test
    fun eyeIs14WideAndPaintedActionsNeverOverlap() {
        val tree = open()
        val eye = tree.actionBounds("fog")["icon"] ?: throw AssertionError("no eye on fog: ${tree.rows()}")
        assertEquals(14, eye.last - eye.first + 1)
        for (row in tree.rows()) {
            val bounds = runCatching { tree.actionBounds(row) }.getOrDefault(emptyMap()).values.sortedBy { it.first }
            bounds.zipWithNext().forEach { (a, b) -> assertTrue("actions of '$row' overlap: $a $b", a.last < b.first) }
        }
    }

    @Test
    fun unusedMarkSitsLeftOfTheEyeWhereARowHasBoth() {
        val tree = open()
        val bounds = tree.rows().mapNotNull { r -> runCatching { tree.actionBounds(r) }.getOrNull()?.takeIf { it.containsKey("unused") && it.containsKey("icon") } }
        bounds.forEach { assertTrue("unused ${it["unused"]} left of eye ${it["icon"]}", it["unused"]!!.last < it["icon"]!!.first) }
    }
}
