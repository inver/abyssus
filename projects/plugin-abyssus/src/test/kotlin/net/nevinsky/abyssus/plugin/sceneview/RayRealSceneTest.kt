/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneAssetState
import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneConversion
import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneFrame
import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneSnapshots
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.plugin.AssetLoading
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderSource
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.raytracing.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor

/**
 * The fixture project's Main Scene is a real scene: a few hundred model parts, a dozen blended panes and several
 * 2048x2048 textures. It once failed ray tracing with "RESOURCE_LIMIT: Scene exceeds instance, triangle or byte limits"
 * because the caps were sized for toy scenes. These tests keep it working. They assert nothing about the fixture's exact
 * contents, only that the stable native scene snapshot stays inside the bounds and renders using real binary assets.
 */
class RayRealSceneTest {
    private val project = File("src/test/testData/project/Untitled").absoluteFile

    private fun snapshot(): RaySceneFrame {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val loading = AssetLoading(JsonProcessor(), printingLog, Executor(Runnable::run), ShaderSource("/shader/sky", AssetLoading::class.java))
        val content = sceneContentOf(parseScene(File("src/test/testData/project/Tree/scenes/Main Scene.scene").readText()))
        val assets = raySceneAssetsOf(ViewAssets(loading))
        assets.update(project, content)
        val state = assets.poll()
        assertTrue("the scene's assets must load: $state", state is RaySceneAssetState.Ready)
        val camera = PerspectiveCamera(60f, 160f, 90f).apply { position.set(0f, 60f, 140f); lookAt(0f, 0f, 0f); near = .5f; far = 3000f; update() }
        val params = SceneRenderParams.DEFAULT.copy(content = content, projectDir = project)
        val conversion = RaySceneSnapshots().capture(params, camera, NO_LIGHTS, state, activeCameraId = null)
        assertTrue("a real scene must convert with the default limits: $conversion", conversion is RaySceneConversion.Ready)
        return (conversion as RaySceneConversion.Ready).frame
    }

    @Test fun theFixtureSceneIsInsideEveryRayTracingBound() {
        val scene = snapshot().scene
        assertNull("the whole-view fallback must not trigger for the fixture scene", scene.unsupportedReason())
        assertTrue("the scene is bigger than the old 128-instance cap", scene.instances.size > 128)
        assertTrue("its textures stay as bytes", scene.textures.isNotEmpty() && scene.textures.all { it.isBytes })
        assertTrue(scene.instances.size <= RAY_INSTANCE_CAPACITY)
    }

    @Test fun theFixtureSceneRendersThroughTheRealMetalBackend() {
        assumeTrue(System.getProperty("abyssus.metalTests") == "true" && System.getProperty("os.name").startsWith("Mac"))
        renderThrough(MetalRayBackendFactory())
    }

    @Test fun theFixtureSceneRendersThroughTheRealVulkanBackend() {
        assumeTrue(System.getProperty("abyssus.vulkanTests") == "true")
        renderThrough(VulkanRayBackendFactory())
    }

    private fun renderThrough(provider: RayBackendProvider) {
        val frame = snapshot()
        val result = provider.probe()
        assumeTrue("Ray tracing is not available here: $result", result is RayCapability.Available)
        (result as RayCapability.Available).backend.use { backend ->
            backend.openSession("real-scene", RayLimits(maxInstances = backend.capabilities.maxInstances)).use { session ->
                session.submit(RaySceneRequest(RayFrameKey(1, 1, 1, 1), 160, 90, frame.camera.rayCamera(), frame.scene))
                val deadline = System.nanoTime() + 30_000_000_000L
                var rendered: RayFrame? = null
                while (rendered == null && System.nanoTime() < deadline) { rendered = session.poll(); Thread.sleep(2) }
                val image = checkNotNull(rendered) { "the real scene did not render within 30 seconds" }
                val colors = image.colorValues()
                val summary = "${colors.toSet().size} distinct values in [${colors.min()}, ${colors.max()}], ${image.depthValues().count { it < 1f }} of ${image.depthValues().size} pixels hit"
                assertTrue("something of the scene is hit: $summary", image.depthValues().any { it < 1f })
                // not a fixture-specific colour count: the scene's pixels must differ from the background's, and nothing may be flat zero
                val depth = image.depthValues()
                val sceneColor = colors.copyOfRange(4 * depth.indexOfFirst { it < 1f }, 4 * depth.indexOfFirst { it < 1f } + 3)
                val background = depth.indexOfFirst { it >= 1f }
                assertTrue("the scene is not drawn in one flat colour: $summary", colors.toSet().size > 1)
                if (background >= 0) assertFalse("scene and background look the same: $summary", sceneColor.contentEquals(colors.copyOfRange(4 * background, 4 * background + 3)))
                assertTrue(image.colorValues().all { it.isFinite() })
            }
        }
    }

    /** Metal and Vulkan both hold this many instances per session. */
    private companion object { const val RAY_INSTANCE_CAPACITY = 1024 }
}
