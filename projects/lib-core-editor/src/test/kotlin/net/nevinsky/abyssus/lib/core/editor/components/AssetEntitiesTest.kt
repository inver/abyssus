/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.scene.SceneEngine
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

class AssetEntitiesTest {
    private val tree = RenderAsset("MODEL", "tree")
    private val terrain = RenderAsset("TERRAIN", "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")

    private fun mainScene() = SceneJson().parse(File("src/test/testData/project/Tree/scenes/Main Scene.scene").readText())
    private fun empty() = SceneJson().parse("""{"format":"abyssus","formatVersion":1,"ecs":{}}""")
    /** The entity's position; the writer leaves out zero coordinates. */
    private fun position(entity: JsonNode) = entity["components"]["PositionComponent"]["localPosition"].let {
        Vec3(it.path("x").floatValue(), it.path("y").floatValue(), it.path("z").floatValue())
    }

    @Test
    fun aModelBecomesTheNextEntityAndNothingElseChanges() {
        val root = mainScene()
        val before = root.deepCopy<JsonNode>()
        val added = AssetEntities(ResourceEditorMessages()).add(root, tree, Vec3(10f, 0f, -4f))
        assertEquals(EditResult.Changed, added.result)
        assertEquals("9", added.entityId)
        val entities = root["ecs"]
        val entity = entities["9"]["components"]
        assertEquals("Model 9", entity["NameComponent"]["name"].asText())
        assertEquals("OBJECT", entity["TypeComponent"]["type"].asText())
        val renderable = entity["RenderComponent"]["renderable"]
        assertEquals("asset", renderable["kind"].asText())
        assertEquals("defaultShader", renderable["shaderKey"].asText())
        assertEquals("MODEL", renderable["asset"]["type"].asText())
        assertEquals("tree", renderable["asset"]["assetName"].asText())
        assertEquals(Vec3(10f, 0f, -4f), position(entities["9"]))
        for ((id, old) in before["ecs"].properties()) assertEquals(old.toString(), entities[id].toString())
        for ((key, old) in before.properties()) if (key != "ecs") assertEquals(old, root[key])
    }

    @Test
    fun aTerrainIsCentredOnThePoint() {
        val root = mainScene()
        val added = AssetEntities(ResourceEditorMessages()).add(root, terrain, Vec3(0f, 0f, 0f), 1600f)
        val entity = root["ecs"][added.entityId]
        assertEquals("Terrain 9", entity["components"]["NameComponent"]["name"].asText())
        assertEquals("TERRAIN", entity["components"]["TypeComponent"]["type"].asText())
        assertEquals("terrain", entity["components"]["RenderComponent"]["renderable"]["shaderKey"].asText())
        assertEquals(Vec3(-800f, 0f, -800f), position(entity))
    }

    @Test
    fun aTerrainOfUnknownSizePutsItsCornerAtThePoint() {
        val root = mainScene()
        val entity = root["ecs"][AssetEntities(ResourceEditorMessages()).add(root, terrain, Vec3(3f, 1f, 2f), null).entityId]
        assertEquals(Vec3(3f, 1f, 2f), position(entity))
    }

    @Test
    fun anEmptySceneStartsAtZeroAndTheRuntimeLoadsIt() {
        val root = empty()
        assertEquals("0", AssetEntities(ResourceEditorMessages()).add(root, tree, Vec3(1f, 2f, 3f)).entityId)
        val document = EcsLoader(JsonProcessor().mapper, log = NOPLogger.NOP_LOGGER)
            .loadToEngine(root["ecs"], SceneEngine())
        // loaded without a project, the only complaint is the missing folder: the render component itself is understood
        assertEquals(listOf("render asset MODEL tree has no folder in the project assets"), document.warnings.map { it.toString() })
    }

    @Test
    fun aWrappedSceneGetsAMatchingArchetype() {
        val root = SceneJson().parse(File("src/test/testData/project/Lights/scenes/Creation Baseline.scene").readText())
        val first = AssetEntities(ResourceEditorMessages()).add(root, tree, Vec3(0f, 0f, 0f))
        val ecs = root["ecs"]
        val entity = ecs["entities"][first.entityId]
        val archetype = ecs["archetypes"][entity["archetype"].asText()].map { it.asText() }.toSet()
        assertEquals(setOf("NameComponent", "TypeComponent", "PositionComponent", "RenderComponent"), archetype)
        val archetypes = ecs["archetypes"].toString()
        val second = AssetEntities(ResourceEditorMessages()).add(root, terrain, Vec3(0f, 0f, 0f), 100f)
        assertEquals(archetypes, ecs["archetypes"].toString())
        assertEquals(entity["archetype"], ecs["entities"][second.entityId]["archetype"])
    }

    @Test
    fun aNonNativeSceneOrAnotherAssetTypeIsRejected() {
        val legacy = SceneJson().parse("""{"ecs":{}}""")
        assertFalse(AssetEntities(ResourceEditorMessages()).add(legacy, tree, Vec3(0f, 0f, 0f)).result == EditResult.Changed)
        assertFalse(AssetEntities(ResourceEditorMessages()).add(empty(), RenderAsset("SKYBOX", "sky"), Vec3(0f, 0f, 0f)).result == EditResult.Changed)
        assertFalse(AssetEntities(ResourceEditorMessages()).add(empty(), tree, Vec3(Float.NaN, 0f, 0f)).result == EditResult.Changed)
    }
}
