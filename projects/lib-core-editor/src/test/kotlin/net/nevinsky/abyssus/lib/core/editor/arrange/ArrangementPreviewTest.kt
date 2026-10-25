/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.PathAsset
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.content.ArrangementPlacement
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.pick.PathEdit
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrangementPreviewTest {
    private fun scene(): SceneContent {
        val path = arrangementAlong(listOf(Vector3(0f, 0f, 0f), Vector3(20f, 0f, 0f))).also {
            it.asset = PathAsset(assetName = "post")
            it.spacing = 10f
            it.slots = mutableListOf(11, 12, 13)
        }
        val models = listOf(11, 12, 13).map { AssetPlacement(it.toString(), "post", PlacementTransform.IDENTITY) }
        return SceneContent(models = models + AssetPlacement("99", "rock", PlacementTransform.IDENTITY), arrangements = listOf(ArrangementPlacement("10", path)))
    }

    @Test
    fun copiesFollowTheDraftHeightAndKeepTheirIds() {
        val draft = PathEdit("10", listOf(Vector3(0f, 0f, 0f), Vector3(20f, 0f, 0f)))
        val shown = ArrangementPreview().apply(scene(), draft) { _, _, _ -> 4f }
        assertEquals(listOf("99", "11", "12", "13"), shown.models.map { it.entityId })
        assertEquals(listOf(4f, 4f, 4f), shown.models.drop(1).map { it.transform.position.y })
    }

    @Test
    fun aLongerDraftAddsPreviewCopiesAndAShorterOneDropsThem() {
        val longer = ArrangementPreview().apply(scene(), PathEdit("10", listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f))), LineHeights())
        assertEquals(5, longer.models.count { it.assetName == "post" })
        val shorter = ArrangementPreview().apply(scene(), PathEdit("10", listOf(Vector3(0f, 0f, 0f), Vector3(10f, 0f, 0f))), LineHeights())
        assertEquals(listOf("99", "11", "12"), shorter.models.map { it.entityId })
    }

    @Test
    fun aNegativeYawTurnsAsRegenerateWritesIt() {
        val q = PathSlot(1f, 2f, 3f, -90f).rotation()
        assertEquals(true, q.w >= 0f)
        assertEquals(PathSlot(1f, 2f, 3f, 270f).rotation().y, q.y, 1e-5f)
    }
}
