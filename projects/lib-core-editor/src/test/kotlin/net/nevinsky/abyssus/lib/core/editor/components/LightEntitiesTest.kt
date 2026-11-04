/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.math.Vector3
import java.io.File
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.format.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text-level cases (key order of the new entry, the runtime loading it back) are deferred to Phase 6. */
class LightEntitiesTest {
    private val lights = LightEntities(ResourceEditorMessages())

    private fun fixture() =
        parseScene(File("src/test/testData/project/Lights/scenes/Creation Baseline.scene").readText())

    @Test
    fun createsNextEntityAndPreservesExistingData() {
        val scene = fixture()
        val before = scene.ecs.toMap()

        val added = lights.add(scene, LightPreset.DIRECTIONAL, Vector3(0f, 0f, 0f))

        assertEquals(EditResult.Changed, added.result)
        assertEquals(7L, added.entityId)
        val entity = scene.entity(7)!!
        assertEquals("Directional Light 7", entity.component(NameComponent::class.java)!!.name)
        assertEquals(EntityType.LIGHT_DIRECTIONAL, entity.header().type)
        for ((id, old) in before) assertEquals(old, scene.entity(id))
        assertEquals(
            setOf(NameComponent::class.java, PositionComponent::class.java, LightComponent::class.java),
            entity.components.map { it.javaClass }.filter { it != entity.header().javaClass }.toSet(),
        )
        assertEquals(0, entity.header().flags)
        assertEquals(8L, lights.add(scene, LightPreset.SPOT, Vector3(0f, 0f, 0f)).entityId)
    }

    @Test
    fun emptySceneStartsAtOneAndPresetsReadBackAsLights() {
        for (preset in LightPreset.entries) {
            val scene = SceneDto()
            val added = lights.add(scene, preset, Vector3(10f, 0f, -4f))
            assertEquals(1L, added.entityId)

            val light = sceneContentOf(scene).lights.single()
            assertEquals(
                if (preset == LightPreset.SPOT) {
                    LightKind.SPOT
                } else {
                    LightKind.DIRECTIONAL
                },
                light.kind,
            )
            assertEquals(
                Vector3(
                    10f,
                    if (preset == LightPreset.SPOT) {
                        5f
                    } else {
                        0f
                    },
                    -4f,
                ),
                light.position,
            )
            val y =
                when (preset) {
                    LightPreset.DIRECTIONAL -> -0.7071068f
                    LightPreset.SUN -> -0.5f
                    LightPreset.SPOT -> -1f
                }
            val z =
                when (preset) {
                    LightPreset.DIRECTIONAL -> -0.7071068f
                    LightPreset.SUN -> -0.8660254f
                    LightPreset.SPOT -> 0f
                }
            assertEquals(0f, light.direction.x, 0.0001f)
            assertEquals(y, light.direction.y, 0.0001f)
            assertEquals(z, light.direction.z, 0.0001f)
            assertEquals(100f, light.range, 0f)
            if (preset == LightPreset.SUN) {
                assertTrue(light.intensity > 1f)
                assertTrue(light.color.b < light.color.r)
            }
            assertNotNull(scene.entity(1)!!.header().type)
        }
    }

    @Test
    fun aSceneWithoutEntitiesGetsOneLight() {
        for (text in
            listOf(
                """{"format":"abyssus","formatVersion":1}""",
                """{"format":"abyssus","formatVersion":1,"name":"Empty","ecs":{}}""",
            )) {
            val scene = parseScene(text)
            val added = lights.add(scene, LightPreset.SUN, Vector3(0f, 0f, 0f))
            assertEquals(EditResult.Changed, added.result)
            assertEquals(1L, added.entityId)
            assertEquals(1, sceneContentOf(scene).lights.size)
        }
    }

    @Test
    fun aNonFinitePositionIsRejectedWithoutMutation() {
        val scene = fixture()
        val ids = scene.ecs.keys.toSet()
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY)) {
            assertTrue(lights.add(scene, LightPreset.SUN, Vector3(bad, 0f, 0f)).result is EditResult.Rejected)
        }
        assertEquals(ids, scene.ecs.keys)
    }
}
