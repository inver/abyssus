/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.vma.Vma.*
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.util.vma.VmaAllocationInfo
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME
import org.lwjgl.vulkan.KHRDeferredHostOperations.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME
import org.lwjgl.vulkan.KHRPortabilityEnumeration.*
import org.lwjgl.vulkan.KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VK12.*
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

internal const val COLOR_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT
internal const val DEPTH_FORMAT = VK_FORMAT_R32_SFLOAT
private const val PORTABILITY_SUBSET = "VK_KHR_portability_subset"
private const val VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation"

/** Maps a Vulkan result to an exception. `VK_ERROR_DEVICE_LOST` is the recoverable device-loss signal. */
internal fun vkCheck(result: Int, what: String) {
    if (result == VK_SUCCESS) return
    if (result == VK_ERROR_DEVICE_LOST) throw RayDeviceLostException("Vulkan device lost during $what")
    throw IllegalStateException("Vulkan $what failed with result $result")
}

/** Host or device memory with its VMA allocation. `mapped` is non-null for host-visible buffers. */
internal class VulkanBuffer(val buffer: Long, val allocation: Long, val size: Long, val mapped: ByteBuffer?, val address: Long)

internal class VulkanImage(val image: Long, val allocation: Long, val view: Long)

/** The physical device chosen by a probe, with everything measured from it (never inferred from names). */
internal class VulkanCandidate(
    val physical: VkPhysicalDevice, val name: String, val queueFamily: Int, val capabilities: RayCapabilities,
    val scratchAlignment: Long, val portabilitySubset: Boolean, val discrete: Boolean,
)

/** Process-global LWJGL Vulkan function loading; nothing is loaded before the first probe. */
internal fun loadVulkanLibrary() = synchronized(VulkanInstance::class.java) {
    try {
        if (VK.getFunctionProvider() == null) VK.create()
    } catch (missing: LinkageError) {
        // A failed static initializer surfaces as ExceptionInInitializerError/NoClassDefFoundError; both mean "no loader".
        throw missing as? UnsatisfiedLinkError ?: UnsatisfiedLinkError("Vulkan runtime not found: ${missing.cause?.message ?: missing.message}")
    }
}

/** Headless instance: no surface or window extensions. Portability enumeration is enabled only when offered (MoltenVK). */
internal class VulkanInstance(validation: Boolean, private val log: Logger = NOPLogger.NOP_LOGGER) : AutoCloseable {
    val instance: VkInstance
    private val messenger: Long
    private val portability: Boolean
    val validationActive: Boolean
    val validationMessages = CopyOnWriteArrayList<String>()
    private val callback: VkDebugUtilsMessengerCallbackEXT?

