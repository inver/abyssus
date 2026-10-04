/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Optional, instance-owned loading. No native library is touched until load is requested. */
internal class MetalLibrary(
    private val resource: () -> ByteArray?,
    private val systemLoad: (String) -> Unit,
) {
    private var loaded = false

    fun load(): Unit = synchronized(javaClass) {
        if (loaded) return@synchronized
        val bytes = checkNotNull(resource()) { "Packaged Metal JNI library is missing" }
        // The JVM caches native libraries by path. Share code within a classloader, never session state.
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val directory = Path.of(System.getProperty("java.io.tmpdir"),
            "abyssus-metal-${ProcessHandle.current().pid()}-${System.identityHashCode(javaClass.classLoader)}")
        Files.createDirectories(directory)
        directory.toFile().deleteOnExit()
        val file = directory.resolve("$digest.dylib")
        if (!Files.exists(file)) {
            Files.write(file, bytes)
            file.toFile().deleteOnExit()
        }
        check(Files.readAllBytes(file).contentEquals(bytes)) { "Extracted Metal library is corrupt" }
        systemLoad(file.toAbsolutePath().toString())
        loaded = true
    }
}

/** The native bridge builds at most this many bottom-level structures and instances per session. */
internal const val METAL_MAX_INSTANCES = 1024

/** Optional provider, injected once at the application composition root. All methods run on its serial worker. */
class MetalRayBackendFactory(
    private val deviceAvailable: () -> Boolean = { true },
    private val health: RayDeviceHealth = RayDeviceHealth(),
    private val log: Logger = NOPLogger.NOP_LOGGER,
) : RayBackendProvider {
    private val bridge = MetalBridge()
    private val library = MetalLibrary({ resource("libabyssus_ray.dylib") }, { System.load(it) })
    private var shader: ByteArray? = null
    private var backend: MetalRayBackend? = null

    private fun resource(name: String): ByteArray? {
        val arch = when (System.getProperty("os.arch")) {
            "aarch64", "arm64" -> "macos-arm64"
            "x86_64", "amd64" -> "macos-x86_64"
            else -> error("Unsupported Metal architecture")
        }
        return javaClass.getResourceAsStream("/native/$arch/$name")?.use { it.readBytes() }
    }
    private fun prepare(): ByteArray {
        check(System.getProperty("os.name").startsWith("Mac")) { "Metal requires macOS" }
        library.load()
        return shader ?: checkNotNull(resource("slice.metallib")) { "Packaged Metal shader is missing" }.also { shader = it }
    }
    override fun probe(): RayCapability {
        if (!deviceAvailable()) return RayCapability.Unavailable(RayUnavailableReason.ACCELERATION_STRUCTURES)
        backend?.takeUnless { it.disposed }?.let { return RayCapability.Available(it) }
        log.info("Probing Metal")
        return RayBackendProbe({
            val values = bridge.probe(prepare())
            check(values.size == 5) { "Invalid Metal probe response" }
            RayCapabilities(values[0] != 0L,values[1] != 0L,values[2] != 0L,values[3].toInt(),values[4],METAL_MAX_INSTANCES).also { log.info("Metal probe: $it") }
        }, { caps ->
            val handle = bridge.create(prepare())
            check(handle != 0L) { "Metal backend initialization failed" }
            MetalRayBackend(bridge,handle,caps,bridge.deviceName(handle),health,log).also {
                backend = it
                log.info("Metal backend ready on '${it.info.gpu}'")
            }
        }).probe().also { if (it is RayCapability.Unavailable) log.warn("Metal is unavailable: ${it.reason}${it.detail?.let { d -> " ($d)" } ?: ""}") }
    }
}

