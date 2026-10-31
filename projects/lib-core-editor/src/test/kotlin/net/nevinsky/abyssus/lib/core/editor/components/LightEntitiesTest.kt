/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import net.nevinsky.abyssus.lib.core.editor.components.LightEntities
import net.nevinsky.abyssus.lib.core.editor.components.LightPreset
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

class LightEntitiesTest {
    private fun fixture() = SceneJson().parse(File("src/test/testData/project/Lights/scenes/Creation Baseline.scene").readText())
    private fun empty() = SceneJson().parse("""{"format":"abyssus","formatVersion":1,"ecs":{}}""")

    @Test fun createsNextEntityAndPreservesExistingData() {
        val root = fixture()
        val before = root.deepCopy<JsonNode>()
        val added = LightEntities(ResourceEditorMessages()).add(root, LightPreset.DIRECTIONAL, Vector3(0f, 0f, 0f))
        assertEquals(EditResult.Changed, added.result)
        assertEquals("7", added.entityId)
        val ecs = root["ecs"]
        assertFalse(ecs.has("componentIdentifiers"))
        val entity = ecs["7"]
        assertEquals("Directional Light 7", entity["components"]["NameComponent"]["name"].asText())
        for ((id, old) in before["ecs"].properties()) assertEquals(old.toString(), ecs[id].toString())
        val components = entity["components"].fieldNames().asSequence().toSet()
        assertEquals(setOf("NameComponent", "PositionComponent", "LightComponent"), components)
        assertEquals(listOf("type", "components"), entity.fieldNames().asSequence().toList())
        val second = LightEntities(ResourceEditorMessages()).add(root, LightPreset.SPOT, Vector3(0f, 0f, 0f))
        assertEquals("8", second.entityId)
        assertEquals(listOf("type", "components"), ecs["8"].fieldNames().asSequence().toList())
    }

    @Test fun emptySceneStartsAtZeroAndPresetsLoad() {
        for (preset in LightPreset.entries) {
            val root = empty()
            val added = LightEntities(ResourceEditorMessages()).add(root, preset, Vector3(10f, 0f, -4f))
            assertEquals("0", added.entityId)
            val light = sceneContentOf(parseScene(root.toString())).lights.single()
            assertEquals(if (preset == LightPreset.SPOT) LightKind.SPOT else LightKind.DIRECTIONAL, light.kind)
            assertEquals(Vector3(10f, if (preset == LightPreset.SPOT) 5f else 0f, -4f), light.position)
            val y = when (preset) { LightPreset.DIRECTIONAL -> -0.7071068f; LightPreset.SUN -> -0.5f; LightPreset.SPOT -> -1f }
            val z = when (preset) { LightPreset.DIRECTIONAL -> -0.7071068f; LightPreset.SUN -> -0.8660254f; LightPreset.SPOT -> 0f }
            assertEquals(0f, light.direction.x, 0.0001f)
            assertEquals(y, light.direction.y, 0.0001f)
            assertEquals(z, light.direction.z, 0.0001f)
            assertEquals(100f, light.range, 0f)
            if (preset == LightPreset.SUN) { assertTrue(light.intensity > 1f); assertTrue(light.color.b < light.color.r) }
            val engine = SceneEngine()
            val document = net.nevinsky.abyssus.lib.core.testing.loadEcs(SceneJson().compact(root["ecs"]), engine)
            assertTrue(document.warnings.toString(), document.warnings.isEmpty())
            val entity = engine.ids[0]!!
            assertNotNull(entity.getComponent(NameComponent::class.java))
            assertNotNull(entity.getComponent(net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent::class.java).type)
            assertNotNull(entity.getComponent(PositionComponent::class.java))
            assertNotNull(entity.getComponent(LightComponent::class.java))
        }
    }

    @Test fun missingEcsOrEntitiesIsAnEmptyScene() {
        for (text in listOf("""{"format":"abyssus","formatVersion":1}""", """{"format":"abyssus","formatVersion":1,"name":"Empty","ecs":{}}""")) {
            val root = SceneJson().parse(text)
            val added = LightEntities(ResourceEditorMessages()).add(root, LightPreset.SUN, Vector3(0f, 0f, 0f))
            assertEquals(EditResult.Changed, added.result)
            assertEquals("0", added.entityId)
            assertEquals(1, sceneContentOf(parseScene(root.toString())).lights.size)
        }
    }

    @Test fun anEntityAddedToAnEmptyEcsStartsAtZeroWithoutFlags() {
        val root = SceneJson().parse("""{"format":"abyssus","formatVersion":1,"ecs":{}}""")
        LightEntities(ResourceEditorMessages()).add(root, LightPreset.SUN, Vector3(0f, 0f, 0f))
        assertEquals(listOf("type", "components"), root["ecs"]["0"].fieldNames().asSequence().toList())
    }

    @Test fun malformedBookkeepingIsRejectedWithoutMutation() {
        for (text in listOf("""{"format":"abyssus","formatVersion":1,"ecs":[]}""", "[]", """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""")) {
            val root = SceneJson().parse(text)
            val before = root.toString()
            assertTrue(LightEntities(ResourceEditorMessages()).add(root, LightPreset.SUN, Vector3(0f, 0f, 0f)).result is EditResult.Rejected)
            assertEquals(before, root.toString())
        }
    }
}