    init {
        loadVulkanLibrary()
        MemoryStack.stackPush().use { stack ->
            val extensions = instanceExtensions(stack)
            val layers = instanceLayers(stack)
            portability = VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME in extensions
            validationActive = validation && VALIDATION_LAYER in layers && VK_EXT_DEBUG_UTILS_EXTENSION_NAME in extensions
            if (validation && !validationActive) log.warn("Vulkan validation was requested but the $VALIDATION_LAYER layer or VK_EXT_debug_utils is not available")
            val enabled = mutableListOf<String>()
            if (portability) enabled += VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME
            if (validationActive) enabled += VK_EXT_DEBUG_UTILS_EXTENSION_NAME
            val app = VkApplicationInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("Abyssus")).apiVersion(VK_API_VERSION_1_2)
            val create = VkInstanceCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO).pApplicationInfo(app)
                .ppEnabledExtensionNames(names(stack, enabled))
            if (portability) create.flags(VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR)
            if (validationActive) create.ppEnabledLayerNames(names(stack, listOf(VALIDATION_LAYER)))
            val handle = stack.mallocPointer(1)
            vkCheck(vkCreateInstance(create, null, handle), "vkCreateInstance")
            instance = VkInstance(handle[0], create)
            log.info("Vulkan instance created (validation ${if (validationActive) "on" else "off"}, portability ${if (portability) "on" else "off"})")
            if (validationActive) {
                callback = VkDebugUtilsMessengerCallbackEXT.create { severity, _, data, _ ->
                    val text = VkDebugUtilsMessengerCallbackDataEXT.create(data).pMessageString() ?: "validation message"
                    if (severity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT != 0) {
                        validationMessages += text
                        log.warn("Vulkan validation error: $text")
                    } else log.warn("Vulkan validation warning: $text")
                    VK_FALSE
                }
                val info = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack).sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                    .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT)
                    .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
                        VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                    .pfnUserCallback(callback)
                val out = stack.mallocLong(1)
                vkCheck(vkCreateDebugUtilsMessengerEXT(instance, info, null, out), "vkCreateDebugUtilsMessengerEXT")
                messenger = out[0]
            } else {
                callback = null
                messenger = 0L
            }
        }
    }

    /** Best compatible device (discrete preferred), or the reason none qualifies. */
    fun select(): Pair<VulkanCandidate?, String?> = MemoryStack.stackPush().use { stack ->
        val count = stack.ints(0)
        vkCheck(vkEnumeratePhysicalDevices(instance, count, null), "vkEnumeratePhysicalDevices")
        if (count[0] == 0) return@use null to "No Vulkan physical device"
        val devices = stack.mallocPointer(count[0])
        vkCheck(vkEnumeratePhysicalDevices(instance, count, devices), "vkEnumeratePhysicalDevices")
        val candidates = mutableListOf<VulkanCandidate>()
        var reason: String? = null
        for (i in 0 until count[0]) {
            val result = evaluate(stack, VkPhysicalDevice(devices[i], instance))
            result.first?.let {
                candidates += it
                log.info("Vulkan device '${it.name}' qualifies (${if (it.discrete) "discrete" else "integrated or software"}, ${it.capabilities})")
            } ?: run {
                reason = reason ?: result.second
                log.info("Vulkan device rejected: ${result.second}")
            }
        }
        candidates.sortedByDescending { it.discrete }.firstOrNull() to reason
    }

    private fun evaluate(stack: MemoryStack, physical: VkPhysicalDevice): Pair<VulkanCandidate?, String?> {
        stack.push().use {
            val scratch = VkPhysicalDeviceAccelerationStructurePropertiesKHR.calloc(stack)
                .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR)
            val props = VkPhysicalDeviceProperties2.calloc(stack).sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2).pNext(scratch.address())
            vkGetPhysicalDeviceProperties2(physical, props)
            val limits = props.properties().limits()
            val name = props.properties().deviceNameString()
            if (props.properties().apiVersion() < VK_API_VERSION_1_2) return null to "$name: Vulkan 1.2 is required"

            val available = deviceExtensions(stack, physical)
            val missing = listOf(VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME, VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME,
                VK_KHR_RAY_QUERY_EXTENSION_NAME).filter { it !in available }
            val features = queryFeatures(stack, physical)
            val reasonMissingAs = missing.any { it != VK_KHR_RAY_QUERY_EXTENSION_NAME } || !features.accelerationStructure
            val reasonMissingRq = VK_KHR_RAY_QUERY_EXTENSION_NAME in missing || !features.rayQuery
            val family = computeQueueFamily(stack, physical)
            val formats = listOf(COLOR_FORMAT, DEPTH_FORMAT).all { format ->
                val format2 = VkFormatProperties.calloc(stack)
                vkGetPhysicalDeviceFormatProperties(physical, format, format2)
                val needed = VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT or VK_FORMAT_FEATURE_TRANSFER_SRC_BIT
                format2.optimalTilingFeatures() and needed == needed
            }
            val memory = VkPhysicalDeviceMemoryProperties.calloc(stack)
            vkGetPhysicalDeviceMemoryProperties(physical, memory)
            var heap = 0L
            for (h in 0 until memory.memoryHeapCount()) {
                val memoryHeap = memory.memoryHeaps(h)
                if (memoryHeap.flags() and VK_MEMORY_HEAP_DEVICE_LOCAL_BIT != 0) heap += memoryHeap.size()
            }
            val capabilities = RayCapabilities(
                accelerationStructures = !reasonMissingAs && features.bufferDeviceAddress,
                rayQueries = !reasonMissingRq && features.timelineSemaphore && features.descriptorIndexing,
                colorDepthReadback = family >= 0 && formats,
                maxFrameDimension = minOf(limits.maxImageDimension2D(), 4096),
                memoryBudgetBytes = heap / 2,
                maxInstances = VULKAN_MAX_INSTANCES,
                sceneOptics = true,
            )
            val unavailable = capabilities.unavailableReason()
            if (unavailable != null) return null to "$name: ${unavailable.name.lowercase()}"
            return VulkanCandidate(physical, name, family, capabilities,
                maxOf(1L, scratch.minAccelerationStructureScratchOffsetAlignment().toLong()),
                PORTABILITY_SUBSET in available, props.properties().deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) to null
        }
    }

    private class Features(
        val bufferDeviceAddress: Boolean, val timelineSemaphore: Boolean, val descriptorIndexing: Boolean,
        val accelerationStructure: Boolean, val rayQuery: Boolean,
    )

    private fun queryFeatures(stack: MemoryStack, physical: VkPhysicalDevice): Features {
        val v12 = VkPhysicalDeviceVulkan12Features.calloc(stack).sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
        val accel = VkPhysicalDeviceAccelerationStructureFeaturesKHR.calloc(stack)
            .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR)
        val query = VkPhysicalDeviceRayQueryFeaturesKHR.calloc(stack)
            .sType(KHRRayQuery.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_QUERY_FEATURES_KHR)
        v12.pNext(accel.address())
        accel.pNext(query.address())
        val features = VkPhysicalDeviceFeatures2.calloc(stack).sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2).pNext(v12.address())
        vkGetPhysicalDeviceFeatures2(physical, features)
        return Features(
            v12.bufferDeviceAddress(), v12.timelineSemaphore(),
            v12.descriptorIndexing() && v12.runtimeDescriptorArray() && v12.shaderSampledImageArrayNonUniformIndexing(),
            accel.accelerationStructure(), query.rayQuery(),
        )
    }

    private fun computeQueueFamily(stack: MemoryStack, physical: VkPhysicalDevice): Int {
        val count = stack.ints(0)
        vkGetPhysicalDeviceQueueFamilyProperties(physical, count, null)
        val families = VkQueueFamilyProperties.calloc(count[0], stack)
        vkGetPhysicalDeviceQueueFamilyProperties(physical, count, families)
        return (0 until count[0]).firstOrNull { families[it].queueFlags() and VK_QUEUE_COMPUTE_BIT != 0 } ?: -1
    }

    private fun deviceExtensions(stack: MemoryStack, physical: VkPhysicalDevice): Set<String> {
        val count = stack.ints(0)
        vkEnumerateDeviceExtensionProperties(physical, null as CharSequence?, count, null)
        // Driver-defined count: NVIDIA lists enough extensions to overflow the 64 KB thread stack, so allocate on the heap.
        val properties = VkExtensionProperties.calloc(count[0])
        try {
            vkEnumerateDeviceExtensionProperties(physical, null as CharSequence?, count, properties)
            return (0 until count[0]).map { properties[it].extensionNameString() }.toSet()
        } finally {
            properties.free()
        }
    }

    private fun instanceExtensions(stack: MemoryStack): Set<String> {
        val count = stack.ints(0)
        vkEnumerateInstanceExtensionProperties(null as CharSequence?, count, null)
        val properties = VkExtensionProperties.calloc(count[0])
        try {
            vkEnumerateInstanceExtensionProperties(null as CharSequence?, count, properties)
            return (0 until count[0]).map { properties[it].extensionNameString() }.toSet()
        } finally {
            properties.free()
        }
    }

    private fun instanceLayers(stack: MemoryStack): Set<String> {
        val count = stack.ints(0)
        vkEnumerateInstanceLayerProperties(count, null)
        val properties = VkLayerProperties.calloc(count[0])
        try {
            vkEnumerateInstanceLayerProperties(count, properties)
            return (0 until count[0]).map { properties[it].layerNameString() }.toSet()
        } finally {
            properties.free()
        }
    }

    override fun close() {
        if (messenger != 0L) vkDestroyDebugUtilsMessengerEXT(instance, messenger, null)
        vkDestroyInstance(instance, null)
        callback?.free()
    }
}

