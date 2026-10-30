package net.nevinsky.abyssus.uitesting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class TreeStructureUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun scene() = SceneFacts.read(project.resolve(scenePath))


    @Test
    fun projectExpandsToScenesUiAndAssetsInThatOrderWithoutANameRow() {
        val tree = frame.selectAbyssusView()
        tree.expand("Untitled.abss")
        val rows = tree.rowInfo()
        assertEquals(0, rows.first().depth)
        val children = rows.drop(1).takeWhile { it.depth >= 1 }.filter { it.depth == 1 }
        assertEquals(rows.map { it.text }.toString(), listOf("Scenes", "UI", "Assets"), children.map { it.label })
        assertTrue("no name row in ${rows.map { it.text }}", rows.none { it.text.startsWith("name") })
        frame.normalizeLayout(ide)
        tree.clickRow("Untitled.abss")
        shot("tree-collapsed", frame.projectToolWindow)
    }

    @Test
    fun assetsAreGroupedByTypeWithCountsAndEveryAssetAppearsOnce() {
        val tree = frame.selectAbyssusView()
        tree.expandAssets()
        val rows = tree.rowInfo()
        val assets = rows.first { it.label == "Assets" }
        val assetCount = project.dir.resolve("assets").toFile().listFiles()!!.count { it.isDirectory }
        assertEquals("Assets $assetCount", assets.text)
        val groups = rows.filter { it.depth == assets.depth + 1 && rows.indexOf(it) > rows.indexOf(assets) }
        assertEquals(rows.map { it.text }.toString(), listOf("Models", "Terrains", "Skyboxes"), groups.map { it.label })
        assertEquals(assetCount, groups.sumOf { it.text.substringAfterLast(" ").toInt() })
        project.dir.resolve("assets").toFile().listFiles()!!.filter { it.isDirectory }.forEach { dir ->
            assertEquals("asset ${dir.name} once in ${rows.map { it.text }}", 1, rows.count { it.label == dir.name })
        }
        frame.normalizeLayout(ide)
        tree.clickRow("Untitled.abss")
        shot("tree-assets-groups", frame.projectToolWindow)
        assertTrue("assets sit under their group", rows.filter { it.depth == assets.depth + 2 }.all { it.iconName.startsWith("asset_") || it.iconName.startsWith("skybox") })
    }

    @Test
    fun sceneEcsAndEntityRowsAreAsBefore() {
        val facts = scene()
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        tree.expand(facts.treeLabel)
        tree.expand("ecs")
        val rows = tree.rows()
        assertTrue("scene row ${facts.treeLabel} in $rows", facts.treeLabel in rows)
        assertTrue("ecs row in $rows", rows.any { it.startsWith("ecs") })
        facts.entityName(0)?.let { name -> assertTrue("entity $name in $rows", rows.any { it.startsWith(name) }) }
        assertTrue("skybox row in $rows", rows.any { it.startsWith("skybox:") })
    }

    @Test
    fun switchingViewsDoesNotDuplicateRows() {
        val tree = frame.selectAbyssusView()
        tree.expandAssets()
        val first = tree.rows()
        frame.selectView("Project")
        val again = frame.selectAbyssusView()
        again.expandAssets()
        val rows = again.rows()
        assertEquals(first, rows)
        assertEquals(1, rows.count { it.startsWith("Assets") })
        assertFalse(rows.isEmpty())
    }
}
