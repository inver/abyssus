/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run through `verifyVulkanPackaging`, which puts the built jar (not loose resources) on the classpath. */
class VulkanNativePackagingTest {
    private val classpath = System.getProperty("java.class.path").split(File.pathSeparator).map { File(it).name }

    @Test fun jarHoldsValidSpirvAndNoShaderCompiler() {
        for (name in listOf("slice", "scene")) {
            val spirv = VulkanNativePackagingTest::class.java.getResourceAsStream("/native/vulkan/$name.spv")?.use { it.readBytes() }
            assertNotNull("$name SPIR-V must be built into the module resources (install glslangValidator or glslc)", spirv)
            assertEquals("$name SPIR-V magic number", 0x07230203, java.nio.ByteBuffer.wrap(spirv!!).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
        }
        assertTrue("No shaderc may be packaged", classpath.none { it.contains("shaderc", ignoreCase = true) })
    }

    @Test fun nativesCoverEveryTarget() {
        for (target in listOf("natives-linux", "natives-windows", "natives-macos", "natives-macos-arm64")) {
            assertTrue("lwjgl-vma $target", classpath.any { it.startsWith("lwjgl-vma-") && it.endsWith("-$target.jar") })
            assertTrue("lwjgl $target", classpath.any { it.startsWith("lwjgl-") && !it.startsWith("lwjgl-vma") && it.endsWith("-$target.jar") })
        }
    }

    @Test fun moltenVkIsPackagedForMacOnly() {
        val vulkanNatives = classpath.filter { it.startsWith("lwjgl-vulkan-") && it.contains("natives") }
        assertEquals(listOf("natives-macos", "natives-macos-arm64"), vulkanNatives.map {
            it.removeSuffix(".jar").substringAfter("-natives-").let { target -> "natives-$target" }
        }.sorted())
    }

    @Test fun packagedBackendRendersAFrame() {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.vulkanTests"))
        val result = VulkanRayBackendFactory().probe()
        assertTrue("Vulkan must support the slice: $result", result is RayCapability.Available)
        (result as RayCapability.Available).backend.use { backend ->
            assertEquals("Vulkan", backend.info.name)
            assertTrue(backend.info.gpu.isNotBlank())
            backend.openSession("packaged", RayLimits()).use { session ->
                val mesh = RaySliceMesh(floatArrayOf(-2f, -2f, 0f, 2f, -2f, 0f, 0f, 2f, 0f), intArrayOf(0, 1, 2))
                val instance = RaySliceInstance(0, listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f), listOf(1f, 1f, 1f))
                val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, 0.1f, 100f)
                session.submit(RayRequest(RayFrameKey(1, 1, 1, 1), 1, 1, camera, listOf(mesh), listOf(instance)))
                val deadline = System.nanoTime() + 5_000_000_000L
                var frame: RayFrame? = null
                while (frame == null && System.nanoTime() < deadline) {
                    frame = session.poll()
                    if (frame == null) Thread.sleep(1)
                }
                assertNotNull("Packaged shader must render a frame", frame)
                assertTrue(frame!!.depthValues()[0] < 1f)
            }
            backend.openSession("optics", RayLimits()).use(::assertPackagedSceneOptics)
        }
    }
}
