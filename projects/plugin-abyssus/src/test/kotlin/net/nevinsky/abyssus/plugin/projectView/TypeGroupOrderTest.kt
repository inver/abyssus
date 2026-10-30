/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.assets.MetaType
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeGroupOrderTest {
    @Test
    fun wellKnownGroupsComeFirstThenTheRestAlphabetically() {
        val shuffled = listOf("SHADER", "CLOUDS", "MATERIAL", "SKYBOX", "TEXTURE", "FOLIAGE", "TERRAIN", "MODEL", "PIXMAP_TEXTURE")
        assertEquals(
            listOf("MODEL", "TERRAIN", "FOLIAGE", "TEXTURE", "SKYBOX", "CLOUDS", "MATERIAL", "PIXMAP_TEXTURE", "SHADER"),
            shuffled.sortedWith(typeGroupOrder),
        )
    }

    @Test
    fun skyboxTypesShareOneGroup() {
        assertEquals(setOf("SKYBOX"), listOf(MetaType.SKYBOX, MetaType.SKYBOX_HDR, MetaType.SKYBOX_PROCEDURAL).map(::groupOf).toSet())
    }

    @Test
    fun anUnknownGroupShowsItsCapitalisedName() {
        assertEquals("Pixmap texture", groupLabel("PIXMAP_TEXTURE"))
    }
}

class FixedRankTest {
    @Test
    fun foldersAndGroupsKeepTheirOrderAgainstNameSorting() {
        val scenes = fixedRank(emptyList<Any>(), "scenes")
        val ui = fixedRank(FolderRow(FolderKind.UI, emptyList()), "ui")
        val assets = fixedRank(FolderRow(FolderKind.ASSETS, emptyList()), "assets")
        org.junit.Assert.assertTrue(compareFixed(scenes, ui)!! < 0)
        org.junit.Assert.assertTrue(compareFixed(ui, assets)!! < 0)
        org.junit.Assert.assertTrue(compareFixed(fixedRank(TypeGroup("TERRAIN", emptyList()), "TERRAIN"), fixedRank(TypeGroup("SKYBOX", emptyList()), "SKYBOX"))!! < 0)
        org.junit.Assert.assertNull(compareFixed(scenes, fixedRank("x", "ecs")))
    }
}
