/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Rgba
import net.nevinsky.abyssus.editor.content.Quat
import net.nevinsky.abyssus.editor.content.PlacementTransform
import net.nevinsky.abyssus.editor.content.AssetPlacement

import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.raytracing.RayColor
import net.nevinsky.abyssus.raytracing.RayEnvironment
import net.nevinsky.abyssus.raytracing.RayFog
import net.nevinsky.abyssus.sceneview.gizmo.DragResult
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RaySceneDiffTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // the camera's frustum update is native
    }

    private val placement = AssetPlacement("entity", "model", PlacementTransform.IDENTITY)
    private val model = rayTestModel()
    private val assets = RaySceneAssetState.Ready(mapOf("model" to model), emptyMap())
    private val camera = PerspectiveCamera(61f, 800f, 600f).apply {
        position.set(4f, 5f, 6f); direction.set(-1f, -1f, -1f).nor(); near = .2f; far = 500f; update()
    }
    private val converter = RaySceneSnapshots()

    private fun frame(
        content: SceneContent = SceneContent(models = listOf(placement)), project: String = "project", preview: Map<String, DragResult> = emptyMap(),
        lights: LightSet = LightSet.NONE, environment: RayEnvironment? = null, view: PerspectiveCamera = camera,
        state: RaySceneAssetState = assets, fog: FogParams? = null, poses: Map<String, RayModelPose> = emptyMap(),
        snapshots: RaySceneSnapshots = converter,
    ): RaySceneFrame = (snapshots.capture(SceneRenderParams.DEFAULT.copy(content = content, projectDir = File(project), fog = fog), view, lights,
        state, preview, environment = environment, poses = poses) as RaySceneConversion.Ready).frame

    private fun moved(x: Float) = mapOf("entity" to DragResult(PlacementTransform(Vec3(x, 0f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f)), null))

    @Test fun theFirstFrameIsAStructuralRebuild() {
        val diff = RaySceneDiff.between(null, frame())
        assertTrue(diff.rebuild)
        assertEquals(setOf(RaySceneChange.STRUCTURE), diff.changes)
    }

    @Test fun anUnchangedSceneChangesNothing() {
        val first = frame()
        val diff = RaySceneDiff.between(first, frame())
        assertEquals(emptySet<RaySceneChange>(), diff.changes)
        assertFalse(diff.rebuild); assertFalse(diff.resetsHistory)
    }

    @Test fun movingAnInstanceIsATransformUpdateNotARebuild() {
        val before = frame()
        val after = frame(preview = moved(3f))
        val diff = RaySceneDiff.between(before, after)
        assertEquals(setOf(RaySceneChange.TRANSFORM), diff.changes)
        assertFalse(diff.rebuild)
        assertTrue(diff.resetsHistory)
        // the converter hands the backend the same mesh objects, so geometry is reused and only the top level updates
        assertTrue(before.scene.meshes.indices.all { before.scene.meshes[it] === after.scene.meshes[it] })
        assertSame(before.scene.textures, before.scene.textures)
    }

    @Test fun anIdenticalAssetKeepsMeshAndTextureIdentityAcrossCaptures() {
        val first = frame(); val second = frame()
        assertEquals(first.scene.meshes.size, second.scene.meshes.size)
        assertTrue(first.scene.meshes.indices.all { first.scene.meshes[it] === second.scene.meshes[it] })
        assertTrue(first.scene.textures.indices.all { first.scene.textures[it] === second.scene.textures[it] })
    }

    @Test fun aReplacedAssetOrProjectRebuilds() {
        val before = frame()
        assertTrue(RaySceneDiff.between(before, frame(project = "other")).rebuild)
        val replaced = RaySceneAssetState.Ready(mapOf("model" to rayTestModel(4)), emptyMap())
        val diff = RaySceneDiff.between(before, frame(state = replaced))
        assertTrue(diff.rebuild)
        assertTrue(RaySceneChange.MATERIAL in diff.changes)
        assertFalse("a replaced asset must not reuse the old mesh", before.scene.meshes[0] === frame(state = replaced).scene.meshes[0])
        val removed = frame(content = SceneContent(models = listOf(placement, placement.copy(entityId = "second"))))
        assertTrue(RaySceneDiff.between(before, removed).rebuild)
    }

    @Test fun poseCameraLightAndEnvironmentChangesAreClassifiedWithoutARebuild() {
        val before = frame()
        val pose = RayModelPose(1, emptyMap(), emptyMap())
        assertEquals(setOf(RaySceneChange.POSE), RaySceneDiff.between(before, frame(poses = mapOf("entity" to pose))).changes)
        val orbit = PerspectiveCamera(61f, 800f, 600f).apply { position.set(5f, 5f, 6f); direction.set(-1f, -1f, -1f).nor(); near = .2f; far = 500f; update() }
        assertEquals(setOf(RaySceneChange.CAMERA), RaySceneDiff.between(before, frame(view = orbit)).changes)
        val env = RayEnvironment(ambient = RayColor(.3f, .3f, .3f))
        assertEquals(setOf(RaySceneChange.ENVIRONMENT), RaySceneDiff.between(before, frame(environment = env)).changes)
        val fogged = RaySceneDiff.between(before, frame(fog = FogParams(Rgba(.5f, .5f, .5f, 1f), .02f, 1f)))
        assertEquals(setOf(RaySceneChange.ENVIRONMENT), fogged.changes)
        assertFalse(fogged.rebuild)
    }
}
