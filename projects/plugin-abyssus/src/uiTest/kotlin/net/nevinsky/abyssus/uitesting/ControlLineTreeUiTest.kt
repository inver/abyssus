package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * The redesigned tree on the Control Line project: 186 entities, 51 assets in six type groups, a Game UI asset and the
 * game's own components. Runs only when the IDE opened a copy of that project:
 * `ABYSSUS_UI_FIXTURE=projects/app-game-control-line/project/ControlLine scripts/run-ui-tests-docker.sh --tests '*ControlLineTreeUiTest'`.
 */
class ControlLineTreeUiTest : UiTestBase() {
    override val projectName = "ControlLine"

    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val assetDirs get() = project.dir.resolve("assets").toFile().listFiles()!!.filter { it.isDirectory }

    /** The `type` of every asset folder, from its meta.json. */
    private fun assetTypes(): Map<String, String> = assetDirs.associate { dir ->
        dir.name to SceneJson().parseObject(Files.readString(dir.toPath().resolve("meta.json"))).get("type").asText()
    }

    private fun openScene(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        tree.expand("Field (0)")
        return tree
    }

    @Test
    fun projectExpandsToScenesUiAndAssetsAndUiListsItsScreens() {
        val tree = frame.selectAbyssusView()
        tree.expand("ControlLine.abss")
        tree.expand("UI")
        val rows = tree.rowInfo()
        assertEquals(rows.map { it.text }.toString(), listOf("Scenes", "UI", "Assets"), rows.filter { it.depth == 1 }.map { it.label })
        val ui = rows.first { it.label == "UI" }
        assertTrue("the screens asset under UI in ${rows.map { it.text }}", rows.any { it.label == "screens" && it.depth == ui.depth + 1 })
        assertTrue("no name row", rows.filter { it.depth <= 1 }.none { it.text.startsWith("name") })
    }

    @Test
    fun assetsFormSixGroupsWithCountsAndNoUiAssetAmongThem() {
        val tree = frame.selectAbyssusView()
        tree.expandAssets()
        val rows = tree.rowInfo()
        val assets = rows.first { it.label == "Assets" }
        val types = assetTypes().filterValues { it != "UI" }
        assertEquals("Assets ${types.size}", assets.text)
        val groups = rows.drop(rows.indexOf(assets) + 1).takeWhile { it.depth > assets.depth }.filter { it.depth == assets.depth + 1 }
        assertEquals(
            rows.map { it.text }.toString(),
            listOf("Models", "Terrains", "Foliage", "Textures", "Skyboxes", "Clouds"), groups.map { it.label },
        )
        val expected = mapOf("Models" to "MODEL", "Terrains" to "TERRAIN", "Foliage" to "FOLIAGE", "Textures" to "TEXTURE", "Skyboxes" to "SKYBOX_PROCEDURAL", "Clouds" to "CLOUDS")
        groups.forEach { g ->
            assertEquals("count of ${g.label}", types.values.count { it.startsWith(expected.getValue(g.label)) }, g.text.substringAfterLast(" ").toInt())
        }
        types.keys.forEach { name -> assertEquals("asset $name once", 1, rows.count { it.label == name }) }
        frame.normalizeLayout(ide)
        tree.clickRow("ControlLine.abss")
        shot("controlline-tree-assets", frame.projectToolWindow)
    }

    @Test
    fun theSceneListsItsEntitiesAndEveryRowKeepsTheGridGeometry() {
        val tree = openScene()
        tree.expand("ecs")
        val rows = tree.rowInfo()
        listOf("Pilot", "Racer", "Stunter", "Trainer").forEach { name ->
            assertTrue("$name in the entity rows", rows.any { it.label == name })
        }
        assertTrue("a scene with 186 entities (copies grouped) fills the tree: ${rows.size} rows", rows.size > 100)
        val (indent, height) = tree.metrics()
        assertEquals(JBUI16, indent)
        assertEquals(24, height)
        val levels = rows.map { it.depth }.toSet()
        assertTrue("entities sit one level below ecs in $levels", levels.containsAll(listOf(0, 1, 2, 3, 4)))
    }

    @Test
    fun gameComponentsCarryTheGameBadgeAndBuiltInOnesDoNot() {
        val tree = openScene()
        tree.expand("ecs")
        tree.expand("Racer")
        tree.expand("Pilot")
        val rows = tree.rowInfo()
        val plane = rows.first { it.label == "Plane" }
        val pilot = rows.last { it.label == "Pilot" }
        assertTrue("game badge on ${plane.text}: ${plane.fragments}", plane.fragments.any { it.trim() == "game" })
        assertTrue("game badge on ${pilot.text}: ${pilot.fragments}", pilot.fragments.any { it.trim() == "game" })
        val builtIn = rows.filter { it.label == "Name" || it.label == "Position" || it.label == "Collider" }
        assertTrue("built-in component rows in ${rows.map { it.text }}", builtIn.isNotEmpty())
        builtIn.forEach { assertTrue("no badge on ${it.text}", it.fragments.none { f -> f.trim() == "game" }) }
        frame.normalizeLayout(ide)
        tree.clickRow("Racer")
        shot("controlline-tree-game", frame.projectToolWindow)
    }

    @Test
    fun anAssetTheSceneRendersIsNotUnusedAndTheUnusedFilterKeepsOnlyUnusedOnes() {
        val tree = frame.selectAbyssusView()
        tree.expandAssets()
        val used = tree.actionBounds("model_racer")
        assertFalse("model_racer is rendered by the scene: $used", used.containsKey("unused"))
        val unused = tree.rowInfo().filter { it.depth >= 3 }.map { it.label }.filter { tree.actionBounds(it).containsKey("unused") }
        assertTrue("some asset of the project is unused", unused.isNotEmpty())
        toggleUnusedFilter()
        try {
            val filtered = frame.tree().also { it.expandAssets() }.rowInfo().filter { it.depth >= 3 }.map { it.label }
            assertFalse("model_racer is filtered out: $filtered", "model_racer" in filtered)
            assertTrue("every unused asset stays: $unused vs $filtered", filtered.containsAll(unused))
        } finally {
            toggleUnusedFilter()
        }
    }

    private fun toggleUnusedFilter() {
        val item = "//*[contains(@text,'Show Only Unused Assets')]"
        frame.clickHeaderButton("Options")
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "filter in Options") { robot.findAll<ComponentFixture>(byXpath(item)).isNotEmpty() }
        robot.find<ComponentFixture>(byXpath(item)).click()
    }

    private companion object {
        const val JBUI16 = 16
    }
}
