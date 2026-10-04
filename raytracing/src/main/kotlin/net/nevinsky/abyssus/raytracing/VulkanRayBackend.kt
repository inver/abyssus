/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import net.nevinsky.abyssus.raytracing.VulkanDeviceContext.Access
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRAccelerationStructure.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK12.vkGetBufferDeviceAddress
import java.nio.ByteBuffer

private const val MAX_RAYS_PER_DISPATCH = 1 shl 19
private const val MAX_GEOMETRY_BYTES = 32L * 1024 * 1024
private const val WAIT_TIMEOUT_NS = 10_000_000_000L
private const val INSTANCE_BYTES = 96
private const val TLAS_INSTANCE_BYTES = 64
/** The most instances (and meshes) one Vulkan session holds; more fall back to raster. */
internal const val VULKAN_MAX_INSTANCES = 1024

/**
 * Optional provider, injected once at the application composition root. Nothing Vulkan-related loads before `probe`;
 * a missing loader is reported as "runtime not found" rather than thrown.
 */
class VulkanRayBackendFactory(
    private val deviceAvailable: () -> Boolean = { true },
    private val health: RayDeviceHealth = RayDeviceHealth(),
    private val validation: Boolean = java.lang.Boolean.getBoolean("abyssus.raytracing.validation"),
    private val log: Logger = NOPLogger.NOP_LOGGER,
) : RayBackendProvider {
    private var backend: VulkanRayBackend? = null
    private var measuredInstance: VulkanInstance? = null
    private var measuredCandidate: VulkanCandidate? = null
    private var noDeviceReason: String? = null

    /** Validation errors recorded by the current backend's instance (empty unless validation is enabled). */
    val validationErrors: List<String> get() = backend?.validationErrors ?: emptyList()
    val validationActive: Boolean get() = backend?.validationActive ?: false
    val probed: Boolean get() = backend != null
    /** The GPU the current backend runs on, without probing again. */
    val deviceName: String? get() = backend?.info?.gpu

    override fun probe(): RayCapability {
        if (!deviceAvailable()) return RayCapability.Unavailable(RayUnavailableReason.ACCELERATION_STRUCTURES)
        backend?.takeUnless { it.disposed }?.let { return RayCapability.Available(it) }
        noDeviceReason = null
        log.info("Probing Vulkan (validation ${if (validation) "requested" else "off"})")
        val result = RayBackendProbe(::measure, ::create).probe()
        val detail = noDeviceReason
        return if (result is RayCapability.Unavailable && detail != null) result.copy(detail = detail) else result
    }

    private fun measure(): RayCapabilities {
        val instance = VulkanInstance(validation, log)
        val (candidate, reason) = try {
            instance.select()
        } catch (failure: Throwable) {
            instance.close()
            throw failure
        }
        if (candidate == null) {
            instance.close()
            noDeviceReason = reason
            log.warn("No usable Vulkan device: ${reason ?: "none found"}")
            return RayCapabilities(false, false, false, 0, 0)
        }
        measuredInstance = instance
        measuredCandidate = candidate
        return candidate.capabilities
    }

    private fun create(capabilities: RayCapabilities): RayBackend {
        val instance = checkNotNull(measuredInstance)
        val candidate = checkNotNull(measuredCandidate)
        measuredInstance = null
        measuredCandidate = null
        var context: VulkanDeviceContext? = null
        try {
            fun shader(name: String) = checkNotNull(javaClass.getResourceAsStream("/native/vulkan/$name.spv")?.use { it.readBytes() }) {
                "Packaged Vulkan shader $name is missing: the plugin was built without glslangValidator or glslc (see raytracing/README.md)"
            }
            val slice = shader("slice")
            val scene = shader("scene")
            context = VulkanDeviceContext(instance, candidate)
            val pipeline = VulkanPipeline(context, slice, scene)
            return VulkanRayBackend(context, pipeline, capabilities, health, log).also {
                backend = it
                log.info("Vulkan backend ready on '${candidate.name}' (shaders slice ${slice.size} B, scene ${scene.size} B)")
            }
        } catch (failure: Throwable) {
            log.warn("Vulkan backend initialization failed: ${failure.message}", failure)
            context?.close()
            instance.close()
            throw failure
        }
    }
}

/**
 * Descriptor layout, pipeline layout and the two compute pipelines, shared by every session on the device. The feasibility
 * slice and the full scene renderer use one layout: the slice shader ignores the scene-payload bindings 7 and 8.
 */
internal class VulkanPipeline(private val context: VulkanDeviceContext, slice: ByteArray, scene: ByteArray) : AutoCloseable {
    val descriptorLayout: Long
    val pipelineLayout: Long
    val pipeline: Long
    val scenePipeline: Long
    private val shader: Long
    private val sceneShader: Long

