/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.editor.ray.rayTestModel
import net.nevinsky.abyssus.lib.raytracing.*
import org.junit.Assert.*
import org.junit.Test

class RayFieldSceneBuilderTest {
    private val model = rayTestModel()
    private val camera = RayFieldCamera(listOf(0f, 2f, 5f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
    private val sun = RayLight("sun", RayLightKind.DIRECTIONAL, RayColor(1f, 1f, 1f), RayVec3(0f, -1f, 0f))

    private fun inputs(x: Float = 0f, camera: RayFieldCamera = this.camera, asset: net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelSnapshot = model) =
        RayFieldInputs(
            listOf(RayPlacedModel("m0", asset, Matrix4().translate(x, 0f, 0f))), emptyList(), listOf(sun),
            RayColor(.3f, .3f, .3f), RayColor(.6f, .7f, .85f), RayFog(RayColor(.6f, .7f, .85f), .01f, 1.5f), null, camera,
        )

    @Test fun aMovedPlaneKeepsEveryMeshAndTheSceneGeneration() {
        val builder = RayFieldSceneBuilder()
        val first = builder.build(inputs(0f))
        val second = builder.build(inputs(3f))
        assertEquals(first.key.sceneGeneration, second.key.sceneGeneration)
        assertEquals(1, builder.structureChanges)
        assertTrue(first.scene.meshes.indices.all { first.scene.meshes[it] === second.scene.meshes[it] })
        assertTrue("a moved instance is a content change", second.contentRevision > first.contentRevision)
        assertEquals(first.key.cameraRevision, second.key.cameraRevision)
    }

    @Test fun anUnchangedFrameChangesNoRevision() {
        val builder = RayFieldSceneBuilder()
        val first = builder.build(inputs())
        val second = builder.build(inputs())
        assertEquals(first.key, second.key)
        assertEquals(first.contentRevision, second.contentRevision)
        assertEquals(first.environmentRevision, second.environmentRevision)
    }

    @Test fun aMovedCameraOnlyChangesTheCameraRevision() {
        val builder = RayFieldSceneBuilder()
        val first = builder.build(inputs())
        val moved = RayFieldCamera(listOf(1f, 2f, 5f), camera.direction, camera.up, 60f, .1f, 100f)
        val second = builder.build(inputs(camera = moved))
        assertEquals(first.key.sceneGeneration, second.key.sceneGeneration)
        assertEquals(first.key.cameraRevision + 1, second.key.cameraRevision)
        assertEquals(first.contentRevision, second.contentRevision)
    }

    @Test fun aReplacedAssetIsAStructureChange() {
        val builder = RayFieldSceneBuilder()
        val first = builder.build(inputs())
        val second = builder.build(inputs(asset = rayTestModel()))
        assertEquals(first.key.sceneGeneration + 1, second.key.sceneGeneration)
    }

    @Test fun theSceneCarriesTheSunFogAndEnvironment() {
        val frame = RayFieldSceneBuilder().build(inputs())
        assertEquals(listOf(sun), frame.scene.lights)
        assertNotNull(frame.scene.fog)
        assertEquals(RayColor(.6f, .7f, .85f), frame.scene.environment.background)
        assertEquals(2, frame.scene.instances.size)
        assertNull(frame.scene.unsupportedReason())
    }
}
