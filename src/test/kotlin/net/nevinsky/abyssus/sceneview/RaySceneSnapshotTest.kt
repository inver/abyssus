/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.scene.CameraParams
import net.nevinsky.abyssus.editor.scene.FogParams
import net.nevinsky.abyssus.editor.scene.SceneContent
import net.nevinsky.abyssus.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.editor.scene.renderParamsOf
import net.nevinsky.abyssus.editor.document.SceneJson

import net.nevinsky.abyssus.editor.document.SceneRaySettingsCodec
import net.nevinsky.abyssus.editor.ray.RaySceneFallback

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Rgba
import net.nevinsky.abyssus.editor.content.Quat
import net.nevinsky.abyssus.editor.content.PlacementTransform
import net.nevinsky.abyssus.editor.content.AssetPlacement
import net.nevinsky.abyssus.editor.content.LightKind
import net.nevinsky.abyssus.editor.content.LightPlacement

import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.core.assets.model.RayModelSnapshot
import net.nevinsky.abyssus.raytracing.RayColor
import net.nevinsky.abyssus.raytracing.RayEnvironment
import net.nevinsky.abyssus.editor.pick.DragResult
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
        lights: LightSet = NO_LIGHTS, active: String? = null, environment: RayEnvironment? = null): RaySceneFrame =
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
        assertEquals(setOf(RaySceneChange.TRANSFORM), raySceneDiff(before, after).changes)
    }

    @Test fun `mesh parts retain materials channels and unsigned 32 bit indices`() {
        val large = model(65_538)
        val frame = (RaySceneSnapshots().capture(params(), camera, NO_LIGHTS,
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
        val set = lightSetOf(sources.reversed(), Vec3(0f, 0f, 0f))
        val frame = ready(params(SceneContent(lights = sources)), lights = set)
        assertEquals(set.point.map { it.entityId }, frame.scene.lights.map { it.id })
        assertEquals(5, frame.scene.lights.size)
        assertEquals(20f, frame.scene.lights.first().color.r, 0f) // raster binder multiplies color by range
        val chosen = LightSet(set.directional, set.point.reversed(), set.spot)
        assertEquals(setOf(RaySceneChange.LIGHT), raySceneDiff(frame, ready(params(SceneContent(lights = sources)), lights = chosen)).changes)
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
        assertEquals(setOf(RaySceneChange.CAMERA), raySceneDiff(before, after).changes)
        assertEquals(4f, before.camera.position.x, 0f)
    }

    @Test fun `asset deletion and project replacement require structural rebuild`() {
        val before = ready()
        val removed = ready(params(SceneContent()))
        assertTrue(removed.scene.instances.isEmpty())
        assertTrue(raySceneDiff(before, removed).rebuild)
        val replaced = ready(params(project = "replacement"))
        assertTrue(raySceneDiff(before, replaced).rebuild)
    }

    @Test fun `resource limits cause explicit whole scene fallback`() {
        val result = RaySceneSnapshots(RaySnapshotLimits(maxInstances = 1)).capture(params(), camera, NO_LIGHTS, assets)
        assertEquals(RaySceneFallback.RESOURCE_LIMIT, (result as RaySceneConversion.Fallback).reason)
        val byteResult = RaySceneSnapshots(RaySnapshotLimits(maxBytes = 1)).capture(params(), camera, NO_LIGHTS, assets)
        assertEquals(RaySceneFallback.RESOURCE_LIMIT, (byteResult as RaySceneConversion.Fallback).reason)
    }

    @Test fun `pending and failed displayed assets stay explicit`() {
        val snapshots = RaySceneSnapshots()
        assertTrue(snapshots.capture(params(), camera, NO_LIGHTS, RaySceneAssetState.Ready(emptyMap(), emptyMap())) is RaySceneConversion.Preparing)
        val failed = snapshots.capture(params(), camera, NO_LIGHTS, RaySceneAssetState.Failed(mapOf("model:model" to IllegalStateException())))
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
        assertEquals(setOf(RaySceneChange.ENVIRONMENT), raySceneDiff(frame, after).changes)
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
        owner.update(File("first"), SceneContent())
        assertEquals(listOf("first:model"), closed)
        owner.update(File("first"), repeated); owner.update(File("next"), repeated)
        assertEquals(listOf("first:model", "first:model"), closed)
        owner.close(); owner.close()
        assertEquals(listOf("first:model", "first:model", "next:model"), closed)
    }

    @Test fun savedSettingsAndOpticsCopyMaterialsWithoutDuplicatingGeometry() {
        val asset = rayTestModel(pbr = true)
        val sources = RaySceneAssetState.Ready(mapOf("model" to asset), emptyMap())
        val p = params(SceneContent(models = listOf(placement, placement.copy(entityId = "second")))).copy(
            ecs = net.nevinsky.abyssus.editor.document.SceneJson().parse("""{"entities":{"entity":{"components":{"RenderComponent":{"rayTracingMaterials":{"red":{"transmission":1,"ior":1.4}}}}}}}"""),
            rayTracing = SceneRaySettingsCodec().read(net.nevinsky.abyssus.editor.document.SceneJson().parse("""{"rayTracing":{"maxReflectionBounces":2}}""")))
        val converter = RaySceneSnapshots()
        val before = (converter.capture(p.copy(ecs = null), camera, NO_LIGHTS, sources) as RaySceneConversion.Ready).frame
        val after = (converter.capture(p, camera, NO_LIGHTS, sources) as RaySceneConversion.Ready).frame
        assertEquals(2, after.scene.meshes.size)
        assertTrue(before.scene.meshes[0] === after.scene.meshes[0])
        val edited = after.scene.instances.first { it.id.startsWith("entity/") }
        val shared = after.scene.instances.first { it.id.startsWith("second/") }
        assertEquals(1f, after.scene.materials[edited.material].transmission, 0f)
        assertEquals(1.4f, after.scene.materials[edited.material].ior, 0f)
        assertEquals(0f, after.scene.materials[shared.material].transmission, 0f)
        assertEquals(setOf(RaySceneChange.MATERIAL), raySceneDiff(before, after).changes)
        assertEquals(2, after.settings.maxReflectionBounces)
    }

    @Test fun malformedSettingsPreventRayConversionButKeepOrdinarySceneParsing() {
        val scene = net.nevinsky.abyssus.editor.parseScene("""{"format":"abyssus","formatVersion":1,"rayTracing":{"maxRefractionBounces":null}}""")
        val p = renderParamsOf(scene, CameraParams.DEFAULT)
        assertNull(p.rayTracing.settings)
        assertTrue(RaySceneSnapshots().capture(p,camera,NO_LIGHTS,assets) is RaySceneConversion.Fallback)
    }

    @Test fun unresolvedOverridesRemainStoredAndNeverRetargetAnotherMaterial() {
        val ecs=net.nevinsky.abyssus.editor.document.SceneJson().parse("""{"entities":{"entity":{"components":{"RenderComponent":{"rayTracingMaterials":{"lost":{"transmission":1}}}}}}}""")
        val p=params().copy(ecs=ecs)
        val before=net.nevinsky.abyssus.editor.document.SceneJson().compact(ecs)
        assertTrue(RaySceneSnapshots().capture(p,camera,NO_LIGHTS,assets) is RaySceneConversion.Fallback)
        assertEquals(before,net.nevinsky.abyssus.editor.document.SceneJson().compact(ecs))
    }

    private fun model(count: Int = 3): RayModelSnapshot = rayTestModel(count)
}
