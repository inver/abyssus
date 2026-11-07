/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modellights

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.components.LightEntities
import net.nevinsky.abyssus.lib.core.editor.components.LightPreset
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.format.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLightsTest {
    private val edit = ModelLights(ResourceEditorMessages())

    /** A scene with a spot light (id 1), the warehouse (id 2, moved 10 along x, scaled 2) and a rock (id 3). */
    private fun scene(): SceneDto {
        val scene = SceneDto()
        LightEntities(ResourceEditorMessages()).add(scene, LightPreset.SPOT, Vector3())
        scene.add(
            EntityType.OBJECT,
            NameComponent("Warehouse"),
            PositionComponent().also {
                it.localPosition.set(10f, 0f, 0f)
                it.localScale = Vector3(2f, 2f, 2f)
            },
            RenderComponent("defaultShader", MetaType.MODEL, "model_warehouse"),
        )
        return scene
    }

    private fun geometry() = materialGeometries(lampsAt(0f, 5f)).single()

    @Test
    fun addsOnePointLightPerLampAtItsWorldCentre() {
        val scene = scene()
        val before = scene.ecs.toMap()

        val added = edit.add(scene, 2, geometry())

        assertEquals(EditResult.Changed, added.result)
        assertEquals(2, added.ids.size)
        assertEquals((before.keys.max() + 1..before.keys.max() + 2).toList(), added.ids)
        for ((id, old) in before) assertEquals(old, scene.entity(id))
        val lights = sceneContentOf(scene).lights.filter { it.kind == LightKind.POINT }
        assertEquals(2, lights.size)
        // lamp centres are x 0.5 and 5.5, y 0.5; the entity moves them 10 along x and doubles them
        assertEquals(Vector3(11f, 1f, 0f), lights[0].position)
        assertEquals(Vector3(21f, 1f, 0f), lights[1].position)
        assertEquals(0.8f, lights[0].color.g, 1e-6f)
        assertTrue(lights[0].range >= LAMP_MIN_RANGE)
        assertEquals(
            listOf("Warehouse Lamp 1", "Warehouse Lamp 2"),
            added.ids.map { scene.component(it, NameComponent::class.java)!!.name },
        )
    }

    @Test
    fun aNonModelEntityIsRejected() {
        val scene = scene()
        val rejected = edit.add(scene, 1, geometry()).result
        assertTrue(rejected is EditResult.Rejected)
        assertEquals(2, scene.ecs.size)
    }

    @Test
    fun noGeometryIsRejectedAndNothingIsAdded() {
        val scene = scene()
        val empty = MaterialGeometry("lamp", null, FloatArray(0), IntArray(0))
        assertTrue(edit.add(scene, 2, empty).result is EditResult.Rejected)
        assertEquals(2, scene.ecs.size)
    }

    @Test
    fun overTheCapLampsAreMerged() {
        val scene = scene()
        val added = edit.add(scene, 2, geometry(), max = 1)
        assertEquals(1, added.ids.size)
        assertTrue(added.merged)
    }
}
