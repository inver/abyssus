/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.sceneOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Text-level cases (the new entry's key order, the runtime loading it back) are deferred to Phase 6. */
class AssetEntitiesTest {
    private val tree = BaseMeta(MetaType.MODEL, "tree")
    private val terrain = BaseMeta(MetaType.TERRAIN, "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")
    private val assets = AssetEntities(ResourceEditorMessages())

    private fun mainScene() = parseScene(File("src/test/testData/project/Tree/scenes/Main Scene.scene").readText())

    private fun position(entity: EntityDto) = entity.component(PositionComponent::class.java)!!.localPosition

    @Test
    fun aModelBecomesTheNextEntityAndNothingElseChanges() {
        val scene = mainScene()
        val before = scene.ecs.toMap()
        val added = assets.add(scene, tree, Vector3(10f, 0f, -4f))

        assertEquals(EditResult.Changed, added.result)
        assertEquals(9L, added.entityId)
        val entity = scene.entity(9)!!
        assertEquals("Model 9", entity.component(NameComponent::class.java)!!.name)
        assertEquals(EntityType.OBJECT, entity.header().type)
        val render = entity.component(RenderComponent::class.java)!!
        assertEquals("defaultShader", render.shaderKey)
        assertEquals(MetaType.MODEL, render.type)
        assertEquals("tree", render.assetName)
        assertEquals(Vector3(10f, 0f, -4f), position(entity))
        for ((id, old) in before) assertEquals(old, scene.entity(id))
        assertEquals(before.size + 1, scene.ecs.size)
    }

    @Test
    fun aTerrainIsCentredOnThePoint() {
        val scene = mainScene()
        val added = assets.add(scene, terrain, Vector3(0f, 0f, 0f), 1600f)
        val entity = scene.entity(added.entityId!!)!!

        assertEquals("Terrain 9", entity.component(NameComponent::class.java)!!.name)
        assertEquals(EntityType.TERRAIN, entity.header().type)
        assertEquals("terrain", entity.component(RenderComponent::class.java)!!.shaderKey)
        assertEquals(Vector3(-800f, 0f, -800f), position(entity))
    }

    @Test
    fun aTerrainOfUnknownSizePutsItsCornerAtThePoint() {
        val scene = mainScene()
        val added = assets.add(scene, terrain, Vector3(3f, 1f, 2f), null)
        assertEquals(Vector3(3f, 1f, 2f), position(scene.entity(added.entityId!!)!!))
    }

    @Test
    fun anEmptySceneStartsAtOne() {
        val scene = SceneDto()
        assertEquals(1L, assets.add(scene, tree, Vector3(1f, 2f, 3f)).entityId)
        assertEquals(setOf(1L), scene.ecs.keys)
    }

    @Test
    fun aNewAssetEntityHasExactlyTheThreeComponentsAndNoFlags() {
        val scene = parseScene(File("src/test/testData/project/Lights/scenes/Creation Baseline.scene").readText())
        val first = assets.add(scene, tree, Vector3(0f, 0f, 0f))
        val second = assets.add(scene, terrain, Vector3(0f, 0f, 0f), 100f)

        for (id in listOf(first.entityId!!, second.entityId!!)) {
            val entity = scene.entity(id)!!
            assertEquals(0, entity.header().flags)
            assertEquals(
                setOf(NameComponent::class.java, PositionComponent::class.java, RenderComponent::class.java),
                entity.components.map { it.javaClass }.filter { it != entity.header().javaClass }.toSet(),
            )
        }
    }

    @Test
    fun anotherAssetTypeOrANonFinitePointIsRejectedAndLeavesTheScene() {
        val scene = sceneOf()
        val skybox = assets.add(scene, BaseMeta(MetaType.SKYBOX, "sky"), Vector3(0f, 0f, 0f))
        val nan = assets.add(scene, tree, Vector3(Float.NaN, 0f, 0f))

        assertFalse(skybox.result == EditResult.Changed)
        assertFalse(nan.result == EditResult.Changed)
        assertNull(skybox.entityId)
        assertEquals(emptySet<Long>(), scene.ecs.keys)
    }
}
