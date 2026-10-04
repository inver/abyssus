/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.assets.AssetLoading
import net.nevinsky.abyssus.assets.AssetLog
import net.nevinsky.abyssus.assets.ShaderSource
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.raytracing.MetalRayBackendFactory
import net.nevinsky.abyssus.raytracing.VulkanRayBackendFactory
import net.nevinsky.abyssus.sceneview.RayBackendSelector
import net.nevinsky.abyssus.sceneview.RayBackendService
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The plugin's composition root for the `core` module: builds its objects once for the IDE and hands them out. Code in
 * `core` never looks this up; plugin code passes what it gets from here into `core` constructors.
 */
@Service(Service.Level.APP)
class AbyssusCore : Disposable {
    val json = JsonProcessor()

    /** The scene view's own GLSL (grid lines, overlay, terrain), from the plugin's resources. */
    val sceneShaders = ShaderSource("/shader/scene", AbyssusCore::class.java)

    /** Asset loading for every scene view: problems go to the IDE log, `prepare` runs on the IDE's pool. */
    val loading = AssetLoading(
        json,
        AssetLog { message, error -> Logger.getInstance("Abyssus.assets").warn(message, error) },
        AppExecutorUtil.getAppExecutorService(),
        ShaderSource("/shader/sky", AssetLoading::class.java),
    )

    private val rayServiceHolder = lazy {
        RayBackendService(RayBackendSelector.fromStartup(providers = mapOf(
            "metal" to { MetalRayBackendFactory() },
            "vulkan" to { VulkanRayBackendFactory() },
        )))
    }

    /**
     * The optional ray tracing service. Created on first use, so ordinary raster startup builds no providers and loads
     * no native library; a backend is only probed when a view's Ray Tracing toggle is switched on.
     */
    internal val rayService: RayBackendService get() = rayServiceHolder.value

    private val rayConverterHolder = lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "abyssus-ray-convert").apply { isDaemon = true } }
    }

    /** One thread that converts scene snapshots (including skin deformation) off the render thread. */
    internal val rayConverter: Executor get() = rayConverterHolder.value

    override fun dispose() {
        if (rayServiceHolder.isInitialized()) rayService.close()
        if (rayConverterHolder.isInitialized()) rayConverterHolder.value.shutdown()
    }
}
