/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.sceneOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderedAssetTest {
    @Test
    fun anEntityNamesItsRenderedAsset() {
        val scene = sceneOf(
            entityOf(1, EntityType.TERRAIN, RenderComponent("terrain", MetaType.TERRAIN, "t1")),
            entityOf(2, EntityType.OBJECT, NameComponent("plain")),
        )
        val asset = renderedAsset(scene, 1)!!
        assertEquals("t1", asset.name)
        assertTrue(asset.isTerrain())
        assertNull(renderedAsset(scene, 2))
        assertNull(renderedAsset(scene, 99))
    }
}
