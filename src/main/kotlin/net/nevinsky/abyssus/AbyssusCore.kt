/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.assets.AssetLoading
import net.nevinsky.abyssus.core.ModelLogging
import net.nevinsky.abyssus.log.IntellijLoggerFactory
import org.slf4j.ILoggerFactory
import net.nevinsky.abyssus.assets.ShaderSource
import net.nevinsky.abyssus.assets.edit.AssetFieldDescriptions
import net.nevinsky.abyssus.assets.edit.AssetMetaEditor
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.terrain.generation.TerrainAssetWriter
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerator
import net.nevinsky.abyssus.assets.terrain.generation.TerrainHeightEncoder
import net.nevinsky.abyssus.assets.terrain.generation.TerrainRecipeCodec
import net.nevinsky.abyssus.assets.terrain.noise.FastNoiseSamplerFactory
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
    /**
     * The one logging interface of every module is SLF4J: this factory hands `gdx-model`, `core` and `raytracing` loggers
     * over the IDE logger (`Abyssus.assets`, `Abyssus.model`, `Abyssus.ray`, `Abyssus.scenes` in `idea.log`).
     */
    val loggers: ILoggerFactory = IntellijLoggerFactory("Abyssus")

    init {
        ModelLogging.logger = loggers.getLogger("model")
    }

    val json = JsonProcessor()
    val format = net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat()
    val scenes = net.nevinsky.abyssus.runtime.SceneLoading(
        json,
        loggers.getLogger("scenes"),
        format,
    )
    val metaFiles = net.nevinsky.abyssus.dto.MetaFiles(net.nevinsky.abyssus.assets.files.AssetMetaReader(json, format))

    /** The editable `meta.json` fields of each asset type and the editor that changes them one at a time. */
    val assetFields = AssetFieldDescriptions()
    val assetEditor = AssetMetaEditor(assetFields)

    /** Terrain generation: seeded heights, their file encoding, new terrain files and the Abyssus-only recipe. */
    val terrainGenerator = TerrainGenerator(FastNoiseSamplerFactory())
    val heightEncoder = TerrainHeightEncoder()
    val terrainWriter = TerrainAssetWriter(json, heightEncoder)
    val terrainRecipes = TerrainRecipeCodec(json)
    val newTerrains = net.nevinsky.abyssus.terrain.NewTerrainFactory(json, terrainWriter, heightEncoder, terrainRecipes)


    /** The scene view's own GLSL (grid lines, overlay, terrain), from the plugin's resources. */
    val sceneShaders = ShaderSource("/shader/scene", AbyssusCore::class.java)

    /** Asset loading for every scene view: problems go to the IDE log, `prepare` runs on the IDE's pool. */
    val loading = AssetLoading(
        json,
        loggers.getLogger("assets"),
        AppExecutorUtil.getAppExecutorService(),
        ShaderSource("/shader/sky", AssetLoading::class.java),
    )

    /** The loading pipeline's Radiance sky pieces, for the chooser and the Properties panel. */
    val hdrPreviews: net.nevinsky.abyssus.projectView.HdrPreviewSource = object : net.nevinsky.abyssus.projectView.HdrPreviewSource {
        override val files get() = loading.hdrFiles
        override val decoder get() = loading.decoder
        override val preview get() = loading.hdrPreview
    }

    private val rayServiceHolder = lazy {
        val rayLog = loggers.getLogger("ray")
        RayBackendService(RayBackendSelector.fromStartup(providers = mapOf(
            "metal" to { MetalRayBackendFactory(log = rayLog) },
            "vulkan" to { VulkanRayBackendFactory(log = rayLog) },
        ), log = rayLog), log = rayLog)
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
