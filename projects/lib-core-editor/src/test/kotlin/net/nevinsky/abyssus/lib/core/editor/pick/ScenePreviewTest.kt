/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import java.io.File
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.Pose
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScenePreviewTest {
    private val mundus =
        sceneContentOf(parseScene(File("src/test/testData/project/Lights/scenes/Abyssus Lights.scene").readText()))

    @Test
    fun turningAHandleAimedLightMovesItsHandle() {
        val turned =
            DragResult(
                PlacementTransform(Vector3(0f, 10f, 0f), Quaternion(0f, 0f, 0f, 1f), Vector3(1f, 1f, 1f)),
                Vector3(0f, 0f, -1f),
            )
        val previewed = ScenePreview().apply(mundus, 1L, turned)
        // The light keeps facing the turned direction and stays where it is.
        assertEquals(Vector3(0f, 0f, -1f), previewed.lights.first { it.entityId == 1L }.direction)
        assertEquals(Vector3(0f, 10f, 0f), previewed.lights.first { it.entityId == 1L }.position)
        // The handle moves to keep the same distance along the turned direction.
        assertEquals(Vector3(0f, 10f, -10f), previewed.entityPositions[0L])
        // The other light and its handle are untouched.
        assertEquals(Vector3(0f, -1f, 0f), previewed.lights.first { it.entityId == 4L }.direction)
        assertEquals(Vector3(0f, 0f, 0f), previewed.entityPositions[3L])
    }

    @Test
    fun movingALookAtLightCarriesItsChildHandle() {
        val moved =
            DragResult(
                PlacementTransform(Vector3(0f, 20f, 0f), Quaternion(), Vector3(1f, 1f, 1f)),
                null,
            )
        val previewed = ScenePreview().apply(mundus, 1L, moved)
        // The light is now above its handle and faces it, straight down.
        assertEquals(Vector3(0f, 20f, 0f), previewed.lights.first { it.entityId == 1L }.position)
        assertEquals(Vector3(0f, -1f, 0f), previewed.lights.first { it.entityId == 1L }.direction)
        // The handle is the light's child: it moves with the light, so the light keeps its aim.
        assertEquals(Vector3(0f, 10f, 0f), previewed.entityPositions[0L])
    }

    @Test
    fun aLightWithoutTargetUsesTheDraggedDirection() {
        val noTarget =
            sceneContentOf(
                parseScene(
                    """{"format":"abyssus","formatVersion":1,"ecs":{
            "1":{"type":"LIGHT_DIRECTIONAL","components":{"LightComponent":{},
"PositionComponent":{"localPosition":{"y":10}}}}}}"""
                )
            )
        val turned =
            DragResult(
                PlacementTransform(Vector3(0f, 10f, 0f), Quaternion(0f, 0f, 0f, 1f), Vector3(1f, 1f, 1f)),
                Vector3(0f, 0f, -1f),
            )
        val previewed = ScenePreview().apply(noTarget, 1L, turned)
        assertEquals(Vector3(0f, 0f, -1f), previewed.lights.single().direction)
        // The light itself is in entityPositions and did not move.
        assertEquals(1, previewed.entityPositions.size)
        assertEquals(Vector3(0f, 10f, 0f), previewed.entityPositions[1L])
    }

    @Test
    fun aimedTargetIsANullForALightWithoutATurnedDirection() {
        val moved =
            DragResult(
                PlacementTransform(Vector3(0f, 10f, 0f), Quaternion(), Vector3(1f, 1f, 1f)),
                null,
            )
        assertNull(ScenePreview().aimedTarget(mundus, 1L, moved))
    }

    @Test
    fun aimedTargetIsANullWhenTheLightLooksAtANonHandle() {
        val aimedAtModel =
            sceneContentOf(
                parseScene(
                    """{"format":"abyssus","formatVersion":1,"ecs":{
            "0":{"components":{"RenderComponent":{"type":"MODEL","assetName":"a"},
                "PositionComponent":{"localPosition":{"x":1}}}},
            "1":{"type":"LIGHT_DIRECTIONAL","components":{"LightComponent":{},"PositionComponent":{"lookAtId":0,
"localPosition":{"x":5}}}}}}"""
                )
            )
        val turned =
            DragResult(
                PlacementTransform(Vector3(5f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f), Vector3(1f, 1f, 1f)),
                Vector3(-1f, 0f, 0f),
            )
        assertNull(ScenePreview().aimedTarget(aimedAtModel, 1L, turned))
    }

    @Test
    fun aimedTargetUsesADistanceOfOneWhenTheHandleIsAtTheLight() {
        val atLight =
            sceneContentOf(
                parseScene(
                    """{"format":"abyssus","formatVersion":1,"ecs":{
            "2":{"type":"HANDLE","components":{"PositionComponent":{"localPosition":{"x":5}}}},
            "1":{"type":"LIGHT_DIRECTIONAL","components":{"LightComponent":{},"PositionComponent":{"lookAtId":2,
"localPosition":{"x":5}}}}}}"""
                )
            )
        val turned =
            DragResult(
                PlacementTransform(Vector3(5f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f), Vector3(1f, 1f, 1f)),
                Vector3(0f, 0f, -1f),
            )
        val target = ScenePreview().aimedTarget(atLight, 1L, turned)
        assertNotNull(target)
        assertEquals(Vector3(5f, 0f, -1f), target!!)
    }

    @Test
    fun movingAPointLightDoesNotReAimIt() {
        val point =
            sceneContentOf(
                parseScene(
                    """{"format":"abyssus","formatVersion":1,"ecs":{
            "1":{"type":"LIGHT_POINT","components":{"LightComponent":{},"PositionComponent":{"lookAtId":2,
"localPosition":{"y":10}}}},
            "2":{"type":"HANDLE","components":{"PositionComponent":{}}}}}"""
                )
            )
        val moved = DragResult(PlacementTransform(Vector3(5f, 10f, 0f), Quaternion(), Vector3(1f, 1f, 1f)), null)
        val before = point.lights.single().direction
        assertEquals(before, ScenePreview().apply(point, 1L, moved).lights.single().direction)
    }

    // ---- pose overrides (Play) ----

    private val untitled =
        sceneContentOf(parseScene(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()))

    @Test
    fun aPoseOverrideReplacesAPlacementAndKeepsItsScale() {
        val authored = untitled.models.single { it.entityId == 0L }
        val pose = Pose(Vector3(1f, 0.5f, 2f), Quaternion(0f, 0.70710677f, 0f, 0.70710677f))
        val posed = ScenePreview().withPoses(untitled, mapOf(0L to pose))
        val model = posed.models.single { it.entityId == 0L }
        assertEquals(PlacementTransform(pose.position, pose.rotation, authored.transform.scale), model.transform)
        assertEquals(pose.position, posed.entityPositions[0L])
        // the other entities keep their authored placements
        assertEquals(untitled.models.filter { it.entityId != 0L }, posed.models.filter { it.entityId != 0L })
        assertEquals(untitled.terrains, posed.terrains)
        // an entity the scene lacks is ignored
        assertEquals(untitled, ScenePreview().withPoses(untitled, mapOf(99L to pose)))
    }

    @Test
    fun aDragPreviewStillWorksOverPoses() {
        val posed = ScenePreview().withPoses(untitled, mapOf(0L to Pose(Vector3(1f, 0.5f, 2f), Quaternion())))
        val dragged = DragResult(PlacementTransform(Vector3(5f, 5f, 5f), Quaternion(), Vector3(1f, 1f, 1f)), null)
        val previewed = ScenePreview().apply(posed, mapOf(2L to dragged))
        assertEquals(Vector3(5f, 5f, 5f), previewed.models.single { it.entityId == 2L }.transform.position)
        assertEquals(Vector3(1f, 0.5f, 2f), previewed.models.single { it.entityId == 0L }.transform.position)
    }
}
