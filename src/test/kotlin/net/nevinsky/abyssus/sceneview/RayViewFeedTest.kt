/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.ray.RayModePhase

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Quat
import net.nevinsky.abyssus.editor.content.PlacementTransform
import net.nevinsky.abyssus.editor.content.AssetPlacement

import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.AssetLoading
import net.nevinsky.abyssus.core.assets.loading.ShaderSource
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.raytracing.RayUnavailableReason
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

class RayViewFeedTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // the camera copy updates its frustum natively
    }

    private val model = rayTestModel()
    private val placement = AssetPlacement("entity", "model", PlacementTransform.IDENTITY)
    private val camera = PerspectiveCamera(61f, 8f, 8f).apply { position.set(4f, 5f, 6f); direction.set(-1f, -1f, -1f).nor(); near = .2f; far = 500f; update() }
    private val device = RayFakeDevice()
    private val service = RayFakeDevice.service("metal" to device)
    private val closers = mutableListOf<AutoCloseable>()

    @After fun close() { closers.forEach(AutoCloseable::close); service.close() }

    private fun assets(failure: Throwable? = null) = RaySceneAssets(
        { _, _ -> RayAssetLease({ if (failure == null) model else null }, { failure }, {}) },
        { _, _ -> error("No terrain expected") },
    )

    private fun feed(executor: Executor = Executor(Runnable::run), assets: RaySceneAssets = assets(), snapshots: RaySceneSnapshots = RaySceneSnapshots(),
        id: String = "view"): RayViewFeed =
        RayViewFeed(service.newView(id), assets, executor, snapshots = snapshots).also { closers += it }

    private fun context(content: SceneContent = SceneContent(models = listOf(placement)), hdrAmbient: FloatArray? = null) =
        RayFrameContext(SceneRenderParams.DEFAULT.copy(content = content, projectDir = File("project")), content, camera, LightSet.NONE, emptyList(), 8, 8, null, hdrAmbient)

    private fun presented(feed: RayViewFeed, content: SceneContent = SceneContent(models = listOf(placement))): RaySceneDisplay? {
        var display: RaySceneDisplay? = null
        RayFakeDevice.await(what = "a ray frame") { display = feed.frame(context(content)); display != null }
        return display
    }

    @Test fun anOffViewReturnsNothingAndNeverProbesAnything() {
        val feed = feed()
        repeat(10) { assertNull(feed.frame(context())) }
        assertEquals(0, device.probes.get())
        assertEquals(RayModePhase.Off, feed.runtime.mode.phase)
    }

    @Test fun anActiveViewPresentsFramesCarryingTheCameraAndContentTheyWereRenderedFor() {
        val feed = feed()
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        val display = presented(feed)!!
        RayFakeDevice.await(what = "the Active phase") { feed.runtime.mode.phase == RayModePhase.Active }
        val metadata = display.metadata
        assertEquals(8, metadata.width); assertEquals(8, metadata.height)
        assertEquals(camera.position.x, metadata.camera.position.x, 1e-5f)
        assertEquals(setOf("entity"), metadata.content.models.map { it.entityId }.toSet())
        assertTrue(display.frame.width in 1..8)
    }

    @Test fun editsAndPreviewsReachTheRendererThroughContentAndOnlyChangeTransforms() {
        val feed = feed()
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        presented(feed)
        val before = device.submitted.get()
        val moved = placement.copy(transform = PlacementTransform(Vec3(5f, 0f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f)))
        val content = SceneContent(models = listOf(moved))
        // an older frame may be shown briefly while motion continues, so wait for one rendered for the moved content
        var display: RaySceneDisplay? = null
        RayFakeDevice.await(what = "a frame for the moved content") {
            display = feed.frame(context(content))
            display?.metadata?.content?.models?.singleOrNull()?.transform?.position?.x == 5f
        }
        assertTrue("the move was submitted as another frame", device.submitted.get() > before)
        assertEquals(5f, display!!.metadata.content.models.single().transform.position.x, 0f)
    }

    @Test fun renderThreadCallsNeverWaitForAStalledConverter() {
        val queue = java.util.concurrent.LinkedBlockingQueue<Runnable>()
        val gate = CountDownLatch(1)
        val stalled = Executor { task -> queue.add(Runnable { gate.await(5, TimeUnit.SECONDS); task.run() }) }
        val feed = feed(stalled)
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        val started = System.nanoTime()
        repeat(200) { feed.frame(context()) }
        assertTrue("frame() must only copy and post", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000)
        assertTrue("work is coalesced into one queued drain, not one task per frame", queue.size <= 1)
        gate.countDown()
        queue.poll()?.run()
    }

    @Test fun aFailedAssetFallsBackTheWholeViewWithAReason() {
        val feed = feed(assets = assets(failure = IllegalStateException("bad mesh")))
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        RayFakeDevice.await(what = "fallback") { feed.frame(context()); feed.runtime.mode.phase == RayModePhase.Failed }
        assertTrue(feed.runtime.mode.failure!!.contains("bad mesh"))
        assertNull(feed.frame(context()))
    }

    @Test fun aSceneOverTheSnapshotLimitsFallsBackInsteadOfDroppingGeometry() {
        val feed = feed(snapshots = RaySceneSnapshots(RaySnapshotLimits(maxInstances = 1)))
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        RayFakeDevice.await(what = "fallback") { feed.frame(context()); feed.runtime.mode.phase == RayModePhase.Failed }
        assertTrue(feed.runtime.mode.failure, feed.runtime.mode.failure!!.contains("too large for ray tracing") && feed.runtime.mode.failure!!.contains("limit is 1"))
    }

    @Test fun resetDiscardsOlderFramesAndTurningOffReleasesTheCompanions() {
        var closed = 0
        val feed = feed(assets = RaySceneAssets({ _, _ -> RayAssetLease({ model }, { null }, { closed++ }) }, { _, _ -> error("none") }))
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        presented(feed)
        feed.reset()
        // a reset (replaced GL context) invalidates what was in flight; the next frame starts a new generation
        presented(feed)
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(false) }
        assertNull(feed.frame(context()))
        assertEquals("companions are released when ray tracing goes off", 1, closed)
    }

    @Test fun theScenesHdrSkyAndItsAmbientColoursReachTheRenderer() {
        val loading = AssetLoading(
            JsonProcessor(), printingLog, Executor(Runnable::run),
            ShaderSource("/shader/sky", AssetLoading::class.java)
        )
        val project = File("src/test/testData/project/Untitled").absoluteFile
        val assets = RaySceneAssets(
            { _, _ -> RayAssetLease({ model }, { null }, {}) }, { _, _ -> error("none") },
            acquireSky = { dir, name -> ViewAssets(loading).project(dir).raySkies.acquire(name).let { lease -> RayAssetLease({ lease.snapshot }, { lease.failure }, lease::close) } },
        )
        val feed = feed(assets = assets)
        val content = SceneContent(models = listOf(placement), skybox = "skybox_hdr")
        val ambient = FloatArray(18) { (it / 3 + 1) * .1f }
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        RayFakeDevice.await(what = "a scene with the transferred sky") {
            feed.frame(RayFrameContext(SceneRenderParams.DEFAULT.copy(content = content, projectDir = project), content, camera, LightSet.NONE, emptyList(), 8, 8, null, ambient))
            device.lastScene?.environment?.texture != null
        }
        val scene = device.lastScene!!
        val environment = scene.environment
        assertEquals(0, environment.texture)
        assertTrue("an HDR sky is flagged so primary misses are tone mapped", environment.hdr)
        assertEquals(1024, scene.textures[0].width)
        assertEquals(512, scene.textures[0].height)
        assertEquals("sky", scene.textures[0].id)
        assertEquals(6, environment.ambientCube!!.size)
        assertEquals(.1f, environment.ambientCube!![0].r, 1e-6f)
        assertEquals(.6f, environment.ambientCube!![5].b, 1e-6f)
    }

    @Test fun aSkyThatCannotBeTransferredShowsTheBackgroundInsteadOfFailingTheView() {
        val project = File("src/test/testData/project/Untitled").absoluteFile
        val loading = AssetLoading(
            JsonProcessor(), printingLog, Executor(Runnable::run),
            ShaderSource("/shader/sky", AssetLoading::class.java)
        )
        val assets = RaySceneAssets({ _, _ -> RayAssetLease({ model }, { null }, {}) }, { _, _ -> error("none") },
            acquireSky = { dir, name -> ViewAssets(loading).project(dir).raySkies.acquire(name).let { lease -> RayAssetLease({ lease.snapshot }, { lease.failure }, lease::close) } })
        val feed = feed(assets = assets)
        val content = SceneContent(models = listOf(placement), skybox = "skybox_physical") // a procedural sky has no CPU form
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        RayFakeDevice.await(what = "a scene without a sky texture") {
            feed.frame(RayFrameContext(SceneRenderParams.DEFAULT.copy(content = content, projectDir = project), content, camera, LightSet.NONE, emptyList(), 8, 8, null))
            device.lastScene != null
        }
        assertNull(device.lastScene!!.environment.texture)
        assertNotEquals(RayModePhase.Failed, feed.runtime.mode.phase)
    }

    @Test fun aBakedProceduralSkyReachesTheRendererAsADisplayValueTexture() {
        val feed = feed()
        val baked = net.nevinsky.abyssus.core.assets.sky.RaySkySnapshot(4, 2, FloatArray(4 * 2 * 4) { .25f }, hdr = false)
        val content = SceneContent(models = listOf(placement), skybox = "procedural")
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        RayFakeDevice.await(what = "a scene with the baked sky") {
            feed.frame(RayFrameContext(SceneRenderParams.DEFAULT.copy(content = content, projectDir = File("project")), content, camera, LightSet.NONE,
                emptyList(), 8, 8, null, null) { baked })
            device.lastScene?.environment?.texture != null
        }
        val environment = device.lastScene!!.environment
        assertEquals(0, environment.texture)
        assertFalse("a baked procedural sky holds display values, not HDR radiance", environment.hdr)
        assertEquals(4, device.lastScene!!.textures[0].width)
    }

    @Test fun unavailableHardwareLeavesTheFeedOnRasterWithTheReason() {
        device.unavailable = RayUnavailableReason.ACCELERATION_STRUCTURES
        val feed = feed()
        SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
        RayFakeDevice.await(what = "unavailable") { feed.runtime.mode.phase == RayModePhase.Unavailable }
        assertNull(feed.frame(context()))
        assertFalse(feed.runtime.mode.toggleEnabled)
    }
}
