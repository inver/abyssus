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
