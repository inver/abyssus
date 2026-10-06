/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.core.assets.loading.ShaderSource
import net.nevinsky.abyssus.dto.AssetMetaReader
import net.nevinsky.abyssus.dto.DocumentParsing
import net.nevinsky.abyssus.dto.MetaFiles
import net.nevinsky.abyssus.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.projectView.HdrPreviewSource
import net.nevinsky.abyssus.raytracing.MetalRayBackendFactory
import net.nevinsky.abyssus.raytracing.VulkanRayBackendFactory
import net.nevinsky.abyssus.sceneview.RayBackendSelector
import net.nevinsky.abyssus.sceneview.RayBackendService
import net.nevinsky.abyssus.terrain.NewTerrainFactory
import net.nevinsky.abyssus.terrain.TerrainAssetWriter
import net.nevinsky.abyssus.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.terrain.generation.TerrainGenerator
import net.nevinsky.abyssus.terrain.generation.TerrainRecipeCodec
import net.nevinsky.abyssus.terrain.noise.FastNoiseSamplerFactory
import org.slf4j.Logger
import java.util.concurrent.Executors

class DocumentServices(val json: JsonProcessor, val format: AbyssusDocumentFormat, log: Logger) {
    val parsing = DocumentParsing(json, log, format)
}

class AssetServices(json: JsonProcessor, format: AbyssusDocumentFormat, log: Logger) {
    val metaFiles = MetaFiles(AssetMetaReader(json, format))
    val fields = AssetFieldDescriptions()
    val editor = AssetMetaEditor(fields)
    val sceneShaders = ShaderSource("/shader/scene", AbyssusCore::class.java)
    val loading = AssetLoading(json, log, AppExecutorUtil.getAppExecutorService(), ShaderSource("/shader/sky", AssetLoading::class.java))
    val hdrPreviews = object : HdrPreviewSource { override val preview get() = loading.hdrPreview }
}

class TerrainServices(json: JsonProcessor) {
    val generator = TerrainGenerator(FastNoiseSamplerFactory())
    val heightEncoder = TerrainHeightEncoder()
    val writer = TerrainAssetWriter(json, heightEncoder)
    val recipes = TerrainRecipeCodec(json)
    val newTerrains = NewTerrainFactory(json, writer, heightEncoder, recipes)
}

/** Native resources keep their dedicated worker ownership and are closed only if initialized. */
internal class RayServices(log: Logger, val exposure: () -> Float) : AutoCloseable {
    val log = log
    private val serviceHolder = lazy {
        RayBackendService(RayBackendSelector.fromStartup(providers = mapOf(
            "metal" to { MetalRayBackendFactory(log = log) }, "vulkan" to { VulkanRayBackendFactory(log = log) },
        ), log = log), log = log)
    }
    val service get() = serviceHolder.value
    private val converterHolder = lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "abyssus-ray-convert").apply { isDaemon = true } }
    }
    val converter get() = converterHolder.value
    override fun close() {
        if (serviceHolder.isInitialized()) serviceHolder.value.close()
        if (converterHolder.isInitialized()) converterHolder.value.shutdown()
    }
}