    init {
        val device = context.device
        MemoryStack.stackPush().use { stack ->
            val out = stack.mallocLong(1)
            fun module(spirv: ByteArray): Long {
                // SPIR-V can exceed the 64 KB thread stack, so it goes on the heap.
                val code = MemoryUtil.memAlloc(spirv.size).put(spirv).flip()
                try {
                    val info = VkShaderModuleCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO).pCode(code)
                    vkCheck(vkCreateShaderModule(device, info, null, out), "vkCreateShaderModule")
                } finally {
                    MemoryUtil.memFree(code)
                }
                return out[0]
            }
            shader = module(slice)
            sceneShader = module(scene)

            val types = intArrayOf(
                VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
                VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
            )
            val bindings = VkDescriptorSetLayoutBinding.calloc(types.size, stack)
            types.forEachIndexed { i, type ->
                bindings[i].binding(i).descriptorType(type).descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
            }
            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO).pBindings(bindings)
            vkCheck(vkCreateDescriptorSetLayout(device, layoutInfo, null, out), "vkCreateDescriptorSetLayout")
            descriptorLayout = out[0]

            val push = VkPushConstantRange.calloc(1, stack).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(16)
            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(descriptorLayout)).pPushConstantRanges(push)
            vkCheck(vkCreatePipelineLayout(device, pipelineLayoutInfo, null, out), "vkCreatePipelineLayout")
            pipelineLayout = out[0]

            fun compute(module: Long): Long {
                val stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"))
                val pipelines = VkComputePipelineCreateInfo.calloc(1, stack).sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .stage(stage).layout(pipelineLayout)
                vkCheck(vkCreateComputePipelines(device, VK_NULL_HANDLE, pipelines, null, out), "vkCreateComputePipelines")
                return out[0]
            }
            pipeline = compute(shader)
            scenePipeline = compute(sceneShader)
        }
    }

    override fun close() {
        vkDestroyPipeline(context.device, pipeline, null)
        vkDestroyPipeline(context.device, scenePipeline, null)
        vkDestroyPipelineLayout(context.device, pipelineLayout, null)
        vkDestroyDescriptorSetLayout(context.device, descriptorLayout, null)
        vkDestroyShaderModule(context.device, shader, null)
        vkDestroyShaderModule(context.device, sceneShader, null)
    }
}

/** All sessions share this device and queue; each keeps independent geometry, structures and output images. */
class VulkanRayBackend internal constructor(
    private val context: VulkanDeviceContext, private val pipeline: VulkanPipeline,
    override val capabilities: RayCapabilities, private val health: RayDeviceHealth, private val log: Logger = NOPLogger.NOP_LOGGER,
) : RayBackend {
    override val info = RayBackendInfo("Vulkan", context.candidate.name)
    private val worker = Thread.currentThread()
    private val sessions = linkedMapOf<String, RaySession>()
    private var closed = false
    private var lostFlag = false
    internal val disposed: Boolean get() = closed

    /** Validation-layer errors recorded since the backend was created (empty unless validation is enabled). */
    val validationErrors: List<String> get() = context.instance.validationMessages.toList()
    val validationActive: Boolean get() = context.instance.validationActive

    /** True only after Vulkan itself reported `VK_ERROR_DEVICE_LOST`. A loss injected through `health` leaves the device healthy, so teardown still waits. */
    internal fun lost(): Boolean = lostFlag
    internal fun markLost() { lostFlag = true }

    override fun openSession(viewId: String, limits: RayLimits): RaySession {
        check(Thread.currentThread() === worker && !closed) { "Vulkan backend is closed or used from another worker" }
        require(viewId.isNotEmpty() && viewId !in sessions) { "View already owns a Vulkan session" }
        require(limits.maxDimension <= capabilities.maxFrameDimension && limits.maxPixels <= 4_194_304 && limits.maxInstances <= capabilities.maxInstances)
        health.checkUsable()
        log.atDebug().log { "Vulkan session '$viewId' opened, $limits" }
        val driver = VulkanRaySession(context, pipeline, limits, this, log) { sessions.remove(viewId) }
        return RayQueuedSession(driver, health).also { sessions[viewId] = it }
    }

    override fun dispose() {
        check(Thread.currentThread() === worker) { "Vulkan backend must stay on its owner worker" }
        if (closed) return
        log.info("Disposing the Vulkan backend on '${context.candidate.name}' (${sessions.size} open sessions${if (lost()) ", device lost" else ""})")
        sessions.values.toList().forEach { it.dispose() }
        closed = true
        context.waitIdle(lost())
        pipeline.close()
        context.close()
        context.instance.close()
    }
}

private class Blas(val buffer: VulkanBuffer, val handle: Long)

private class FrameResources(val width: Int, val height: Int, val color: VulkanImage, val depth: VulkanImage,
    val colorReadback: VulkanBuffer, val depthReadback: VulkanBuffer)

