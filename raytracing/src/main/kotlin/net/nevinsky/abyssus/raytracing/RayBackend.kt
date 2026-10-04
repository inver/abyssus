/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import java.io.IOException
import java.util.concurrent.CancellationException

/** Features and renderer bounds measured by the backend, never inferred from GPU names. */
data class RayCapabilities(
    val accelerationStructures: Boolean,
    val rayQueries: Boolean,
    val colorDepthReadback: Boolean,
    val maxFrameDimension: Int,
    val memoryBudgetBytes: Long,
    /** The most instances (and meshes) one session of this backend can hold; scenes with more fall back to raster. */
    val maxInstances: Int = 128,
    val sceneOptics: Boolean = false,
) {
    fun unavailableReason(): RayUnavailableReason? = when {
        !accelerationStructures -> RayUnavailableReason.ACCELERATION_STRUCTURES
        !rayQueries -> RayUnavailableReason.RAY_QUERIES
        !colorDepthReadback -> RayUnavailableReason.COLOR_DEPTH_READBACK
        maxFrameDimension <= 0 || memoryBudgetBytes <= 0 -> RayUnavailableReason.RESOURCE_LIMITS
        else -> null
    }
}

enum class RayUnavailableReason {
    ACCELERATION_STRUCTURES, RAY_QUERIES, COLOR_DEPTH_READBACK, RESOURCE_LIMITS,
    RUNTIME_NOT_FOUND, INITIALIZATION_FAILED,
    /** The `abyssus.raytracing.backend=off` property: ray tracing is switched off for this IDE session. */
    DISABLED,
}

data class RayBackendInfo(val name: String, val gpu: String)

/** Reason codes are localized by the plugin; the plain JVM module does not own UI strings. */
sealed interface RayCapability {
    data class Available(val backend: RayBackend) : RayCapability
    data class Unavailable(val reason: RayUnavailableReason, val detail: String? = null) : RayCapability
}

/** Internal backend protocol: no IntelliJ extension point or binary compatibility promise. */
interface RayBackendProvider {
    fun probe(): RayCapability
}

interface RayBackend : AutoCloseable {
    val info: RayBackendInfo
    val capabilities: RayCapabilities
    fun openSession(viewId: String, limits: RayLimits): RaySession
    fun dispose()
    override fun close() = dispose()
}

interface RaySession : AutoCloseable {
    fun submit(request: RayRequest)
    fun submit(request: RaySceneRequest) { throw UnsupportedOperationException("Backend does not support scene materials") }
    fun poll(): RayFrame?
    /** How many times this session built acceleration structures; transform-only updates must not increase it. */
    val geometryBuilds: Long get() = 0
    fun dispose()
    override fun close() = dispose()
}

/** Per-session upper bounds, independent of any other view's allocation. */
data class RayLimits(val maxDimension: Int = 4096, val maxPixels: Int = 4_194_304, val maxInstances: Int = 128) {
    init { require(maxDimension > 0 && maxPixels > 0 && maxInstances > 0) }
}

/** Feasibility request. Complete scene material/texture snapshots are added by the asset tasks. */
class RayRequest(
    override val key: RayFrameKey, override val width: Int, override val height: Int,
    override val camera: RaySliceCamera, meshes: List<RaySliceMesh>, instances: List<RaySliceInstance>,
) : RayRenderRequest {
    private val geometry = meshes.toList()
    private val placements = instances.toList()
    init {
        require(width > 0 && height > 0)
        require(geometry.isNotEmpty() && placements.isNotEmpty() && placements.all { it.mesh in geometry.indices })
    }
    fun meshes(): List<RaySliceMesh> = geometry.toList()
    fun instances(): List<RaySliceInstance> = placements.toList()
    override fun withRenderPlan(width: Int, height: Int, samples: Int, sampleOffset: Int, accumulationEpoch: Long) =
        RayRequest(key, width, height, camera, geometry, placements)
}

/** Converts expected native initialization failures to availability, without swallowing cancellation. */
class RayBackendProbe(
    private val capabilities: () -> RayCapabilities,
    private val initialize: (RayCapabilities) -> RayBackend,
) : RayBackendProvider {
    override fun probe(): RayCapability = try {
        val caps = capabilities()
        val reason = caps.unavailableReason()
        if (reason != null) RayCapability.Unavailable(reason) else RayCapability.Available(initialize(caps))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (missing: UnsatisfiedLinkError) {
        RayCapability.Unavailable(RayUnavailableReason.RUNTIME_NOT_FOUND,missing.message)
    } catch (unreadable: IOException) {
        RayCapability.Unavailable(RayUnavailableReason.INITIALIZATION_FAILED,unreadable.message)
    } catch (failed: IllegalStateException) {
        RayCapability.Unavailable(RayUnavailableReason.INITIALIZATION_FAILED,failed.message)
    }
}

/** One view owns one session; the backend/device belongs to the application service. */
class RaySessionOwner(private val backend: RayBackend, private val viewId: String, private val limits: RayLimits) : AutoCloseable {
    var session: RaySession? = null
        private set
    fun open(): RaySession = session ?: backend.openSession(viewId,limits).also { session = it }
    override fun close() {
        val owned = session
        session = null
        owned?.dispose()
    }
}