/** All sessions share this device/queue; each keeps independent scene and readback resources. */
class MetalRayBackend internal constructor(
    private val bridge: MetalBridge, private var handle: Long,
    override val capabilities: RayCapabilities, gpu: String, private val health: RayDeviceHealth, private val log: Logger = NOPLogger.NOP_LOGGER,
) : RayBackend {
    override val info = RayBackendInfo("Metal",gpu)
    private val worker = Thread.currentThread()
    private val sessions = linkedMapOf<String,RaySession>()
    internal val disposed: Boolean get() = handle == 0L
    override fun openSession(viewId: String, limits: RayLimits): RaySession {
        check(Thread.currentThread() === worker && !disposed) { "Metal backend is closed or used from another worker" }
        require(viewId.isNotEmpty() && viewId !in sessions) { "View already owns a Metal session" }
        require(limits.maxDimension <= capabilities.maxFrameDimension && limits.maxPixels <= 4_194_304 && limits.maxInstances <= capabilities.maxInstances)
        health.checkUsable()
        log.atDebug().log { "Metal session '$viewId' opened, $limits" }
        val driver = MetalRaySession(bridge,bridge.openSession(handle),limits) { sessions.remove(viewId) }
        return RayQueuedSession(driver,health).also { sessions[viewId] = it }
    }
    override fun dispose() {
        check(Thread.currentThread() === worker) { "Metal backend must stay on its owner worker" }
        if (disposed) return
        log.info("Disposing the Metal backend (${sessions.size} open sessions)")
        sessions.values.toList().forEach { it.dispose() }
        val owned = handle
        handle = 0L
        bridge.destroy(owned)
    }
}

internal class MetalBridge {
    external fun probe(shader: ByteArray): LongArray
    external fun create(shader: ByteArray): Long
    external fun openSession(backend: Long): Long
    external fun deviceName(backend: Long): String
    external fun destroy(handle: Long)
    external fun updateGeometry(handle: Long, meshes: IntArray, vertices: FloatArray)
    external fun setGeometry(handle: Long, vertices: FloatArray, indices: IntArray, vertexCounts: IntArray, indexCounts: IntArray)
    external fun submit(handle: Long, width: Int, height: Int, camera: FloatArray, instances: FloatArray, meshes: IntArray)
    external fun setSceneData(handle: Long, values: FloatArray?, bytes: ByteArray?)
    external fun poll(handle: Long, color: FloatArray, depth: FloatArray): Boolean
}

