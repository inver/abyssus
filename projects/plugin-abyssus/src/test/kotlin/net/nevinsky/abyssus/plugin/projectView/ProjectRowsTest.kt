/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.editor.testAsset
import net.nevinsky.abyssus.plugin.dto.ProjectDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRowsTest {
    private fun project(scenes: List<Any> = emptyList(), vararg assets: Pair<String, String>) =
        ProjectDto("P", scenes, assets.map { (name, type) -> testAsset(name, name, type) })

    private fun groups(project: ProjectDto): List<DtoRow> =
        folderRows(projectRows(project).single { it.name == "assets" }.value as FolderRow)

    @Test
    fun foldersComeInOrderAndTheNameIsNotARow() {
        assertEquals(listOf("scenes", "ui", "assets"), projectRows(project()).map { it.name })
    }

    @Test
    fun scenesKeepTheirList() {
        val rows = projectRows(project(scenes = listOf("a", "b", "c")))
        assertEquals(3, (rows[0].value as List<*>).size)
    }

    @Test
    fun assetsGroupByTypeAndSkyboxesShareAGroup() {
        val p = project(emptyList(), "m1" to "MODEL", "m2" to "MODEL", "m3" to "MODEL", "t" to "TERRAIN", "s" to "SKYBOX_PROCEDURAL")
        val rows = groups(p)
        assertEquals(listOf("MODEL", "TERRAIN", "SKYBOX"), rows.map { it.name })
        assertEquals(listOf(3, 1, 1), rows.map { (it.value as TypeGroup).assets.size })
        assertEquals(listOf("Models", "Terrains", "Skyboxes"), rows.map { groupLabel(it.name) })
    }

    @Test
    fun uiAssetsLiveUnderUiNotAssets() {
        val p = project(emptyList(), "hud" to "UI", "m" to "MODEL")
        val rows = projectRows(p)
        assertEquals(listOf("hud"), (rows[1].value as FolderRow).assets.map { it.name })
        assertEquals(listOf("MODEL"), groups(p).map { it.name })
    }

    @Test
    fun noListIsTruncated() {
        val p = ProjectDto("P", emptyList(), (1..41).map { testAsset("a$it", "u$it", "MODEL") })
        val group = groups(p).single().value as TypeGroup
        assertEquals(41, groupRows(group).size)
        assertEquals((1..41).map { "a$it" }.sorted(), groupRows(group).map { it.name })
    }

    @Test
    fun assetPathsAreDeterministic() {
        val rows = groups(project(emptyList(), "m" to "MODEL"))
        assertEquals("MODEL", rows.single().name)
        assertEquals(listOf("m"), groupRows(rows.single().value as TypeGroup).map { it.name })
    }

    @Test
    fun aProjectWithoutAssetsStillListsEmptyFolders() {
        val rows = projectRows(project())
        assertTrue(folderRows(rows[2].value as FolderRow).isEmpty())
        assertTrue(folderRows(rows[1].value as FolderRow).isEmpty())
    }
}
