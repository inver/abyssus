package net.nevinsky.abyssus.uitesting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** The icon resource of each row kind equals the inventory's, and kinds the inventory keeps distinct do not share one. */
class TreeIconsUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun open(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(project.resolve(scenePath)).treeLabel)
        tree.expand("ecs")
        tree.expandAssets()
        return tree
    }

    private fun expect(rows: List<TreeRowInfo>, label: String, icon: String) {
        val row = rows.firstOrNull { it.label == label || it.text == label } ?: throw AssertionError("no row '$label' in ${rows.map { it.text }}")
        assertEquals("icon of row '$label'", icon, row.iconName)
    }

    @Test
    fun foldersGroupsAndSceneRowsUseTheirInventoryIcons() {
        val tree = open()
        val rows = tree.rowInfo()
        expect(rows, "Untitled.abss", "project")
        expect(rows, "Scenes", "folder_scenes")
        expect(rows, "UI", "folder_ui")
        expect(rows, "Assets", "folder_assets")
        for (group in listOf("Models", "Terrains", "Skyboxes")) expect(rows, group, "folder")
        expect(rows, SceneFacts.read(project.resolve(scenePath)).treeLabel, "scene")
        expect(rows, "ecs", "ecs")
        expect(rows, "skybox: " + SceneFacts.read(project.resolve(scenePath)).skyboxName, "skybox")
    }

    @Test
    fun assetRowsUseTheirTypeIcon() {
        val rows = open().rowInfo()
        val byAsset = mapOf(
            "skybox_default" to "asset_skybox_icon", "skybox_hdr" to "asset_skybox_hdr_icon",
            "skybox_physical" to "asset_skybox_procedural", "tree" to "asset_model",
        )
        byAsset.forEach { (asset, icon) -> assertEquals("icon of $asset", icon, rows.first { it.label == asset }.iconName) }
        assertTrue(rows.filter { it.label.startsWith("model_") }.all { it.iconName == "asset_model" })
        assertTrue(rows.filter { it.label.startsWith("terrain_") }.all { it.iconName == "asset_terrain" })
    }

    @Test
    fun distinctKindsDoNotShareAnIcon() {
        val rows = open().rowInfo()
        val kinds = listOf("Scenes", "UI", "Assets", "ecs", "Untitled.abss").map { k -> k to rows.first { it.label == k }.iconName }
        assertEquals(kinds.toString(), kinds.size, kinds.map { it.second }.toSet().size)
        val assetIcons = listOf("tree", "skybox_physical", "skybox_hdr").map { a -> a to rows.first { it.label == a }.iconName }
        assertEquals(assetIcons.toString(), assetIcons.size, assetIcons.map { it.second }.toSet().size)
    }
}