class MetalRaySession internal constructor(
    private val bridge: MetalBridge, private var handle: Long,
    private val limits: RayLimits, private val onDisposed: () -> Unit,
) : RaySession {
    private val worker = Thread.currentThread()
    private var meshCount = 0
    private var geometryKey: List<RaySliceMesh>? = null
    private var sceneGeometryKey: List<RayMesh>? = null
    private var lastScene: RaySceneSnapshot? = null
    private var builds = 0L
    override val geometryBuilds: Long get() = builds
    private var pending: Pending? = null
    private class Pending(val key: RayFrameKey, val width: Int, val height: Int) {
        val color = FloatArray(width * height * 4)
        val depth = FloatArray(width * height)
    }

    private fun checkOwner() {
        check(Thread.currentThread() === worker) { "Metal session must stay on its owner worker" }
        check(handle != 0L) { "Metal session is closed" }
    }

    /** Upload/build runs on the worker; static geometry survives subsequent instance transform submissions. */
    fun setGeometry(meshes: List<RaySliceMesh>) {
        checkOwner()
        check(pending == null) { "Cannot replace geometry during a render" }
        require(meshes.isNotEmpty() && meshes.size <= limits.maxInstances)
        val vertices = meshes.map { it.vertices() }
        val indices = meshes.map { it.indices() }
        require(vertices.sumOf { it.size.toLong() * 4 } + indices.sumOf { it.size.toLong() * 4 } <= 32L * 1024 * 1024)
        bridge.setGeometry(handle, vertices.flatMap { it.asIterable() }.toFloatArray(), indices.flatMap { it.asIterable() }.toIntArray(),
            vertices.map { it.size / 3 }.toIntArray(), indices.map { it.size }.toIntArray())
        meshCount = meshes.size
        geometryKey = meshes.toList()
        builds += meshes.size
    }

    /** Enqueues GPU work without waiting for a fence. One submitted request per session. */
    fun submit(key: RayFrameKey, width: Int, height: Int, camera: RaySliceCamera, instances: List<RaySliceInstance>) {
        checkOwner()
        check(pending == null) { "A Metal frame is already in flight" }
        require(width in 1..limits.maxDimension && height in 1..limits.maxDimension && width.toLong() * height <= limits.maxPixels)
        require(instances.isNotEmpty() && instances.size <= limits.maxInstances && instances.all { it.mesh < meshCount })
        val request = Pending(key, width, height)
        bridge.submit(handle, width, height, camera.uniforms(width, height),
            instances.flatMap { it.metalUniforms().asIterable() }.toFloatArray(), instances.map { it.mesh }.toIntArray())
        pending = request
    }

    /** Returns null while the GPU is busy; shared readback memory is copied only after completion. */
    override fun poll(): RayFrame? {
        checkOwner()
        val request = pending ?: return null
        if (!bridge.poll(handle, request.color, request.depth)) return null
        pending = null
        return RayFrame(request.key, request.width, request.height, request.color, request.depth)
    }

    override fun submit(request: RayRequest) {
        checkOwner()
        check(pending == null) { "A Metal frame is already in flight" }
        sceneGeometryKey = null
        lastScene = null
        bridge.setSceneData(handle,null,null)
        val meshes = request.meshes()
        if (geometryKey != meshes) setGeometry(meshes)
        submit(request.key,request.width,request.height,request.camera,request.instances())
    }

    override fun submit(request: RaySceneRequest) {
        checkOwner()
        check(pending == null) { "A Metal frame is already in flight" }
        val scene=request.scene
        require(scene.instances.isNotEmpty()) { "Empty ray scenes are not supported yet" }
        scene.unsupportedReason()?.let { throw IllegalArgumentException(it) }
        // Static geometry and shading data survive transform-only updates; only changed inputs are rebuilt or re-encoded.
        val dirty = dirtyMeshes(sceneGeometryKey,scene.meshes)
        val geometryChanged = dirty == null || dirty.isNotEmpty()
        if(dirty == null) {
            setGeometry(scene.meshes.map { RaySliceMesh(it.positions(),it.indices()) })
        } else if(dirty.isNotEmpty()) {
            // same topology, new positions (a re-skinned model): refit only the changed meshes' structures
            check(pending == null) { "Cannot replace geometry during a render" }
            bridge.updateGeometry(handle,dirty.toIntArray(),dirty.flatMap { scene.meshes[it].positions().asIterable() }.toFloatArray())
            builds += dirty.size
        }
        sceneGeometryKey = scene.meshes
        if(geometryChanged || !shadingUnchanged(lastScene,scene)) {
            RaySceneEncoding(scene).encode().let { bridge.setSceneData(handle,it.floats,it.bytes) }
            lastScene=scene
        }
        submit(request.key,request.width,request.height,request.camera,scene.instances.map {
            RaySliceInstance(it.mesh,it.transform().toList(),listOf(1f,1f,1f),primaryOnly=scene.materials[it.material].alphaMode==RayAlphaMode.BLEND)
        })
    }

    override fun dispose() {
        check(Thread.currentThread() === worker) { "Metal session must stay on its owner worker" }
        if (handle == 0L) return
        val owned = handle
        handle = 0L
        bridge.destroy(owned)
        pending = null
        geometryKey = null
        sceneGeometryKey = null
        lastScene = null
        onDisposed()
    }
}

/** The shading payload depends on everything except instance transforms and the camera. */
internal fun shadingUnchanged(previous: RaySceneSnapshot?, next: RaySceneSnapshot): Boolean =
    previous != null && previous.materials == next.materials && previous.lights == next.lights &&
        previous.environment == next.environment && previous.fog == next.fog &&
        previous.instances.map { it.material } == next.instances.map { it.material } &&
        previous.meshes.size == next.meshes.size && previous.meshes.indices.all { previous.meshes[it].sameContentAs(next.meshes[it]) } &&
        previous.textures.size == next.textures.size && previous.textures.indices.all { previous.textures[it].sameContentAs(next.textures[it]) }
