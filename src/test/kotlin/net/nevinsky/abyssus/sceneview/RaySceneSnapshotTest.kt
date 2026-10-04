/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.assets.model.RayModelSnapshot
import net.nevinsky.abyssus.assets.model.RayModelSnapshotReader
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.model.ModelData
import net.nevinsky.abyssus.core.model.ModelMesh
import net.nevinsky.abyssus.core.model.ModelMeshPart
import net.nevinsky.abyssus.raytracing.RayColor
import net.nevinsky.abyssus.raytracing.RayEnvironment
import net.nevinsky.abyssus.sceneview.gizmo.DragResult
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RaySceneSnapshotTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // the camera's frustum update is native
    }

    private val placement = AssetPlacement("entity", "model", PlacementTransform.IDENTITY)
    private val camera = PerspectiveCamera(61f, 800f, 600f).apply {
        position.set(4f, 5f, 6f); direction.set(-1f, -1f, -1f).nor(); near = .2f; far = 500f; update()
    }
    private val model = model()
    private val assets = RaySceneAssetState.Ready(mapOf("model" to model), emptyMap())
    private fun params(content: SceneContent = SceneContent(models = listOf(placement)), project: String = "project") =
        SceneRenderParams.DEFAULT.copy(content = content, projectDir = File(project))
    private fun ready(params: SceneRenderParams = params(), preview: Map<String, DragResult> = emptyMap(),
        lights: LightSet = LightSet.NONE, active: String? = null, environment: RayEnvironment? = null): RaySceneFrame =
        (RaySceneSnapshots().capture(params, camera, lights, assets, preview, active, environment) as RaySceneConversion.Ready).frame

    @Test fun `drag preview changes only instance transforms and keeps shared geometry`() {
        val p = params(SceneContent(models = listOf(placement, placement.copy(entityId = "other"))))
        val before = ready(p)
        val transform = PlacementTransform(Vec3(20f, 3f, -2f), Quat.IDENTITY, Vec3(2f, 2f, 2f))
        val after = ready(p, mapOf("entity" to DragResult(transform, null)))
        assertEquals(2, after.scene.meshes.size) // two material parts, shared by both entities
        val moved = after.scene.instances.first { it.id.startsWith("entity/") }.transform()
        assertEquals(20f, moved[12], 0f)
        assertEquals(5f, moved[13], 0f) // node local Y 1 scaled by entity 2, then entity Y 3
        assertArrayEquals(before.scene.instances.first { it.id.startsWith("other/") }.transform(),
            after.scene.instances.first { it.id.startsWith("other/") }.transform(), 0f)
        assertEquals(setOf(RaySceneChange.TRANSFORM), RaySceneDiff.between(before, after).changes)
    }

    @Test fun `mesh parts retain materials channels and unsigned 32 bit indices`() {
        val large = model(65_538)
        val frame = (RaySceneSnapshots().capture(params(), camera, LightSet.NONE,
            RaySceneAssetState.Ready(mapOf("model" to large), emptyMap())) as RaySceneConversion.Ready).frame
        assertArrayEquals(intArrayOf(0, 65_536, 65_537), frame.scene.meshes[0].indices())
        assertEquals(65_538 * 3, frame.scene.meshes[0].normals()!!.size)
        assertEquals(65_538 * 2, frame.scene.meshes[0].uvs()!!.size)
        assertEquals(RayColor(1f, 0f, 0f), frame.scene.materials[frame.scene.instances[0].material].baseColor)
        assertEquals(RayColor(0f, 1f, 0f), frame.scene.materials[frame.scene.instances[1].material].baseColor)
        assertEquals(1f, frame.scene.instances[0].transform()[13], 0f)
    }

    @Test fun `selected light identities and order are copied from raster light set`() {
        val sources = (0..7).map { i -> LightPlacement("light-$i", LightKind.POINT, Rgba(1f, .5f, 0f, 1f), 2f,
            Vec3(i.toFloat(), 0f, 0f), Vec3(0f, -1f, 0f), range = 10f) }
        val set = LightSet.of(sources.reversed(), Vec3(0f, 0f, 0f))
        val frame = ready(params(SceneContent(lights = sources)), lights = set)
        assertEquals(set.point.map { it.entityId }, frame.scene.lights.map { it.id })
        assertEquals(5, frame.scene.lights.size)
        assertEquals(20f, frame.scene.lights.first().color.r, 0f) // raster binder multiplies color by range
        val chosen = LightSet(set.directional, set.point.reversed(), set.spot)
        assertEquals(setOf(RaySceneChange.LIGHT), RaySceneDiff.between(frame, ready(params(SceneContent(lights = sources)), lights = chosen)).changes)
    }

    @Test fun `camera look through captures actual view lens and active entity`() {
        val before = ready(active = "camera-a")
        camera.position.set(12f, 13f, 14f); camera.fieldOfView = 43f; camera.near = .7f; camera.far = 700f
        camera.lookAt(0f, 0f, 0f); camera.update()
        val after = ready(active = "camera-b")
        assertEquals("camera-b", after.camera.activeCameraId)
        assertEquals(12f, after.camera.position.x, 0f)
        assertEquals(43f, after.camera.fieldOfView, 0f)
        assertEquals(camera.view.`val`.toList(), after.camera.view)
        assertEquals(setOf(RaySceneChange.CAMERA), RaySceneDiff.between(before, after).changes)
        assertEquals(4f, before.camera.position.x, 0f)
    }

    @Test fun `asset deletion and project replacement require structural rebuild`() {
        val before = ready()
        val removed = ready(params(SceneContent.EMPTY))
        assertTrue(removed.scene.instances.isEmpty())
        assertTrue(RaySceneDiff.between(before, removed).rebuild)
        val replaced = ready(params(project = "replacement"))
        assertTrue(RaySceneDiff.between(before, replaced).rebuild)
    }

    @Test fun `resource limits cause explicit whole scene fallback`() {
        val result = RaySceneSnapshots(RaySnapshotLimits(maxInstances = 1)).capture(params(), camera, LightSet.NONE, assets)
        assertEquals(RaySceneFallback.RESOURCE_LIMIT, (result as RaySceneConversion.Fallback).reason)
        val byteResult = RaySceneSnapshots(RaySnapshotLimits(maxBytes = 1)).capture(params(), camera, LightSet.NONE, assets)
        assertEquals(RaySceneFallback.RESOURCE_LIMIT, (byteResult as RaySceneConversion.Fallback).reason)
    }

    @Test fun `pending and failed displayed assets stay explicit`() {
        val snapshots = RaySceneSnapshots()
        assertTrue(snapshots.capture(params(), camera, LightSet.NONE, RaySceneAssetState.Ready(emptyMap(), emptyMap())) is RaySceneConversion.Preparing)
        val failed = snapshots.capture(params(), camera, LightSet.NONE, RaySceneAssetState.Failed(mapOf("model:model" to IllegalStateException())))
        assertEquals(RaySceneFallback.ASSET_FAILURE, (failed as RaySceneConversion.Fallback).reason)
    }

    @Test fun `environment and fog preserve linear ambient and the density gradient`() {
        val p = params().copy(ambient = Rgba(.2f, .3f, .4f, 1f), fog = FogParams(Rgba(.1f, .2f, .3f, 1f), .02f, 1.7f))
        val frame = ready(p)
        assertEquals(RayColor(.2f, .3f, .4f), frame.scene.environment.ambient)
        assertEquals(.02f, frame.scene.fog!!.density, 0f)
        assertEquals(1.7f, frame.scene.fog!!.gradient, 0f)
        val environment = RayEnvironment(ambient = RayColor(0f, 0f, 0f), hdr = true, intensity = 3f, rotation = 15f)
        val after = ready(p, environment = environment)
        assertEquals(environment, after.scene.environment)
        assertEquals(setOf(RaySceneChange.ENVIRONMENT), RaySceneDiff.between(frame, after).changes)
    }

    @Test fun `companion leases share repeated assets release deletion and replace project`() {
        val opened = mutableListOf<String>(); val closed = mutableListOf<String>()
        val owner = RaySceneAssets({ project, name ->
            val id = "${project.name}:$name"; opened += id
            RayAssetLease({ model }, { null }, { closed += id })
        }, { _, _ -> error("No terrain expected") })
        val repeated = SceneContent(models = listOf(placement, placement.copy(entityId = "second")))
        owner.update(File("first"), repeated); owner.update(File("first"), repeated)
        assertEquals(listOf("first:model"), opened)
        assertTrue(owner.poll() is RaySceneAssetState.Ready)
        owner.update(File("first"), SceneContent.EMPTY)
        assertEquals(listOf("first:model"), closed)
        owner.update(File("first"), repeated); owner.update(File("next"), repeated)
        assertEquals(listOf("first:model", "first:model"), closed)
        owner.close(); owner.close()
        assertEquals(listOf("first:model", "first:model", "next:model"), closed)
    }

    private fun model(count: Int = 3): RayModelSnapshot = rayTestModel(count)
}