internal fun names(stack: MemoryStack, values: List<String>): PointerBuffer {
    val buffer = stack.mallocPointer(values.size)
    values.forEachIndexed { i, value -> buffer.put(i, stack.UTF8(value)) }
    return buffer
}

/** Logical device, one compute queue and the VMA allocator. All calls stay on the backend worker. */
internal class VulkanDeviceContext(val instance: VulkanInstance, val candidate: VulkanCandidate) : AutoCloseable {
    val device: VkDevice
    val queue: VkQueue
    val allocator: Long
    private var closed = false

    init {
        MemoryStack.stackPush().use { stack ->
            val priority = stack.floats(1f)
            val queueInfo = VkDeviceQueueCreateInfo.calloc(1, stack).sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(candidate.queueFamily).pQueuePriorities(priority)
            val v12 = VkPhysicalDeviceVulkan12Features.calloc(stack).sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
                .bufferDeviceAddress(true).timelineSemaphore(true).descriptorIndexing(true).runtimeDescriptorArray(true)
                .shaderSampledImageArrayNonUniformIndexing(true)
            val accel = VkPhysicalDeviceAccelerationStructureFeaturesKHR.calloc(stack)
                .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR)
                .accelerationStructure(true)
            val query = VkPhysicalDeviceRayQueryFeaturesKHR.calloc(stack)
                .sType(KHRRayQuery.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_QUERY_FEATURES_KHR).rayQuery(true)
            v12.pNext(accel.address())
            accel.pNext(query.address())
            val extensions = mutableListOf(VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
                VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME, VK_KHR_RAY_QUERY_EXTENSION_NAME)
            if (candidate.portabilitySubset) extensions += PORTABILITY_SUBSET
            val create = VkDeviceCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO).pNext(v12.address())
                .pQueueCreateInfos(queueInfo).ppEnabledExtensionNames(names(stack, extensions))
            val handle = stack.mallocPointer(1)
            vkCheck(vkCreateDevice(candidate.physical, create, null, handle), "vkCreateDevice")
            device = VkDevice(handle[0], candidate.physical, create)
            val queueHandle = stack.mallocPointer(1)
            vkGetDeviceQueue(device, candidate.queueFamily, 0, queueHandle)
            queue = VkQueue(queueHandle[0], device)

