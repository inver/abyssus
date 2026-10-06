/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs

import com.fasterxml.jackson.databind.JsonNode
import java.io.File
import net.nevinsky.abyssus.runtime.ecs.EcsConfigurator
import net.nevinsky.abyssus.runtime.ecs.render.FolderAssetResolver
import net.nevinsky.abyssus.ecs.scene.ComponentEditor
import net.nevinsky.abyssus.ecs.scene.EditResult
import net.nevinsky.abyssus.ecs.scene.FieldKind
import net.nevinsky.abyssus.runtime.schema.SchemaFile
import net.nevinsky.abyssus.filetype.SceneJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentEditorTest {
    private val editor = ComponentEditor()

    private fun scene(vararg entities: String) =
        SceneJson.parse("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{${entities.joinToString(",")}},"archetypes":{"1":{"a":1}},"metadata":{"m":2}}}""")

    private fun entity(id: Int, components: String) = """"$id":{"archetype":1,"components":{$components}}"""

    private fun components(root: JsonNode, id: Int) = root["ecs"]["entities"]["$id"]["components"]

    private fun assertRejected(result: EditResult) = assertTrue("expected Rejected, was $result", result is EditResult.Rejected)

    @Test
    fun everyKindHasFields() {
        val names = editor.kinds.associate { it.name to it.fields.map { f -> f.name } }
        assertEquals(8, names.size)
        assertEquals(listOf("name"), names["NameComponent"])
        assertEquals(listOf("type"), names["TypeComponent"])
        assertEquals(listOf("parentEntityId"), names["ParentComponent"])
        assertEquals(11, names["PositionComponent"]!!.size)
        assertTrue("camera.fieldOfView" in names["CameraComponent"]!!)
        assertEquals(listOf("color.r", "color.g", "color.b", "color.a", "intensity", "range", "coneAngle", "edgeSoftness"), names["LightComponent"])
        assertEquals(listOf("entity1Id", "entity2Id"), names["Point2PointPositionComponent"])
        assertEquals(listOf("assetType", "assetName", "shaderKey"), names["RenderComponent"])
    }

    @Test
    fun lightRangeChangesOnlyRangeAndDropsDefault() {
        val root = scene(entity(0, """"LightComponent":{"light":{"color":{"r":1.00,"g":1,"b":1,"a":1},"intensity":1.0}}"""))
        val before = root.toString()
        assertEquals(EditResult.Unchanged, editor.update(root, "0", "LightComponent", "range", "100"))
        assertEquals(before, root.toString())
        assertEquals(EditResult.Changed, editor.update(root, "0", "LightComponent", "range", "30"))
        assertEquals(30, components(root, 0)["LightComponent"]["light"]["range"].asInt())
        assertEquals(EditResult.Changed, editor.update(root, "0", "LightComponent", "range", "100"))
        assertEquals(before, root.toString())
        for (invalid in listOf("0", "-1", "abc", "NaN", "Infinity")) {
            val result = editor.update(root, "0", "LightComponent", "range", invalid)
            assertRejected(result)
            assertTrue((result as EditResult.Rejected).reason.contains("range"))
            assertEquals(before, root.toString())
        }
    }

    @Test
    fun spotlightEditsValidateBoundariesPreserveTextAndOmitDefaults() {
        for (nested in listOf(true, false)) {
            val values = """{"intensity":1.000,"future":2.3400}"""
            val light = if (nested) """{"light":$values,"outer":7.00}""" else values
            val root = scene(entity(0, """"TypeComponent":{"type":"LIGHT_SPOT"},"LightComponent":$light"""))
            val before = root.toString()
            for ((field, default) in listOf("coneAngle" to "45", "edgeSoftness" to "20")) {
                assertEquals(EditResult.Unchanged, editor.update(root, "0", "LightComponent", field, default))
            }
            for ((field, invalid) in listOf("coneAngle" to listOf("0", "180", "-1", "181", "NaN", "Infinity", "abc"),
                "edgeSoftness" to listOf("-1", "101", "NaN", "Infinity", "abc"))) {
                for (value in invalid) {
                    val result = editor.update(root, "0", "LightComponent", field, value)
                    assertRejected(result)
                    assertTrue((result as EditResult.Rejected).reason.contains(field))
                    assertEquals(before, root.toString())
                }
            }
            assertEquals(EditResult.Changed, editor.update(root, "0", "LightComponent", "coneAngle", "60"))
            assertEquals(EditResult.Changed, editor.update(root, "0", "LightComponent", "edgeSoftness", "25"))
            val c = components(root, 0)["LightComponent"]
            val saved = c["light"] ?: c
            assertEquals(60f, saved["coneAngle"].floatValue(), 0f)
            assertEquals(0.25f, saved["edgeSoftness"].floatValue(), 0f)
            assertEquals("1.000", saved["intensity"].toString())
            assertEquals("2.3400", saved["future"].toString())
            for (softness in listOf("0", "100")) {
                assertEquals(EditResult.Changed, editor.update(root, "0", "LightComponent", "edgeSoftness", softness))
            }
            assertEquals(EditResult.Changed, editor.update(root, "0", "LightComponent", "coneAngle", "45"))
            assertEquals(EditResult.Changed, editor.update(root, "0", "LightComponent", "edgeSoftness", "20"))
            assertEquals(before, root.toString())
        }
    }

    @Test
    fun directRangeWithoutColorStaysDirect() {
        val root = scene(entity(0, """"LightComponent":{"range":30,"other":1.00}"""))
        assertEquals(EditResult.Changed, editor.update(root, "0", "LightComponent", "range", "40"))
        val light = components(root, 0)["LightComponent"]
        assertEquals(40, light["range"].asInt())
        assertFalse(light.has("light"))
        assertEquals("1.00", light["other"].asText())
        assertEquals(EditResult.Changed, editor.update(root, "0", "LightComponent", "range", "100"))
        assertEquals("""{"other":1.00}""", light.toString())
    }

    @Test
    fun addLightUsesDefaults() {
        val root = scene(entity(0, """"NameComponent":{"name":"a"}"""))
        assertEquals(EditResult.Changed, editor.add(root, "0", "LightComponent"))
        val c = components(root, 0)
        assertEquals(listOf("NameComponent", "LightComponent"), c.fieldNames().asSequence().toList())
        val light = c["LightComponent"]["light"]
        assertEquals(1, light["intensity"].asInt())
        assertEquals(listOf(1, 1, 1, 1), listOf("r", "g", "b", "a").map { light["color"][it].asInt() })
        assertEquals("a", c["NameComponent"]["name"].asText())
    }

    @Test
    fun addRenderNeedsAnAsset() {
        val root = scene(entity(0, """"NameComponent":{}"""))
        assertRejected(editor.add(root, "0", "RenderComponent"))
        assertFalse(components(root, 0).has("RenderComponent"))
        assertRejected(editor.add(root, "0", "RenderComponent", mapOf("assetName" to "tree"), assets = setOf("rock")))
        assertEquals(EditResult.Changed, editor.add(root, "0", "RenderComponent", mapOf("assetName" to "tree"), setOf("tree")))
        val asset = components(root, 0)["RenderComponent"]["renderable"]["asset"]
        assertEquals("MODEL", asset["type"].asText())
        assertEquals("tree", asset["assetName"].asText())
    }

    @Test
    fun addTwiceIsRejected() {
        val root = scene(entity(0, """"PositionComponent":{}"""))
        val before = root.toString()
        assertRejected(editor.add(root, "0", "PositionComponent"))
        assertEquals(before, root.toString())
    }

    @Test
    fun missingKindsListsOnlyModeledOnes() {
        val root = scene(entity(0, """"PositionComponent":{},"PickableComponent":{}"""))
        val missing = editor.missingKinds(root, "0").map { it.name }
        assertFalse("PositionComponent" in missing)
        assertFalse("PickableComponent" in missing)
        assertTrue("LightComponent" in missing)
        assertEquals(7, missing.size)
    }

    @Test
    fun updateCameraFieldTouchesOnlyThatValue() {
        val camera = """"CameraComponent":{"camera":{"position":{"x":1.50,"y":2},"far":200,"near":0.5,"fieldOfView":67}}"""
        val root = scene(entity(0, camera))
        assertEquals(EditResult.Changed, editor.update(root, "0", "CameraComponent", "camera.fieldOfView", "60"))
        val cam = components(root, 0)["CameraComponent"]["camera"]
        assertEquals(60, cam["fieldOfView"].asInt())
        assertEquals("""{"x":1.50,"y":2}""", cam["position"].toString())
        assertEquals(200, cam["far"].asInt())
    }

    @Test
    fun updateRejectsNonNumbers() {
        val root = scene(entity(0, """"LightComponent":{"light":{"intensity":1}}"""))
        val before = root.toString()
        val result = editor.update(root, "0", "LightComponent", "intensity", "abc")
        assertRejected(result)
        assertTrue((result as EditResult.Rejected).reason.contains("intensity"))
        assertEquals(before, root.toString())
    }

    @Test
    fun updateToDefaultDropsTheKey() {
        val root = scene(entity(0, """"PositionComponent":{"localScale":{"x":1,"y":2,"z":1}}"""))
        assertEquals(EditResult.Changed, editor.update(root, "0", "PositionComponent", "localScale.y", "1"))
        assertFalse(components(root, 0)["PositionComponent"].has("localScale"))
    }

    @Test
    fun updateToSameValueIsUnchanged() {
        val root = scene(entity(0, """"CameraComponent":{"camera":{"far":200,"near":0.5,"fieldOfView":60}}"""))
        val before = root.toString()
        assertEquals(EditResult.Unchanged, editor.update(root, "0", "CameraComponent", "camera.fieldOfView", "60"))
        assertEquals(before, root.toString())
    }

    @Test
    fun updateNameAndType() {
        val root = scene(entity(0, """"NameComponent":{"name":"a"},"TypeComponent":{"type":"OBJECT"}"""))
        assertEquals(EditResult.Changed, editor.update(root, "0", "NameComponent", "name", "b"))
        assertEquals(EditResult.Changed, editor.update(root, "0", "TypeComponent", "type", "GROUP"))
        assertRejected(editor.update(root, "0", "TypeComponent", "type", "NOPE"))
        assertEquals("b", components(root, 0)["NameComponent"]["name"].asText())
        assertEquals("GROUP", components(root, 0)["TypeComponent"]["type"].asText())
    }

    @Test
    fun lookAtMustNameAnEntityOrNone() {
        val root = scene(entity(0, """"PositionComponent":{"lookAtId":1}"""), entity(1, """"PositionComponent":{}"""))
        val before = root.toString()
        assertRejected(editor.update(root, "0", "PositionComponent", "lookAtId", "99"))
        assertRejected(editor.update(root, "0", "PositionComponent", "lookAtId", "x"))
        assertEquals(before, root.toString())
        assertEquals(EditResult.Changed, editor.update(root, "0", "PositionComponent", "lookAtId", "-1"))
        assertFalse(components(root, 0)["PositionComponent"].has("lookAtId"))
    }

    @Test
    fun parentCyclesAreRejected() {
        val root = scene(
            entity(1, """"ParentComponent":{"parentEntityId":2}"""),
            entity(2, """"ParentComponent":{}"""),
            entity(3, """"ParentComponent":{"parentEntityId":2}"""),
        )
        assertRejected(editor.update(root, "2", "ParentComponent", "parentEntityId", "1"))
        assertRejected(editor.update(root, "2", "ParentComponent", "parentEntityId", "2"))
        assertEquals(EditResult.Changed, editor.update(root, "1", "ParentComponent", "parentEntityId", "3"))
        assertRejected(editor.update(root, "2", "ParentComponent", "parentEntityId", "1"))
    }

    @Test
    fun removeKeepsTheOtherComponentsInOrder() {
        val root = scene(entity(0, """"NameComponent":{},"LightComponent":{"light":{}},"TypeComponent":{}"""))
        assertEquals(EditResult.Changed, editor.remove(root, "0", "LightComponent"))
        assertEquals(listOf("NameComponent", "TypeComponent"), components(root, 0).fieldNames().asSequence().toList())
        assertRejected(editor.remove(root, "0", "LightComponent"))
    }

    @Test
    fun removeRefusedWhileReferenced() {
        val root = scene(
            entity(3, """"PositionComponent":{}"""),
            entity(5, """"PositionComponent":{"lookAtId":3}"""),
        )
        val before = root.toString()
        val result = editor.remove(root, "3", "PositionComponent")
        assertRejected(result)
        assertTrue((result as EditResult.Rejected).reason.contains("5"))
        assertEquals(before, root.toString())
        assertEquals(EditResult.Changed, editor.remove(root, "5", "PositionComponent"))
        assertEquals(EditResult.Changed, editor.remove(root, "3", "PositionComponent"))
    }

    @Test
    fun unmodeledComponentsAreNotEditable() {
        val root = scene(entity(0, """"PickableComponent":{"x":1}"""))
        assertRejected(editor.remove(root, "0", "PickableComponent"))
        assertRejected(editor.update(root, "0", "PickableComponent", "x", "2"))
        assertEquals(null, editor.read(root, "0", "PickableComponent"))
    }

    @Test
    fun readListsFieldValues() {
        val root = scene(entity(0, """"LightComponent":{"light":{"color":{"r":0.5,"g":1,"b":1,"a":1},"intensity":2}}"""))
        val values = editor.read(root, "0", "LightComponent")!!.associate { it.field to it.value }
        assertEquals("0.5", values["color.r"])
        assertEquals("2", values["intensity"])
    }

    @Test
    fun mainSceneEditsKeepEverythingElse() {
        val file = File("src/test/testData/project/Untitled/scenes/Main Scene.scene")
        val original = SceneJson.parse(file.readText())
        val root = original.deepCopy<JsonNode>()
        assertEquals(EditResult.Changed, editor.add(root, "0", "LightComponent"))
        assertEquals(EditResult.Changed, editor.update(root, "4", "CameraComponent", "camera.fieldOfView", "50"))
        assertEquals(EditResult.Changed, editor.remove(root, "0", "LightComponent"))
        // camera field changed; everything else equals the original
        (root["ecs"]["4"]["components"]["CameraComponent"]["camera"] as com.fasterxml.jackson.databind.node.ObjectNode)
            .set<JsonNode>("fieldOfView", original["ecs"]["4"]["components"]["CameraComponent"]["camera"]["fieldOfView"])
        assertEquals(original.toString(), root.toString())
    }

    @Test
    fun editedMainSceneLoadsWithoutNewWarnings() {
        val original = SceneJson.parse(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText())
        val root = original.deepCopy<JsonNode>()
        editor.add(root, "0", "LightComponent")
        editor.update(root, "4", "CameraComponent", "camera.near", "0.25")
        editor.update(root, "0", "PositionComponent", "localPosition.y", "2.5")
        val configurator = EcsConfigurator(
            net.nevinsky.abyssus.core.JsonProcessor().mapper,
            FolderAssetResolver(File("src/test/testData/project/Untitled/assets").list().orEmpty().toList()),
            org.slf4j.helpers.NOPLogger.NOP_LOGGER,
            net.nevinsky.abyssus.runtime.schema.GameComponents(),
        )
        val before = configurator.load(original["ecs"])
        val after = configurator.load(root["ecs"])
        assertEquals(before.document.warnings, after.document.warnings)
        assertEquals(before.engine.entities.size(), after.engine.entities.size())
    }

    @Test
    fun anUnrelatedPositionEditKeepsTheOriginalLookAtNode() {
        for (node in listOf("3", "\"3\"", "\"-1\"", "\"h\"")) {
            val root = scene(entity(0, """"PositionComponent":{"lookAtId":$node,"localPosition":{"x":1}}"""))
            assertEquals(EditResult.Changed, editor.update(root, "0", "PositionComponent", "localPosition.x", "5"))
            assertEquals(node, SceneJson.parse(node), components(root, 0)["PositionComponent"]["lookAtId"])
            assertEquals(5, components(root, 0)["PositionComponent"]["localPosition"]["x"].asInt())
        }
    }

    // ---- schema-declared components, against the Custom fixture and its exported schema ----

    private val customDir = "src/test/testData/project/Custom"
    private val planeEditor = ComponentEditor(SchemaFile().parse(File("$customDir/abyssus/components.schema.json").readText()).components)
    private fun customScene() = SceneJson.parse(File("$customDir/scenes/Field.scene").readText())

    @Test
    fun addAPlaneWritesAnEmptyObjectAndNoIdentifierTable() {
        val root = customScene()
        val original = root.deepCopy<JsonNode>()
        assertEquals(EditResult.Changed, planeEditor.add(root, "1", "PlaneComponent"))
        assertEquals("{}", components(root, 1)["PlaneComponent"].toString())
        (root["ecs"]["entities"]["1"]["components"] as com.fasterxml.jackson.databind.node.ObjectNode).remove("PlaneComponent")
        assertEquals(original.toString(), root.toString())
        assertFalse(root.toString().contains("componentIdentifiers"))
        assertRejected(planeEditor.add(root, "0", "PlaneComponent"))
    }

    @Test
    fun aPlaneFieldIsWrittenAndALimitIsRefusedNamingTheMinimum() {
        val root = customScene()
        assertEquals(EditResult.Changed, planeEditor.update(root, "0", "PlaneComponent", "lineLength", "25"))
        assertEquals("""{"lineLength":25,"kind":"STUNT"}""", components(root, 0)["PlaneComponent"].toString())
        val before = root.toString()
        val result = planeEditor.update(root, "0", "PlaneComponent", "lineLength", "2")
        assertRejected(result)
        assertTrue((result as EditResult.Rejected).reason, result.reason.contains("lineLength") && result.reason.contains("minimum 5"))
        assertRejected(planeEditor.update(root, "0", "PlaneComponent", "lineLength", "31"))
        assertRejected(planeEditor.update(root, "0", "PlaneComponent", "kind", "JET"))
        assertRejected(planeEditor.update(root, "0", "PlaneComponent", "fuelSeconds", "1.5"))
        assertRejected(planeEditor.update(root, "0", "PlaneComponent", "hasTipWeight", "yes"))
        assertEquals(before, root.toString())
        assertEquals(EditResult.Changed, planeEditor.update(root, "0", "PlaneComponent", "lineLength", "18"))
        assertEquals("""{"kind":"STUNT"}""", components(root, 0)["PlaneComponent"].toString())
    }

    @Test
    fun aVectorAxisBelowAnExclusiveMinimumIsRefused() {
        val box = net.nevinsky.abyssus.runtime.schema.ComponentSchema(
            "BoxComponent", "", "Box",
            listOf(net.nevinsky.abyssus.runtime.schema.SchemaField(
                "halfExtents", "Half extents", net.nevinsky.abyssus.runtime.schema.FieldType.VECTOR,
                net.nevinsky.abyssus.runtime.schema.SchemaVector(0.5f, 0.5f, 0.5f), min = 0.0, minExclusive = true,
            )),
        )
        val boxEditor = ComponentEditor(listOf(box))
        val root = scene(entity(0, """"BoxComponent":{}"""))
        val result = boxEditor.update(root, "0", "BoxComponent", "halfExtents.y", "0")
        assertRejected(result)
        assertTrue((result as EditResult.Rejected).reason, result.reason.contains("halfExtents.y") && result.reason.contains("greater than 0"))
        assertEquals("{}", components(root, 0)["BoxComponent"].toString())
        assertEquals(EditResult.Changed, boxEditor.update(root, "0", "BoxComponent", "halfExtents.x", "1"))
        assertEquals("""{"halfExtents":{"x":1,"y":0.5,"z":0.5}}""", components(root, 0)["BoxComponent"].toString())
    }

    @Test
    fun aPlaneEntityReferenceMustNameAnEntityOfTheScene() {
        val root = customScene()
        val before = root.toString()
        assertRejected(planeEditor.update(root, "0", "PlaneComponent", "pilot", "99"))
        assertEquals(before, root.toString())
        assertEquals(EditResult.Changed, planeEditor.update(root, "0", "PlaneComponent", "pilot", "1"))
        assertEquals(1, components(root, 0)["PlaneComponent"]["pilot"].asInt())
    }

    @Test
    fun everyPlaneFieldKindEdits() {
        val root = customScene()
        assertEquals(EditResult.Changed, planeEditor.update(root, "0", "PlaneComponent", "hasTipWeight", "false"))
        assertEquals(EditResult.Changed, planeEditor.update(root, "0", "PlaneComponent", "fuelSeconds", "90"))
        assertEquals(EditResult.Changed, planeEditor.update(root, "0", "PlaneComponent", "leadout.y", "0.5"))
        assertEquals(EditResult.Changed, planeEditor.update(root, "0", "PlaneComponent", "paint.g", "0"))
        assertEquals(EditResult.Changed, planeEditor.update(root, "0", "PlaneComponent", "name", "Ace"))
        assertRejected(planeEditor.update(root, "0", "PlaneComponent", "model", "rock", assetsByType = mapOf("MODEL" to setOf("tree"))))
        assertEquals(EditResult.Changed, planeEditor.update(root, "0", "PlaneComponent", "model", "tree", assetsByType = mapOf("MODEL" to setOf("tree"))))
        assertEquals(
            """{"lineLength":22,"kind":"STUNT","hasTipWeight":false,"fuelSeconds":90,"leadout":{"x":0,"y":0.5,"z":-0.3},"paint":{"r":1,"g":0,"b":1,"a":1},"name":"Ace","model":"tree"}""",
            components(root, 0)["PlaneComponent"].toString(),
        )
        val values = planeEditor.read(root, "0", "PlaneComponent")!!.associateBy { it.field }
        assertEquals(FieldKind.BOOLEAN, values["hasTipWeight"]!!.kind)
        assertEquals("Line length", values["lineLength"]!!.label)
        assertEquals("Lines", values["leadout.x"]!!.group)
        assertEquals("0.5", values["leadout.y"]!!.value)
    }

    @Test
    fun theAddListOffersThePlaneButNeverUnmodeledKinds() {
        val root = customScene()
        val missing = planeEditor.missingKinds(root, "1").map { it.name }
        assertTrue(missing.toString(), "PlaneComponent" in missing)
        assertFalse("PickableComponent" in missing)
        assertFalse("PlaneComponent" in planeEditor.missingKinds(root, "0").map { it.name })
        assertEquals("Plane", planeEditor.kindOf("PlaneComponent")!!.label)
        assertEquals(null, editor.kindOf("PlaneComponent"))
    }

    @Test
    fun removeAPlaneKeepsTheRestInOrder() {
        val root = customScene()
        assertEquals(EditResult.Changed, planeEditor.remove(root, "0", "PlaneComponent"))
        assertEquals(listOf("NameComponent", "TypeComponent", "PositionComponent", "RenderComponent"), components(root, 0).fieldNames().asSequence().toList())
    }
}
