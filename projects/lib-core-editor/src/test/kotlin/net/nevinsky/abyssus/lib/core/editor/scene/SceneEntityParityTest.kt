/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.ecs.component.LightDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.parseScene
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The scene view and the Properties panel read an entity through the same codecs, so they show the same values. */
class SceneEntityParityTest {
    private val fixture = File("src/test/testData/project/Tree/scenes/Main Scene.scene")

    /** A copy of `Main Scene` in memory: entity 7 gets a LightComponent and a spotlight 8 omits range, cone and softness. */
    private fun scene(): SceneDto {
        val scene = parseScene(fixture.readText())
        val white = LightDto(intensity = 1.2f).also { it.color.set(1f, 0.96f, 0.84f, 1f) }
        scene.entity(7)!!.add(LightComponent(white))
        scene.ecs[8] = entityOf(
            8, EntityType.LIGHT_SPOT,
            NameComponent("Spot Light 8"), PositionComponent(1f, 5f, 2f), LightComponent(LightDto()),
        )
        return scene
    }

    private fun value(scene: SceneDto, id: Long, kind: String, field: String): Float =
        ComponentEditor(ResourceEditorMessages()).read(scene, id, kind)!!.single { it.field == field }.value.toFloat()

    @Test
    fun lightPlacementsEqualThePanelValues() {
        val scene = scene()
        val content = sceneContentOf(scene)
        assertEquals(setOf(7L, 8L), content.lights.map { it.entityId }.toSet())
        for (light in content.lights) {
            val id = light.entityId
            fun v(field: String) = value(scene, id, "LightComponent", field)
            assertEquals(id.toString(), Color(v("color.r"), v("color.g"), v("color.b"), 1f), light.color)
            assertEquals(id.toString(), v("intensity"), light.intensity, 0f)
            if (light.kind == LightKind.SPOT) {
                assertEquals(id.toString(), v("range"), light.range, 0f)
                assertEquals(id.toString(), v("coneAngle"), light.coneAngle, 0f)
                assertEquals(id.toString(), v("edgeSoftness") / 100f, light.edgeSoftness, 1e-6f) // the panel shows percent
            }
        }
        val spot = content.lights.single { it.entityId == 8L }
        assertEquals(100f, spot.range, 0f)
        assertEquals(45f, spot.coneAngle, 0f)
        assertEquals(0.2f, spot.edgeSoftness, 0f)
        assertEquals(1.2f, content.lights.single { it.entityId == 7L }.intensity, 0f)
    }

    @Test
    fun cameraAndModelPlacementsEqualThePanelValues() {
        val scene = scene()
        val content = sceneContentOf(scene)
        val camera = content.cameras.single()
        fun cam(field: String) = value(scene, camera.entityId, "CameraComponent", field)
        assertEquals(cam("camera.near"), camera.near, 0f)
        assertEquals(cam("camera.far"), camera.far, 0f)
        assertEquals(cam("camera.fieldOfView"), camera.fieldOfView, 0f)
        for (model in content.models) {
            fun pos(field: String) = value(scene, model.entityId, "PositionComponent", field)
            val p = model.transform.position
            assertEquals(Triple(pos("localPosition.x"), pos("localPosition.y"), pos("localPosition.z")), Triple(p.x, p.y, p.z))
            val q = model.transform.rotation
            assertEquals(listOf(pos("localRotation.x"), pos("localRotation.y"), pos("localRotation.z"), pos("localRotation.w")), listOf(q.x, q.y, q.z, q.w))
            val s = model.transform.scale
            assertEquals(Triple(pos("localScale.x"), pos("localScale.y"), pos("localScale.z")), Triple(s.x, s.y, s.z))
        }
    }

    @Test
    fun aNumericStringHandleTargetIsReadTheSameWay() {
        val scene = scene()
        scene.ecs[9] = entityOf(9, EntityType.HANDLE, PositionComponent(0f, -5f, 0f))
        scene.component(7, PositionComponent::class.java)!!.lookAtId = 9
        val content = sceneContentOf(scene)
        assertEquals(9L, content.lights.single { it.entityId == 7L }.lookAtId)
        assertEquals(9L, content.aimHandleOf(content.lights.single { it.entityId == 7L }))
    }
}