            val functions = VmaVulkanFunctions.calloc(stack).set(instance.instance, device)
            val allocatorInfo = VmaAllocatorCreateInfo.calloc(stack).flags(VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT)
                .instance(instance.instance).physicalDevice(candidate.physical).device(device).pVulkanFunctions(functions)
                .vulkanApiVersion(VK_API_VERSION_1_2)
            val out = stack.mallocPointer(1)
            val result = vmaCreateAllocator(allocatorInfo, out)
            if (result != VK_SUCCESS) {
                vkDestroyDevice(device, null)
                vkCheck(result, "vmaCreateAllocator")
            }
            allocator = out[0]
        }
    }

    fun createBuffer(size: Long, usage: Int, access: Access): VulkanBuffer = MemoryStack.stackPush().use { stack ->
        require(size > 0)
        val info = VkBufferCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO).size(size)
            .usage(usage or VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE)
        val allocation = VmaAllocationCreateInfo.calloc(stack).usage(access.usage).flags(access.flags)
        val buffer = stack.mallocLong(1)
        val handle = stack.mallocPointer(1)
        val details = VmaAllocationInfo.calloc(stack)
        vkCheck(vmaCreateBuffer(allocator, info, allocation, buffer, handle, details), "vmaCreateBuffer")
        val mapped = details.pMappedData().takeIf { it != 0L }?.let { MemoryUtil.memByteBuffer(it, size.toInt()) }
        val address = VkBufferDeviceAddressInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO).buffer(buffer[0])
        VulkanBuffer(buffer[0], handle[0], size, mapped, vkGetBufferDeviceAddress(device, address))
    }

    fun destroyBuffer(buffer: VulkanBuffer?) {
        if (buffer != null) vmaDestroyBuffer(allocator, buffer.buffer, buffer.allocation)
    }

    fun flush(buffer: VulkanBuffer) {
        vmaFlushAllocation(allocator, buffer.allocation, 0, VK_WHOLE_SIZE)
    }

    fun invalidate(buffer: VulkanBuffer) {
        vmaInvalidateAllocation(allocator, buffer.allocation, 0, VK_WHOLE_SIZE)
    }

    fun createImage(width: Int, height: Int, format: Int): VulkanImage = MemoryStack.stackPush().use { stack ->
        val info = VkImageCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO).imageType(VK_IMAGE_TYPE_2D)
            .format(format).mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL)
            .usage(VK_IMAGE_USAGE_STORAGE_BIT or VK_IMAGE_USAGE_TRANSFER_SRC_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
        info.extent().width(width).height(height).depth(1)
        val allocation = VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE)
        val image = stack.mallocLong(1)
        val handle = stack.mallocPointer(1)
        vkCheck(vmaCreateImage(allocator, info, allocation, image, handle, null), "vmaCreateImage")
        val view = VkImageViewCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO).image(image[0])
            .viewType(VK_IMAGE_VIEW_TYPE_2D).format(format)
        view.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1)
        val viewOut = stack.mallocLong(1)
        val result = vkCreateImageView(device, view, null, viewOut)
        if (result != VK_SUCCESS) {
            vmaDestroyImage(allocator, image[0], handle[0])
            vkCheck(result, "vkCreateImageView")
        }
        VulkanImage(image[0], handle[0], viewOut[0])
    }

    fun destroyImage(image: VulkanImage?) {
        if (image == null) return
        vkDestroyImageView(device, image.view, null)
        vmaDestroyImage(allocator, image.image, image.allocation)
    }

    /** Skips every wait once the device is lost: destroying a lost device must not block. */
    fun waitIdle(lost: Boolean) {
        if (!lost) vkDeviceWaitIdle(device)
    }

    override fun close() {
        if (closed) return
        closed = true
        vmaDestroyAllocator(allocator)
        vkDestroyDevice(device, null)
    }

    enum class Access(val usage: Int, val flags: Int) {
        /** Written once or per frame by the host, read by the GPU. */
        HOST_WRITE(VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT or VMA_ALLOCATION_CREATE_MAPPED_BIT),
        /** Written by the GPU and read back by the host. */
        READBACK(VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT or VMA_ALLOCATION_CREATE_MAPPED_BIT),
        DEVICE(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, 0),
    }
}
