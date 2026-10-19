/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import java.io.File
import net.nevinsky.abyssus.plugin.filetype.TreeIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tree's glyphs under `icons/tree/`: every listed icon exists and every file there is listed. */
class TreeIconInventoryTest {
    /** Hue glyphs that read the same in both themes: one file each. */
    private val neutral = listOf(
        "project", "folder_scenes", "folder_ui", "folder_assets", "scene", "ui_screen", "ambient_light", "skybox", "ecs",
        "entity_object", "entity_group", "entity_light_directional", "entity_terrain",
        "asset_model", "asset_terrain", "asset_foliage", "asset_clouds", "asset_texture", "asset_skybox_procedural",
        "asset_ui", "asset_shader",
        "component_position", "component_render", "component_rigid_body", "component_collider", "component_constraint",
        "component_light", "component_foliage", "component_path_arrangement", "component_plane", "component_pilot",
        "eye_on", "eye_off", "action_unused_filter_selected",
    )

    /** Gray and text-colored glyphs: a light file and a `_dark` twin. */
    private val themed = listOf(
        "folder", "fog", "component_name", "component_parent",
        "action_unused_filter", "action_browse",
    )

    private fun resource(name: String) = TreeIcons::class.java.getResource("/icons/tree/$name")

    @Test
    fun everyListedIconHasItsFiles() {
        for (name in neutral + themed) assertNotNull("missing icon resource icons/tree/$name.svg", resource("$name.svg"))
        for (name in themed) assertNotNull("missing icon resource icons/tree/${name}_dark.svg", resource("${name}_dark.svg"))
    }

    @Test
    fun noTreeIconFileIsUnlisted() {
        val dir = File("src/main/resources/icons/tree")
        assertTrue("icons/tree folder not found from ${File(".").absolutePath}", dir.isDirectory)
        val listed = neutral.map { "$it.svg" } + themed.flatMap { listOf("$it.svg", "${it}_dark.svg") }
        val onDisk = dir.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals("orphaned tree icon files: ${onDisk - listed.toSet()}", emptySet<String>(), onDisk - listed.toSet())
    }

    @Test
    fun aDarkVariantDiffersFromItsLightFile() {
        for (name in themed) {
            val light = checkNotNull(resource("$name.svg")).readText()
            val dark = checkNotNull(resource("${name}_dark.svg")).readText()
            assertTrue("$name dark variant equals the light one", light != dark)
        }
    }
}
