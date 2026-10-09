/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.document.SceneRaySettingsState
import net.nevinsky.abyssus.lib.core.editor.EditorMessages

import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.lib.core.assets.model.RayModelSkinning
import net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.raytracing.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import net.nevinsky.abyssus.lib.core.editor.document.documentDisplayMessage

/**
 * Connects one scene view's renderer to its [RayViewRuntime]. [frame] runs on the render thread once per frame: it
 * reads the renderer's current (preview-applied) content, camera, lights and animation poses, copies them into a frozen
 * job and returns the newest completed ray frame. Everything expensive, scene conversion, skin deformation and diffing,
 * runs on [executor] from a one-slot mailbox, so the render thread never blocks and a slow conversion only ever works on
 * the latest job. Nothing here writes a scene file: edits, selection and camera changes reach the renderer through the
 * view's existing state, which is all this reads.
 */
class RayViewFeed(
    val runtime: RayViewRuntime<RayDisplayMetadata>,
    private val assets: RaySceneAssets,
    private val executor: Executor,
    private val messages: EditorMessages,
    private val exposure: () -> Float = { 1f },
    private val snapshots: RaySceneSnapshots = RaySceneSnapshots(),
    private val poses: RayModelPoses = RayModelPoses(),
    private val skinning: RayModelSkinning = RayModelSkinning(),
    private val reportFailure: (Throwable) -> Unit = {},
) : AutoCloseable {
    private class Job(
        val epoch: Long,
        val settingsRevision: Long,
        val params: SceneRenderParams,
        val camera: PerspectiveCamera,
        val lights: LightSet,
        val state: RaySceneAssetState.Ready,
        val poses: Map<String, RayModelPose>,
        val activeCamera: String?,
        val width: Int,
        val height: Int,
        val metadata: RayDisplayMetadata,
        val hdrAmbient: FloatArray?,
        val sky: RaySkySnapshot?,
    )

    private val pendingJob = AtomicReference<Job?>()
    private val running = AtomicBoolean()
    private val closed = AtomicBoolean()
    @Volatile
    private var epoch = 0L
    private var settingsRevision = 0L
    private var settingsSignature: Pair<SceneRaySettingsState, Map<String, com.fasterxml.jackson.databind.JsonNode>>? =
        null

    // Owned by the converter thread (the one worker that runs [drain]).
    private var lastFrame: RaySceneFrame? = null
    private var sceneGeneration = 0L
    private var contextGeneration = 0L
    private var cameraRevision = 0L
    private var poseRevision = 0L
    private var contentRevision = 0L
    private var skyTexture: Pair<RaySkySnapshot, RayTexture>? = null
    private var lastAssets: RaySceneAssetState.Ready? = null

    /** Render thread. The ray frame to present for [context], or null while raster should be shown. */
    fun frame(context: RayFrameContext): RaySceneDisplay? {
        if (closed.get()) return null
        val overrides = net.nevinsky.abyssus.lib.core.editor.document.sceneDocumentFromEcs(context.params.ecs).entities().mapNotNull { entity ->
            entity.componentNode("RenderComponent")?.get("rayTracingMaterials")?.let { entity.id to it }
        }.toMap()
        val signature = context.params.rayTracing to overrides
        if (settingsSignature != signature) {
            settingsSignature = signature
            settingsRevision++
            epoch++
            pendingJob.set(null)
            runtime.invalidateSettingsWork()
        }
        if (!runtime.mode.requested) {
            releaseWhenOff()
            return null
        }
        assets.update(context.params.projectDir, context.content)
        when (val state = assets.poll()) {
            is RaySceneAssetState.Preparing -> return null
            is RaySceneAssetState.Failed -> {
                runtime.fail(state.failures.entries.joinToString { "${it.key}: ${it.value.documentDisplayMessage(messages)}" })
                return null
            }

            is RaySceneAssetState.Ready -> post(context, state)
        }
        val completed = runtime.latest() ?: return null
        return RaySceneDisplay(completed.frame, completed.metadata)
    }

    /** The scene was reset under the view (a replaced GL context or project): older frames must not be presented. */
    fun reset() {
        epoch++
        pendingJob.set(null)
        executor.execute { contextGeneration++; sceneGeneration++; lastFrame = null }
    }

    private fun releaseWhenOff() {
        if (lastFrameWasLive) {
            lastFrameWasLive = false
            epoch++
            pendingJob.set(null)
            assets.close()
            executor.execute { lastFrame = null; lastAssets = null }
        }
    }

    private var lastFrameWasLive = false

    private fun post(context: RayFrameContext, state: RaySceneAssetState.Ready) {
        lastFrameWasLive = true
        val job = Job(
            epoch,
            settingsRevision,
            context.params.copy(content = context.content),
            copy(context.camera),
            context.lights,
            state,
            poses.capture(context.models),
            context.viewCamera,
            context.width,
            context.height,
            captureRayDisplay(context),
            context.hdrAmbient?.copyOf(),
            state.sky ?: context.bakedSky?.invoke()
        )
        pendingJob.set(job)
        if (running.compareAndSet(false, true)) executor.execute(::drain)
    }

    private fun drain() {
        try {
            while (!closed.get()) {
                val job = pendingJob.getAndSet(null) ?: break
                if (job.epoch != epoch) continue
                runCatchingKeepingCancellation { convert(job) }.onFailure { failure ->
                    if (job.epoch == epoch) {
                        reportFailure(failure); runtime.fail(failure.documentDisplayMessage(messages))
                    }
                }
            }
        } finally {
            running.set(false)
            if (pendingJob.get() != null && !closed.get() && running.compareAndSet(
                    false,
                    true
                )
            ) executor.execute(::drain)
        }
    }

    private fun convert(job: Job) {
        if (lastAssets !== job.state && lastAssets?.let { structurallySame(it, job.state) } != true) sceneGeneration++
        lastAssets = job.state
        val sky = job.sky
        val texture = sky?.let(::textureOf)
        val ambientCube = job.hdrAmbient?.takeIf { it.size == 18 }
            ?.let { cube -> List(6) { RayColor(cube[it * 3], cube[it * 3 + 1], cube[it * 3 + 2]) } }
        val ambient = job.params.ambient?.let { RayColor(it.r, it.g, it.b) } ?: RayColor(0f, 0f, 0f)
        val clear = job.params.clear
        val environment = RayEnvironment(
            ambient, RayColor(clear.r, clear.g, clear.b), texture = texture?.let { 0 }, hdr = sky?.hdr ?: false,
            intensity = exposure(), ambientCube = ambientCube
        )
        val conversion =
            snapshots.capture(
                job.params, job.camera, job.lights, job.state, emptyMap(), job.activeCamera, environment,
                listOfNotNull(texture), job.poses, { mesh, palette -> skinning.deform(mesh, palette) },
                environmentRevision = sky?.let { System.identityHashCode(it).toLong() } ?: 0L)
        when (conversion) {
            is RaySceneConversion.Preparing -> return
            is RaySceneConversion.Fallback -> {
                if (job.epoch == epoch) runtime.fail("${conversion.reason.message(messages)}${conversion.detail?.let { " ($it)" } ?: ""}")
                return
            }

            is RaySceneConversion.Ready -> publish(job, conversion.frame)
        }
    }

    private fun publish(job: Job, frame: RaySceneFrame) {
        val scene = frame.scene
        scene.unsupportedReason()?.let { reason ->
            if (job.epoch == epoch) runtime.fail("${RaySceneFallback.RESOURCE_LIMIT.message(messages)} ($reason)")
            return
        }
        val diff = raySceneDiff(lastFrame, frame)
        lastFrame = frame
        if (diff.rebuild) sceneGeneration++
        if (RaySceneChange.CAMERA in diff.changes) cameraRevision++
        if (RaySceneChange.POSE in diff.changes) poseRevision++
        if (diff.changes.any { it != RaySceneChange.CAMERA && it != RaySceneChange.POSE }) contentRevision++
        if (job.epoch != epoch) return
        val key = RayFrameKey(sceneGeneration, contextGeneration, cameraRevision, poseRevision)
        val settings = frame.settings
        val request = RaySceneRequest(
            key, job.width, job.height, frame.camera.rayCamera(), scene,
            maxReflectionBounces = settings.maxReflectionBounces, maxRefractionBounces = settings.maxRefractionBounces,
            maxRaysPerFrame = settings.maxRaysPerFrame.toLong(), settingsRevision = job.settingsRevision
        )
        val rays =
            RayWorkBudget().perCameraSample(scene, settings.maxReflectionBounces, settings.maxRefractionBounces).toInt()
        val input = RayRenderInput(
            request,
            RayDisplayKey(job.width, job.height, job.activeCamera ?: "free"),
            contentRevision,
            rays,
            job.metadata,
            RayRenderSettings(settings.targetSamplesPerPixel, settings.maxRaysPerFrame.toLong()),
            job.settingsRevision
        )
        runtime.offer(input, job.metadata)
    }

    private fun structurallySame(a: RaySceneAssetState.Ready, b: RaySceneAssetState.Ready) =
        a.models.keys == b.models.keys && a.terrains.keys == b.terrains.keys &&
                a.models.all { (name, asset) -> b.models[name] === asset } && a.terrains.all { (name, asset) -> b.terrains[name] === asset }

    private fun textureOf(sky: RaySkySnapshot): RayTexture {
        skyTexture?.takeIf { it.first === sky }?.let { return it.second }
        return RayTexture(
            "sky",
            sky.width,
            sky.height,
            sky.rgba(),
            RayWrap.REPEAT,
            RayWrap.CLAMP_TO_EDGE,
            RayFilter.LINEAR
        ).also { skyTexture = sky to it }
    }

    /** Frozen copy for the converter: a worker must never read the renderer's mutable camera. */
    private fun copy(source: PerspectiveCamera) =
        PerspectiveCamera(source.fieldOfView, source.viewportWidth, source.viewportHeight).also {
            it.position.set(source.position); it.direction.set(source.direction); it.up.set(source.up)
            it.near = source.near; it.far = source.far; it.update()
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        epoch++
        pendingJob.set(null)
        runtime.close()
        assets.close()
    }
}
