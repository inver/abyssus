/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEdit
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing
import net.nevinsky.abyssus.lib.core.editor.meta.EntityAssetChoices
import net.nevinsky.abyssus.lib.core.editor.meta.EntitySections
import net.nevinsky.abyssus.lib.core.editor.meta.readEntitySections
import net.nevinsky.abyssus.lib.core.editor.panel.ChoiceRow
import net.nevinsky.abyssus.lib.core.editor.panel.PanelBuilders
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.editor.parseScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `meshId` on a model's render component: read, shown, edited and placed without the IDE. */
class MeshIdEditingTest {
    private val messages = ResourceEditorMessages()
    private val editing = HeadlessEditing()
    private val editor = ComponentEditor(messages)

    /** [text] as the editor prints it, so an edit changes only the edited lines. */
    private fun canonical(text: String) = SceneJson().let { it.pretty(it.parse(text)) }

    private fun scene(render: String) = canonical("""{
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
""")

    private val glass = scene("""{ "type": "MODEL", "assetName": "duo", "meshId": "glass", "shaderKey": "defaultShader" }""")
    private val whole = scene("""{ "type": "MODEL", "assetName": "duo", "shaderKey": "defaultShader" }""")

    @Test
    fun theViewPlacesTheNamedMeshOnly() {
        val models = sceneContentOf(parseScene(glass)).models.single()
        assertEquals("duo", models.assetName)
        assertEquals("glass", models.meshId)
        assertNull(sceneContentOf(parseScene(whole)).models.single().meshId)
    }

    @Test
    fun aTerrainIgnoresMeshId() {
        val terrain = scene("""{ "type": "TERRAIN", "assetName": "t", "meshId": "glass" }""")
        val content = sceneContentOf(parseScene(terrain))
        assertEquals(1, content.terrains.size)
        assertNull(content.terrains.single().meshId)
        assertTrue(content.models.isEmpty())
    }

    @Test
    fun settingAndClearingTheFieldKeepsTheRestOfTheText() {
        val set = editing.setComponentField(whole, "1", "RenderComponent", "meshId", "glass") as HeadlessEdit.Edited
        assertEquals(1, set.text.lines().size - whole.lines().size)
        assertTrue(set.text.contains("\"meshId\": \"glass\""))
        assertTrue("number text kept", set.text.contains("\"x\": 1.50"))
        val cleared = editing.setComponentField(set.text, "1", "RenderComponent", "meshId", "") as HeadlessEdit.Edited
        assertEquals(whole, cleared.text)
        assertFalse(cleared.text.contains("meshId"))
    }

    @Test
    fun theWholeModelEntryIsNoChangeWhenThereIsNoMesh() {
        assertEquals(HeadlessEdit.Unchanged, editing.setComponentField(whole, "1", "RenderComponent", "meshId", ""))
    }

    @Test
    fun placeAssetWithAMeshRecordsIt() {
        val duo = BaseMeta(MetaType.MODEL, "duo")
        val placed = editing.placeAsset(scene("""{ "type": "MODEL", "assetName": "duo" }"""), duo, 4f, 0f, 5f, meshId = "glass") as HeadlessEdit.Edited
        val added = SceneJson().parse(placed.text)["ecs"]["2"]
        assertEquals("OBJECT", added["type"].asText())
        assertEquals("glass", added["components"]["RenderComponent"]["meshId"].asText())
        assertEquals("duo", added["components"]["RenderComponent"]["assetName"].asText())

        val wholeModel = editing.placeAsset(whole, duo, 4f, 0f, 5f) as HeadlessEdit.Edited
        assertFalse(SceneJson().parse(wholeModel.text)["ecs"]["2"]["components"]["RenderComponent"].has("meshId"))
    }

    @Test
    fun aTerrainIsPlacedWithoutMeshId() {
        val terrain = BaseMeta(MetaType.TERRAIN, "t")
        val placed = AssetEntities(messages).add(SceneJson().parse(whole), terrain, Vector3(), meshId = "glass")
        assertEquals(EditResult.Changed, placed.result)
    }

    private fun sections(text: String, meshes: (String) -> List<String>?): EntitySections.Read =
        readEntitySections(SceneJson().parse(text), "1", "RenderComponent", editor, EntityAssetChoices(listOf("duo"), emptyMap(), meshes), messages) as EntitySections.Read

    private fun meshField(read: EntitySections.Read) = read.sections.single().fields.firstOrNull { it.field == "meshId" }

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
        fun row(text: String, meshes: List<String>): ChoiceRow {
            val read = sections(text) { meshes }
            val panel = builders.entity(SceneJson().parse(text), "1", read, "x.abss")
            return panel.sections.flatMap { it.rows }.filterIsInstance<ChoiceRow>().single { it.field?.field == "meshId" }
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
