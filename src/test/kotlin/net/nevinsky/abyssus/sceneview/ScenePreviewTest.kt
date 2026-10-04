/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.parseScene
import net.nevinsky.abyssus.sceneview.gizmo.DragResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ScenePreviewTest {
    private val mundus = SceneContent.of(parseScene(File("src/test/testData/project/Lights/scenes/Mundus Lights.scene").readText()))

    @Test
    fun turningAHandleAimedLightMovesItsHandle() {
        val turned = DragResult(
            PlacementTransform(Vec3(0f, 10f, 0f), Quat(0f, 0f, 0f, 1f), Vec3(1f, 1f, 1f)),
            Vec3(0f, 0f, -1f),
        )
        val previewed = ScenePreview.apply(mundus, "1", turned)
        // The light keeps facing the turned direction and stays where it is.
        assertEquals(Vec3(0f, 0f, -1f), previewed.lights.first { it.entityId == "1" }.direction)
        assertEquals(Vec3(0f, 10f, 0f), previewed.lights.first { it.entityId == "1" }.position)
        // The handle moves to keep the same distance along the turned direction.
        assertEquals(Vec3(0f, 10f, -10f), previewed.entityPositions["0"])
        // The other light and its handle are untouched.
        assertEquals(Vec3(0f, -1f, 0f), previewed.lights.first { it.entityId == "4" }.direction)
        assertEquals(Vec3(0f, 0f, 0f), previewed.entityPositions["3"])
    }

    @Test
    fun movingALookAtLightReAimsItAtItsUnmovedTarget() {
        val moved = DragResult(
            PlacementTransform(Vec3(0f, 20f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f)),
            null,
        )
        val previewed = ScenePreview.apply(mundus, "1", moved)
        // The light is now above its handle and faces it, straight down.
        assertEquals(Vec3(0f, 20f, 0f), previewed.lights.first { it.entityId == "1" }.position)
        assertEquals(Vec3(0f, -1f, 0f), previewed.lights.first { it.entityId == "1" }.direction)
        // The handle did not move with the light.
        assertEquals(Vec3(0f, 0f, 0f), previewed.entityPositions["0"])
    }

    @Test
    fun aLightWithoutTargetUsesTheDraggedDirection() {
        val noTarget = SceneContent.of(parseScene("""{"ecs":{"entities":{
            "1":{"components":{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{},
                "PositionComponent":{"localPosition":{"y":10}}}}}}}"""))
        val turned = DragResult(
            PlacementTransform(Vec3(0f, 10f, 0f), Quat(0f, 0f, 0f, 1f), Vec3(1f, 1f, 1f)),
            Vec3(0f, 0f, -1f),
        )
        val previewed = ScenePreview.apply(noTarget, "1", turned)
        assertEquals(Vec3(0f, 0f, -1f), previewed.lights.single().direction)
        // The light itself is in entityPositions and did not move.
        assertEquals(1, previewed.entityPositions.size)
        assertEquals(Vec3(0f, 10f, 0f), previewed.entityPositions["1"])
    }

    @Test
    fun aimedTargetIsANullForALightWithoutATurnedDirection() {
        val moved = DragResult(
            PlacementTransform(Vec3(0f, 10f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f)),
            null,
        )
        assertNull(ScenePreview.aimedTarget(mundus, "1", moved))
    }

    @Test
    fun aimedTargetIsANullWhenTheLightLooksAtANonHandle() {
        val aimedAtModel = SceneContent.of(parseScene("""{"ecs":{"entities":{
            "m":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"a"}}},
                "PositionComponent":{"localPosition":{"x":1}}}},
            "l":{"components":{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{},
                "PositionComponent":{"lookAtId":"m","localPosition":{"x":5}}}}}}}"""))
        val turned = DragResult(
            PlacementTransform(Vec3(5f, 0f, 0f), Quat(0f, 0f, 0f, 1f), Vec3(1f, 1f, 1f)),
            Vec3(-1f, 0f, 0f),
        )
        assertNull(ScenePreview.aimedTarget(aimedAtModel, "l", turned))
    }

    @Test
    fun aimedTargetUsesADistanceOfOneWhenTheHandleIsAtTheLight() {
        val atLight = SceneContent.of(parseScene("""{"ecs":{"entities":{
            "h":{"components":{"TypeComponent":{"type":"HANDLE"},"PositionComponent":{"localPosition":{"x":5}}}},
            "l":{"components":{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{},
                "PositionComponent":{"lookAtId":"h","localPosition":{"x":5}}}}}}}"""))
        val turned = DragResult(
            PlacementTransform(Vec3(5f, 0f, 0f), Quat(0f, 0f, 0f, 1f), Vec3(1f, 1f, 1f)),
            Vec3(0f, 0f, -1f),
        )
        val target = ScenePreview.aimedTarget(atLight, "l", turned)
        assertNotNull(target)
        assertEquals(Vec3(5f, 0f, -1f), target!!)
    }

    @Test
    fun movingAPointLightDoesNotReAimIt() {
        val point = SceneContent.of(parseScene("""{"ecs":{"entities":{
            "1":{"components":{"TypeComponent":{"type":"LIGHT_POINT"},"LightComponent":{},
                "PositionComponent":{"lookAtId":2,"localPosition":{"y":10}}}},
            "2":{"components":{"TypeComponent":{"type":"HANDLE"},"PositionComponent":{}}}}}}"""))
        val moved = DragResult(PlacementTransform(Vec3(5f, 10f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f)), null)
        val before = point.lights.single().direction
        assertEquals(before, ScenePreview.apply(point, "1", moved).lights.single().direction)
    }
}