/** Per-view state. Everything runs on the owner worker; `submit` and `poll` never wait for the GPU. */
internal class VulkanRaySession(
    private val context: VulkanDeviceContext, private val pipeline: VulkanPipeline,
    private val limits: RayLimits, private val backend: VulkanRayBackend, private val log: Logger, private val onDisposed: () -> Unit,
) : RaySession {
    private val device = context.device
    private val worker = Thread.currentThread()
    private var closed = false
    private val pool: Long
    private val descriptorPool: Long
    private val descriptorSet: Long
    private val fence: Long
    private val cameraBuffer: VulkanBuffer
    private val instanceBuffer: VulkanBuffer
    private val tlasInstances: VulkanBuffer
    private val tlasBuffer: VulkanBuffer
    private val tlasScratch: VulkanBuffer
    private val tlasScratchAddress: Long
    private val tlas: Long

    private var vertexBuffer: VulkanBuffer? = null
    private var indexBuffer: VulkanBuffer? = null
    private var blas = emptyList<Blas>()
    private var vertexOffsets = IntArray(0)
    private var indexOffsets = IntArray(0)
    private var geometryKey: List<RaySliceMesh>? = null
    private var sceneGeometryKey: List<RayMesh>? = null
    private var lastScene: RaySceneSnapshot? = null
    private var vertexCounts = emptyList<Int>()
    private var indexCounts = emptyList<Int>()
    private var builds = 0L
    /** The scene payload (floats, then RGBA8 texels). A small placeholder until a scene renders; the slice shader never reads it. */
    private var sceneBuffer: VulkanBuffer
    private var frame: FrameResources? = null
    private var pending: Pending? = null
    private var inFlightCommands: PointerBufferHolder? = null

    override val geometryBuilds: Long get() = builds

    private class Pending(val key: RayFrameKey, val width: Int, val height: Int)
    private class PointerBufferHolder(val handles: LongArray)

    init {
        MemoryStack.stackPush().use { stack ->
            val poolInfo = VkCommandPoolCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT).queueFamilyIndex(context.candidate.queueFamily)
            val out = stack.mallocLong(1)
            vkCheck(vkCreateCommandPool(device, poolInfo, null, out), "vkCreateCommandPool")
            pool = out[0]

            val sizes = VkDescriptorPoolSize.calloc(4, stack)
            sizes[0].type(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR).descriptorCount(1)
            sizes[1].type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(5)
            sizes[2].type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(2)
            sizes[3].type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
            val descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(1).pPoolSizes(sizes)
            vkCheck(vkCreateDescriptorPool(device, descriptorPoolInfo, null, out), "vkCreateDescriptorPool")
            descriptorPool = out[0]
            val setInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool).pSetLayouts(stack.longs(pipeline.descriptorLayout))
            vkCheck(vkAllocateDescriptorSets(device, setInfo, out), "vkAllocateDescriptorSets")
            descriptorSet = out[0]

            val fenceInfo = VkFenceCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            vkCheck(vkCreateFence(device, fenceInfo, null, out), "vkCreateFence")
            fence = out[0]
        }
        cameraBuffer = context.createBuffer(80, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, Access.HOST_WRITE)
        sceneBuffer = context.createBuffer(256, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, Access.HOST_WRITE)
        instanceBuffer = context.createBuffer(limits.maxInstances.toLong() * INSTANCE_BYTES, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, Access.HOST_WRITE)
        tlasInstances = context.createBuffer(limits.maxInstances.toLong() * TLAS_INSTANCE_BYTES,
            VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR, Access.HOST_WRITE)

        MemoryStack.stackPush().use { stack ->
            val geometry = instancesGeometry(stack, tlasInstances.address)
            val sizes = buildSizes(stack, VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR, geometry, limits.maxInstances)
            tlasBuffer = context.createBuffer(sizes.accelerationStructureSize(),
                VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR, Access.DEVICE)
            val alignment = context.candidate.scratchAlignment
            tlasScratch = context.createBuffer(sizes.buildScratchSize() + alignment, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, Access.DEVICE)
            tlasScratchAddress = alignUp(tlasScratch.address, alignment)
            tlas = createStructure(stack, tlasBuffer, VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
        }
    }

    private fun alignUp(value: Long, alignment: Long) = (value + alignment - 1) / alignment * alignment

    private fun checkOwner() {
        check(Thread.currentThread() === worker) { "Vulkan session must stay on its owner worker" }
        check(!closed) { "Vulkan session is closed" }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (lost: RayDeviceLostException) {
        log.warn("Vulkan device lost: ${lost.message}", lost)
        backend.markLost()
        throw lost
    }

    private fun instancesGeometry(stack: MemoryStack, address: Long): VkAccelerationStructureGeometryKHR.Buffer {
        val geometry = VkAccelerationStructureGeometryKHR.calloc(1, stack)
            .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR).geometryType(VK_GEOMETRY_TYPE_INSTANCES_KHR)
            .flags(VK_GEOMETRY_OPAQUE_BIT_KHR)
        geometry.geometry().instances().sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR)
            .arrayOfPointers(false).data().deviceAddress(address)
        return geometry
    }

    private fun buildSizes(stack: MemoryStack, type: Int, geometry: VkAccelerationStructureGeometryKHR.Buffer, primitives: Int):
        VkAccelerationStructureBuildSizesInfoKHR {
        val info = VkAccelerationStructureBuildGeometryInfoKHR.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR).type(type)
            .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR).mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)
            .geometryCount(1).pGeometries(geometry)
        val sizes = VkAccelerationStructureBuildSizesInfoKHR.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR)
        vkGetAccelerationStructureBuildSizesKHR(device, VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR, info,
            stack.ints(primitives), sizes)
        return sizes
    }

    private fun createStructure(stack: MemoryStack, storage: VulkanBuffer, type: Int): Long {
        val info = VkAccelerationStructureCreateInfoKHR.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR).buffer(storage.buffer).size(storage.size).type(type)
        val out = stack.mallocLong(1)
        vkCheck(vkCreateAccelerationStructureKHR(device, info, null, out), "vkCreateAccelerationStructureKHR")
        return out[0]
    }

    /** Builds one bottom-level structure per mesh. Static geometry survives transform-only submissions. */
    fun setGeometry(meshes: List<RaySliceMesh>) {
        checkOwner()
        check(pending == null) { "Cannot replace geometry during a render" }
        require(meshes.isNotEmpty() && meshes.size <= limits.maxInstances)
        val vertices = meshes.map { it.vertices() }
        val indices = meshes.map { it.indices() }
        require(vertices.sumOf { it.size.toLong() * 4 } + indices.sumOf { it.size.toLong() * 4 } <= MAX_GEOMETRY_BYTES)
        guarded {
            releaseGeometry()
            val vertexFloats = vertices.sumOf { it.size }
            val indexInts = indices.sumOf { it.size }
            val vertexData = context.createBuffer(vertexFloats * 4L,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT or VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR, Access.HOST_WRITE)
            val indexData = context.createBuffer(indexInts * 4L,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT or VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR, Access.HOST_WRITE)
            vertexBuffer = vertexData
            indexBuffer = indexData
            val vertexView = vertexData.mapped!!.asFloatBuffer()
            val indexView = indexData.mapped!!.asIntBuffer()
            vertexOffsets = IntArray(meshes.size)
            indexOffsets = IntArray(meshes.size)
            var vertexCursor = 0
            var indexCursor = 0
            meshes.indices.forEach { i ->
                vertexOffsets[i] = vertexCursor / 3
                indexOffsets[i] = indexCursor
                vertexView.put(vertices[i])
                indexView.put(indices[i])
                vertexCursor += vertices[i].size
                indexCursor += indices[i].size
            }
            context.flush(vertexData)
            context.flush(indexData)
            vertexCounts = vertices.map { it.size / 3 }
            indexCounts = indices.map { it.size }
            val started = System.nanoTime()
            buildBottomLevel()
            log.atDebug().log { "Vulkan built ${meshes.size} bottom-level structures (${vertexFloats / 3} vertices, ${indexInts / 3} triangles) in ${(System.nanoTime() - started) / 1_000_000} ms" }
            geometryKey = meshes.toList()
            builds += meshes.size
        }
    }

    private fun bottomGeometry(stack: MemoryStack, mesh: Int, vertexCount: Int, indexCount: Int): VkAccelerationStructureGeometryKHR.Buffer {
        val geometry = VkAccelerationStructureGeometryKHR.calloc(1, stack)
            .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR).geometryType(VK_GEOMETRY_TYPE_TRIANGLES_KHR)
            .flags(VK_GEOMETRY_OPAQUE_BIT_KHR)
        val triangles = geometry.geometry().triangles().sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR)
            .vertexFormat(VK_FORMAT_R32G32B32_SFLOAT).vertexStride(12).maxVertex(vertexCount - 1).indexType(VK_INDEX_TYPE_UINT32)
        triangles.vertexData().deviceAddress(vertexBuffer!!.address + vertexOffsets[mesh] * 12L)
        triangles.indexData().deviceAddress(indexBuffer!!.address + indexOffsets[mesh] * 4L)
        return geometry
    }

    // The loops below run once per mesh (hundreds for a real scene), so each iteration gets its own stack frame.
    private fun scratchSize(stack: MemoryStack, mesh: Int) = stack.push().use {
        buildSizes(stack, VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR, bottomGeometry(stack, mesh, vertexCounts[mesh], indexCounts[mesh]),
            indexCounts[mesh] / 3).buildScratchSize()
    }

    /** Allocates one structure per mesh and builds them all. */
    private fun buildBottomLevel() = MemoryStack.stackPush().use { stack ->
        val built = mutableListOf<Blas>()
        try {
            vertexCounts.indices.forEach { i ->
                stack.push().use {
                    val size = buildSizes(stack, VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR,
                        bottomGeometry(stack, i, vertexCounts[i], indexCounts[i]), indexCounts[i] / 3).accelerationStructureSize()
                    val storage = context.createBuffer(size, VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR, Access.DEVICE)
                    built += Blas(storage, createStructure(stack, storage, VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR))
                }
            }
        } finally {
            blas = built
        }
        buildStructures(stack, built.indices.toList())
    }

    /** (Re)builds the listed structures in place from the current vertex and index data. */
    private fun buildStructures(stack: MemoryStack, meshes: List<Int>) {
        val alignment = context.candidate.scratchAlignment
        val scratch = context.createBuffer(meshes.maxOf { scratchSize(stack, it) } + alignment, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, Access.DEVICE)
        try {
            val command = allocateCommands(stack, 1)[0]
            beginCommands(stack, command, oneTime = true)
            val barrier = VkMemoryBarrier.calloc(1, stack).sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
                .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR or VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
            meshes.forEach { i -> stack.push().use {
                val geometry = bottomGeometry(stack, i, vertexCounts[i], indexCounts[i])
                val info = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR).flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                    .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR).dstAccelerationStructure(blas[i].handle)
                    .geometryCount(1).pGeometries(geometry)
                info.scratchData().deviceAddress(alignUp(scratch.address, alignment))
                val range = VkAccelerationStructureBuildRangeInfoKHR.calloc(1, stack).primitiveCount(indexCounts[i] / 3)
                vkCmdBuildAccelerationStructuresKHR(command, info, stack.pointers(range.address()))
                // The scratch buffer is shared, so each build must finish before the next one starts.
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
                    VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, 0, barrier, null, null)
            } }
            vkCheck(vkEndCommandBuffer(command), "vkEndCommandBuffer")
            submitAndWait(stack, command)
            vkFreeCommandBuffers(device, pool, command)
        } finally {
            context.destroyBuffer(scratch)
        }
    }

    /** Same topology, new positions (a re-skinned model): rewrite those vertices and rebuild only their structures. */
    private fun updateGeometry(dirty: List<Int>, meshes: List<RayMesh>) {
        val vertexData = checkNotNull(vertexBuffer)
        val view = vertexData.mapped!!.asFloatBuffer()
        dirty.forEach { view.put(vertexOffsets[it] * 3, meshes[it].positions()) }
        context.flush(vertexData)
        MemoryStack.stackPush().use { stack -> buildStructures(stack, dirty) }
        log.atDebug().log { "Vulkan refitted ${dirty.size} bottom-level structures" }
        builds += dirty.size
        geometryKey = null
    }

    /** Copies the scene payload into the (grown when needed) host-visible buffer. Never called while a frame is in flight. */
    private fun uploadScene(data: RaySceneData) {
        val size = data.floats.size * 4L + data.bytes.size
        if (size > sceneBuffer.size || sceneBuffer.size > 4 * size && sceneBuffer.size > (1L shl 20)) {
            context.destroyBuffer(sceneBuffer)
            sceneBuffer = context.createBuffer(maxOf(size, 256), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, Access.HOST_WRITE)
        }
        log.atDebug().log { "Vulkan scene payload uploaded: ${data.floats.size} floats and ${data.bytes.size} texel bytes" }
        val mapped = sceneBuffer.mapped!!
        mapped.asFloatBuffer().put(data.floats)
        mapped.put(data.floats.size * 4, data.bytes)
        context.flush(sceneBuffer)
    }

    private fun allocateCommands(stack: MemoryStack, count: Int): List<VkCommandBuffer> {
        val info = VkCommandBufferAllocateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .commandPool(pool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(count)
        val out = stack.mallocPointer(count)
        vkCheck(vkAllocateCommandBuffers(device, info, out), "vkAllocateCommandBuffers")
        return (0 until count).map { VkCommandBuffer(out[it], device) }
    }

    private fun beginCommands(stack: MemoryStack, command: VkCommandBuffer, oneTime: Boolean) {
        val begin = VkCommandBufferBeginInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            .flags(if (oneTime) VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT else 0)
        vkCheck(vkBeginCommandBuffer(command, begin), "vkBeginCommandBuffer")
    }

    private fun submitAndWait(stack: MemoryStack, command: VkCommandBuffer) {
        vkCheck(vkResetFences(device, fence), "vkResetFences")
        val submit = VkSubmitInfo.calloc(1, stack).sType(VK_STRUCTURE_TYPE_SUBMIT_INFO).pCommandBuffers(stack.pointers(command))
        vkCheck(vkQueueSubmit(context.queue, submit, fence), "vkQueueSubmit")
        waitForFence()
    }

    private fun waitForFence() {
        val result = vkWaitForFences(device, fence, true, WAIT_TIMEOUT_NS)
        check(result != VK_TIMEOUT) { "Vulkan work did not complete in time" }
        vkCheck(result, "vkWaitForFences")
    }

    private fun releaseGeometry() {
        blas.forEach {
            vkDestroyAccelerationStructureKHR(device, it.handle, null)
            context.destroyBuffer(it.buffer)
        }
        blas = emptyList()
        context.destroyBuffer(vertexBuffer)
        context.destroyBuffer(indexBuffer)
        vertexBuffer = null
        indexBuffer = null
        geometryKey = null
        sceneGeometryKey = null
        lastScene = null
    }

    private fun ensureFrame(width: Int, height: Int): FrameResources {
        frame?.takeIf { it.width == width && it.height == height }?.let { return it }
        releaseFrame()
        val pixels = width.toLong() * height
        return FrameResources(width, height, context.createImage(width, height, COLOR_FORMAT), context.createImage(width, height, DEPTH_FORMAT),
            context.createBuffer(pixels * 8, VK_BUFFER_USAGE_TRANSFER_DST_BIT, Access.READBACK),
            context.createBuffer(pixels * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT, Access.READBACK)).also { frame = it }
    }

    private fun releaseFrame() {
        frame?.let {
            context.destroyImage(it.color)
            context.destroyImage(it.depth)
            context.destroyBuffer(it.colorReadback)
            context.destroyBuffer(it.depthReadback)
        }
        frame = null
    }

    /** Writes the TLAS instances, records the build, bounded dispatches and readback copies, and submits without waiting. */
    private fun enqueue(key: RayFrameKey, width: Int, height: Int, cameraSpec: RaySliceCamera, instances: List<RaySliceInstance>, scene: Boolean) {
        require(width in 1..limits.maxDimension && height in 1..limits.maxDimension && width.toLong() * height <= limits.maxPixels)
        require(instances.isNotEmpty() && instances.size <= limits.maxInstances && instances.all { it.mesh < blas.size })
        val target = ensureFrame(width, height)
        writeInstances(instances)
        val camera = cameraSpec.uniforms(width, height)
        val cameraView = cameraBuffer.mapped!!.asFloatBuffer()
        cameraView.put(camera.copyOf(20))
        context.flush(cameraBuffer)

        MemoryStack.stackPush().use { stack ->
            updateDescriptors(stack, target)
            val rowsPerTile = maxOf(1, MAX_RAYS_PER_DISPATCH / width)
            val tiles = (height + rowsPerTile - 1) / rowsPerTile
            val commands = allocateCommands(stack, tiles + 2)
            recordSetup(stack, commands[0], target, instances.size)
            for (tile in 0 until tiles) {
                recordTile(stack, commands[tile + 1], width, height, tile * rowsPerTile, minOf(rowsPerTile, height - tile * rowsPerTile), scene)
            }
            recordReadback(stack, commands[tiles + 1], target)

            val submits = VkSubmitInfo.calloc(commands.size, stack)
            commands.forEachIndexed { i, command ->
                submits[i].sType(VK_STRUCTURE_TYPE_SUBMIT_INFO).pCommandBuffers(stack.pointers(command))
            }
            vkCheck(vkResetFences(device, fence), "vkResetFences")
            vkCheck(vkQueueSubmit(context.queue, submits, fence), "vkQueueSubmit")
            inFlightCommands = PointerBufferHolder(LongArray(commands.size) { commands[it].address() })
        }
        pending = Pending(key, width, height)
    }

    private fun writeInstances(instances: List<RaySliceInstance>) {
        val data = instanceBuffer.mapped!!
        val tlasData = tlasInstances.mapped!!
        instances.forEachIndexed { i, instance ->
            val values = instance.uniforms()
            val base = i * INSTANCE_BYTES
            for (k in 0 until 20) data.putFloat(base + k * 4, values[k])
            data.putInt(base + 80, vertexOffsets[instance.mesh]).putInt(base + 84, indexOffsets[instance.mesh])
                .putInt(base + 88, 0).putInt(base + 92, 0)
            // Row-major 3x4 from the column-major 4x4.
            val out = i * TLAS_INSTANCE_BYTES
            for (row in 0 until 3) for (col in 0 until 4) tlasData.putFloat(out + (row * 4 + col) * 4, values[col * 4 + row])
            // bit 1: seen by shadow and reflection rays; bit 2: seen by primary rays (blended surfaces have bit 2 only)
            tlasData.putInt(out + 48, i or ((if (instance.primaryOnly) 2 else 3) shl 24))
            tlasData.putInt(out + 52, VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR shl 24)
            tlasData.putLong(out + 56, blasAddress(blas[instance.mesh]))
        }
        context.flush(instanceBuffer)
        context.flush(tlasInstances)
    }

    private fun blasAddress(structure: Blas): Long = MemoryStack.stackPush().use { stack ->
        val info = VkAccelerationStructureDeviceAddressInfoKHR.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR).accelerationStructure(structure.handle)
        vkGetAccelerationStructureDeviceAddressKHR(device, info)
    }

    private fun updateDescriptors(stack: MemoryStack, target: FrameResources) {
        val writes = VkWriteDescriptorSet.calloc(9, stack)
        val structure = VkWriteDescriptorSetAccelerationStructureKHR.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR).pAccelerationStructures(stack.longs(tlas))
        writes[0].sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(descriptorSet).dstBinding(0).descriptorCount(1)
            .descriptorType(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR).pNext(structure.address())
        fun buffer(slot: Int, binding: Int, type: Int, source: VulkanBuffer) {
            val info = VkDescriptorBufferInfo.calloc(1, stack).buffer(source.buffer).offset(0).range(VK_WHOLE_SIZE)
            writes[slot].sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(descriptorSet).dstBinding(binding)
                .descriptorCount(1).descriptorType(type).pBufferInfo(info)
        }
        fun image(slot: Int, binding: Int, source: VulkanImage) {
            val info = VkDescriptorImageInfo.calloc(1, stack).imageView(source.view).imageLayout(VK_IMAGE_LAYOUT_GENERAL)
            writes[slot].sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(descriptorSet).dstBinding(binding)
                .descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(info)
        }
        buffer(1, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, instanceBuffer)
        buffer(2, 2, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, vertexBuffer!!)
        buffer(3, 3, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, indexBuffer!!)
        image(4, 4, target.color)
        image(5, 5, target.depth)
        buffer(6, 6, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, cameraBuffer)
        buffer(7, 7, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, sceneBuffer)
        buffer(8, 8, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, sceneBuffer)
        vkUpdateDescriptorSets(device, writes, null)
    }

    private fun imageBarrier(stack: MemoryStack, image: VulkanImage, oldLayout: Int, newLayout: Int, dstAccess: Int) =
        VkImageMemoryBarrier.calloc(stack).sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER).oldLayout(oldLayout).newLayout(newLayout)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(image.image)
            .dstAccessMask(dstAccess).also {
                it.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1)
            }

    private fun recordSetup(stack: MemoryStack, command: VkCommandBuffer, target: FrameResources, instanceCount: Int) {
        beginCommands(stack, command, oneTime = true)
        val geometry = instancesGeometry(stack, tlasInstances.address)
        val info = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
            .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
            .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR).flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
            .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR).dstAccelerationStructure(tlas).geometryCount(1).pGeometries(geometry)
        info.scratchData().deviceAddress(tlasScratchAddress)
        val range = VkAccelerationStructureBuildRangeInfoKHR.calloc(1, stack).primitiveCount(instanceCount)
        vkCmdBuildAccelerationStructuresKHR(command, info, stack.pointers(range.address()))
        val memory = VkMemoryBarrier.calloc(1, stack).sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
            .srcAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
            .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR or VK_ACCESS_SHADER_READ_BIT)
        val images = VkImageMemoryBarrier.calloc(2, stack)
        images.put(0, imageBarrier(stack, target.color, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL, VK_ACCESS_SHADER_WRITE_BIT))
        images.put(1, imageBarrier(stack, target.depth, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL, VK_ACCESS_SHADER_WRITE_BIT))
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR or VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, memory, null, images)
        vkCheck(vkEndCommandBuffer(command), "vkEndCommandBuffer")
    }

    private fun recordTile(stack: MemoryStack, command: VkCommandBuffer, width: Int, height: Int, firstRow: Int, rows: Int, scene: Boolean) {
        beginCommands(stack, command, oneTime = true)
        vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_COMPUTE, if (scene) pipeline.scenePipeline else pipeline.pipeline)
        vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.pipelineLayout, 0, stack.longs(descriptorSet), null)
        val push: ByteBuffer = stack.malloc(16)
        push.putInt(0, width).putInt(4, height).putInt(8, firstRow).putInt(12, rows)
        vkCmdPushConstants(command, pipeline.pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push)
        vkCmdDispatch(command, (width + 7) / 8, (rows + 7) / 8, 1)
        vkCheck(vkEndCommandBuffer(command), "vkEndCommandBuffer")
    }

    private fun recordReadback(stack: MemoryStack, command: VkCommandBuffer, target: FrameResources) {
        beginCommands(stack, command, oneTime = true)
        val toTransfer = VkMemoryBarrier.calloc(1, stack).sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
            .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, toTransfer, null, null)
        fun copy(image: VulkanImage, buffer: VulkanBuffer) {
            val region = VkBufferImageCopy.calloc(1, stack)
            region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1)
            region.imageExtent().width(target.width).height(target.height).depth(1)
            vkCmdCopyImageToBuffer(command, image.image, VK_IMAGE_LAYOUT_GENERAL, buffer.buffer, region)
        }
        copy(target.color, target.colorReadback)
        copy(target.depth, target.depthReadback)
        val toHost = VkMemoryBarrier.calloc(1, stack).sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT)
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0, toHost, null, null)
        vkCheck(vkEndCommandBuffer(command), "vkEndCommandBuffer")
    }

    override fun submit(request: RayRequest) {
        checkOwner()
        check(pending == null) { "A Vulkan frame is already in flight" }
        sceneGeometryKey = null
        lastScene = null
        guarded {
            val meshes = request.meshes()
            if (geometryKey != meshes) setGeometry(meshes)
            enqueue(request.key, request.width, request.height, request.camera, request.instances(), scene = false)
        }
    }

    override fun submit(request: RaySceneRequest) {
        checkOwner()
        check(pending == null) { "A Vulkan frame is already in flight" }
        val scene = request.scene
        require(scene.instances.isNotEmpty()) { "Empty ray scenes are not supported yet" }
        scene.unsupportedReason()?.let {
            log.warn("Vulkan refuses the scene: $it")
            throw IllegalArgumentException(it)
        }
        require(scene.meshes.size <= limits.maxInstances && scene.instances.size <= limits.maxInstances) { "Scene exceeds the session instance limit" }
        guarded {
            // Static geometry and shading data survive transform-only updates; only changed inputs are rebuilt or re-encoded.
            val dirty = dirtyMeshes(sceneGeometryKey, scene.meshes)
            if (dirty == null) setGeometry(scene.meshes.map { RaySliceMesh(it.positions(), it.indices()) })
            else if (dirty.isNotEmpty()) updateGeometry(dirty, scene.meshes)
            val geometryChanged = dirty == null || dirty.isNotEmpty()
            geometryKey = null
            sceneGeometryKey = scene.meshes
            if (geometryChanged || !shadingUnchanged(lastScene, scene)) {
                uploadScene(RaySceneEncoding(scene).encode())
                lastScene = scene
            }
            enqueue(request.key, request.width, request.height, request.camera, scene.instances.map {
                RaySliceInstance(it.mesh, it.transform().toList(), listOf(1f, 1f, 1f), primaryOnly = scene.materials[it.material].alphaMode == RayAlphaMode.BLEND)
            }, scene = true)
        }
    }

    override fun poll(): RayFrame? {
        checkOwner()
        val request = pending ?: return null
        return guarded {
            val status = vkGetFenceStatus(device, fence)
            if (status == VK_NOT_READY) return@guarded null
            vkCheck(status, "vkGetFenceStatus")
            val target = checkNotNull(frame)
            context.invalidate(target.colorReadback)
            context.invalidate(target.depthReadback)
            val pixels = request.width * request.height
            val colorHalves = target.colorReadback.mapped!!.asShortBuffer()
            val color = FloatArray(pixels * 4) { java.lang.Float.float16ToFloat(colorHalves[it]) }
            val depth = FloatArray(pixels)
            target.depthReadback.mapped!!.asFloatBuffer().get(depth)
            releaseCommands()
            pending = null
            RayFrame(request.key, request.width, request.height, color, depth)
        }
    }

    private fun releaseCommands() {
        val commands = inFlightCommands ?: return
        inFlightCommands = null
        MemoryStack.stackPush().use { stack ->
            val handles = stack.mallocPointer(commands.handles.size)
            commands.handles.forEachIndexed { i, handle -> handles.put(i, handle) }
            vkFreeCommandBuffers(device, pool, handles)
        }
    }

    override fun dispose() {
        check(Thread.currentThread() === worker) { "Vulkan session must stay on its owner worker" }
        if (closed) return
        closed = true
        // Work in flight must finish before its buffers go away, except after a loss where waits could block.
        if (pending != null && !backend.lost()) runCatching { waitForFence() }
        pending = null
        if (!backend.lost()) context.waitIdle(false)
        releaseFrame()
        releaseGeometry()
        vkDestroyAccelerationStructureKHR(device, tlas, null)
        listOf(tlasBuffer, tlasScratch, tlasInstances, instanceBuffer, cameraBuffer, sceneBuffer).forEach { context.destroyBuffer(it) }
        vkDestroyFence(device, fence, null)
        vkDestroyDescriptorPool(device, descriptorPool, null)
        vkDestroyCommandPool(device, pool, null)
        onDisposed()
    }
}
