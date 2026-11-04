/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.meta.EntityAssetChoices
import net.nevinsky.abyssus.lib.core.editor.meta.EntitySections
import net.nevinsky.abyssus.lib.core.editor.meta.readEntitySections
import net.nevinsky.abyssus.lib.core.editor.panel.ChoiceRow
import net.nevinsky.abyssus.lib.core.editor.panel.PanelBuilders
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

/** `meshId` on a model's render component: read, shown, edited and placed without the IDE. */
class MeshIdEditingTest {
    private val messages = ResourceEditorMessages()
    private val editor = ComponentEditor(messages)
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)

    private fun scene(render: String): SceneDto =
        parseScene(
            """{
  "format": "abyssus",
  "formatVersion": 1,
  "ecs": {
    "1": {
      "type": "OBJECT",
      "components": {
        "PositionComponent": {
          "localPosition": { "x": 1.50, "y": 2, "z": 3 }
        },
        "RenderComponent": $render
      }
    }
  }
}
"""
        )

    private val glass
        get() = scene("""{ "type": "MODEL", "assetName": "duo", "meshId": "glass", "shaderKey": "defaultShader" }""")

    private val whole
        get() = scene("""{ "type": "MODEL", "assetName": "duo", "shaderKey": "defaultShader" }""")

    @Test
    fun theViewPlacesTheNamedMeshOnly() {
        val models = sceneContentOf(glass).models.single()
        assertEquals("duo", models.assetName)
        assertEquals("glass", models.meshId)
        assertNull(sceneContentOf(whole).models.single().meshId)
    }

    @Test
    fun aTerrainIgnoresMeshId() {
        val terrain = scene("""{ "type": "TERRAIN", "assetName": "t", "meshId": "glass" }""")
        val content = sceneContentOf(terrain)
        assertEquals(1, content.terrains.size)
        assertNull(content.terrains.single().meshId)
        assertTrue(content.models.isEmpty())
    }

    private fun mesh(scene: SceneDto) = scene.component(1, RenderComponent::class.java)!!.meshId

    @Test
    fun settingAndClearingTheField() {
        val scene = whole
        assertEquals(EditResult.Changed, editor.update(scene, 1L, "RenderComponent", "meshId", "glass"))
        assertEquals("glass", mesh(scene))
        assertEquals(EditResult.Changed, editor.update(scene, 1L, "RenderComponent", "meshId", ""))
        assertNull(mesh(scene))
    }

    @Test
    fun theWholeModelEntryIsNoChangeWhenThereIsNoMesh() {
        assertEquals(EditResult.Unchanged, editor.update(whole, 1L, "RenderComponent", "meshId", ""))
    }

    @Test
    fun placeAssetWithAMeshRecordsIt() {
        val duo = BaseMeta(MetaType.MODEL, "duo")
        val scene = scene("""{ "type": "MODEL", "assetName": "duo" }""")
        val id = AssetEntities(messages).add(scene, duo, Vector3(4f, 0f, 5f), meshId = "glass").entityId!!
        val added = scene.entity(id)!!
        assertEquals(EntityType.OBJECT, added.header().type)
        assertEquals("glass", added.component(RenderComponent::class.java)!!.meshId)
        assertEquals("duo", added.component(RenderComponent::class.java)!!.assetName)

        val wholeId = AssetEntities(messages).add(scene, duo, Vector3(4f, 0f, 5f)).entityId!!
        assertNull(scene.entity(wholeId)!!.component(RenderComponent::class.java)!!.meshId)
    }

    @Test
    fun aTerrainIsPlacedWithoutMeshId() {
        val terrain = BaseMeta(MetaType.TERRAIN, "t")
        val scene = whole
        val placed = AssetEntities(messages).add(scene, terrain, Vector3(), meshId = "glass")
        assertEquals(EditResult.Changed, placed.result)
        assertNull(scene.entity(placed.entityId!!)!!.component(RenderComponent::class.java)!!.meshId)
    }

    private fun sections(scene: SceneDto, meshes: (String) -> List<String>?): EntitySections.Read =
        readEntitySections(
            scene,
            1L,
            "RenderComponent",
            editor,
            EntityAssetChoices(listOf("duo"), emptyMap(), meshes),
            messages,
            json,
        )
            as EntitySections.Read

    private fun meshField(read: EntitySections.Read) =
        read.sections.single().fields.firstOrNull { it.field == "meshId" }

    @Test
    fun theFieldOffersTheMeshesOfTheModel() {
        val field = meshField(sections(glass) { listOf("body", "glass") })!!
        assertEquals(FieldKind.MESH_ID, field.kind)
        assertEquals(listOf("body", "glass"), field.choices)
        assertEquals("glass", field.value)
        assertTrue(field.optional)
    }

    @Test
    fun theFieldIsLeftOutForATerrainOrAnUnreadableModel() {
        assertNull(meshField(sections(glass) { null }))
        assertNull(meshField(sections(scene("""{ "type": "TERRAIN", "assetName": "duo" }""")) { listOf("body") }))
    }

    @Test
    fun aStoredIdTheModelLacksIsShownAsUnknown() {
        val builders = PanelBuilders(messages) { false }
        fun row(scene: SceneDto, meshes: List<String>): ChoiceRow {
            val read = sections(scene) { meshes }
            val panel = builders.entity(scene, 1L, read, "x.abss")
            return panel.sections
                .flatMap { it.rows }
                .filterIsInstance<ChoiceRow>()
                .single { it.field?.field == "meshId" }
        }
        val known = row(glass, listOf("body", "glass"))
        assertFalse(known.unknown)
        assertTrue(known.optional)
        val unknown = row(glass, listOf("body", "wing"))
        assertTrue(unknown.unknown)
        assertEquals("glass", unknown.value)
        assertEquals(listOf("body", "wing"), unknown.choices)
        assertFalse(row(whole, listOf("body")).unknown)
    }
}
