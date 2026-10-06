/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.scene

import net.nevinsky.abyssus.editor.ResourceEditorMessages
import net.nevinsky.abyssus.editor.content.Rgba
import net.nevinsky.abyssus.editor.content.LightKind

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.editor.components.ComponentEditor
import net.nevinsky.abyssus.editor.document.SceneJson
import net.nevinsky.abyssus.editor.parseScene
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The scene view and the Properties panel read an entity through the same codecs, so they show the same values. */
class SceneEntityParityTest {
    private val fixture = File("src/test/testData/project/Tree/scenes/Main Scene.scene")

    /** A copy of `Main Scene` in memory: entity 7 gets a LightComponent and a spotlight 8 omits range, cone and softness. */
    private fun scene(): ObjectNode {
        val root = SceneJson().parse(fixture.readText()) as ObjectNode
        val entities = root.get("ecs") as ObjectNode
        (entities.get("7").get("components") as ObjectNode).set<JsonNode>(
            "LightComponent",
            SceneJson().parse("""{"light":{"color":{"r":1,"g":0.96,"b":0.84,"a":1},"intensity":1.2}}"""),
        )
        entities.set<JsonNode>(
            "8",
            SceneJson().parse(
                """{"archetype":1,"components":{"NameComponent":{"name":"Spot Light 8"},"TypeComponent":{"type":"LIGHT_SPOT"},
                "PositionComponent":{"localPosition":{"x":1,"y":5,"z":2}},
                "LightComponent":{"light":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1}}}}""",
            ),
        )
        return root
    }

    private fun value(root: JsonNode, id: String, kind: String, field: String): Float =
        ComponentEditor(ResourceEditorMessages()).read(root, id, kind)!!.single { it.field == field }.value.toFloat()

    @Test
    fun lightPlacementsEqualThePanelValues() {
        val root = scene()
        val content = sceneContentOf(parseScene(root.toString()))
        assertEquals(setOf("7", "8"), content.lights.map { it.entityId }.toSet())
        for (light in content.lights) {
            val id = light.entityId
            fun v(field: String) = value(root, id, "LightComponent", field)
            assertEquals(id, Rgba(v("color.r"), v("color.g"), v("color.b"), 1f), light.color)
            assertEquals(id, v("intensity"), light.intensity, 0f)
            if (light.kind == LightKind.SPOT) {
                assertEquals(id, v("range"), light.range, 0f)
                assertEquals(id, v("coneAngle"), light.coneAngle, 0f)
                assertEquals(id, v("edgeSoftness") / 100f, light.edgeSoftness, 1e-6f) // the panel shows percent
            }
        }
        val spot = content.lights.single { it.entityId == "8" }
        assertEquals(100f, spot.range, 0f)
        assertEquals(45f, spot.coneAngle, 0f)
        assertEquals(0.2f, spot.edgeSoftness, 0f)
        assertEquals(1.2f, content.lights.single { it.entityId == "7" }.intensity, 0f)
    }

    @Test
    fun cameraAndModelPlacementsEqualThePanelValues() {
        val root = scene()
        val content = sceneContentOf(parseScene(root.toString()))
        val camera = content.cameras.single()
        fun cam(field: String) = value(root, camera.entityId, "CameraComponent", field)
        assertEquals(cam("camera.near"), camera.near, 0f)
        assertEquals(cam("camera.far"), camera.far, 0f)
        assertEquals(cam("camera.fieldOfView"), camera.fieldOfView, 0f)
        for (model in content.models) {
            fun pos(field: String) = value(root, model.entityId, "PositionComponent", field)
            val p = model.transform.position
            assertEquals(Triple(pos("localPosition.x"), pos("localPosition.y"), pos("localPosition.z")), Triple(p.x, p.y, p.z))
            val q = model.transform.rotation
            assertEquals(listOf(pos("localRotation.x"), pos("localRotation.y"), pos("localRotation.z"), pos("localRotation.w")), listOf(q.x, q.y, q.z, q.w))
            val s = model.transform.scale
            assertEquals(Triple(pos("localScale.x"), pos("localScale.y"), pos("localScale.z")), Triple(s.x, s.y, s.z))
        }
    }

    @Test
    fun aTextualHandleTargetIsReadTheSameWay() {
        val root = scene()
        val entities = root.get("ecs") as ObjectNode
        entities.set<JsonNode>("h", SceneJson().parse("""{"archetype":1,"components":{"TypeComponent":{"type":"HANDLE"},"PositionComponent":{"localPosition":{"y":-5}}}}"""))
        ((entities.get("7").get("components") as ObjectNode).get("PositionComponent") as ObjectNode).put("lookAtId", "h")
        val content = sceneContentOf(parseScene(root.toString()))
        assertEquals("h", content.lights.single { it.entityId == "7" }.lookAtId)
        assertEquals("h", content.aimHandleOf(content.lights.single { it.entityId == "7" }))
    }
}
