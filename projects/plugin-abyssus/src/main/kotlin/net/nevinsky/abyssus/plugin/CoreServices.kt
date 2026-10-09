/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin

import javax.swing.SwingUtilities
import net.nevinsky.abyssus.lib.gdx.editor.ray.rayBackendSelectorFromStartup
import net.nevinsky.abyssus.lib.gdx.editor.meta.AssetFieldDescriptions
import net.nevinsky.abyssus.lib.gdx.editor.meta.AssetMetaEditor
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.gdx.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.gdx.editor.document.DocumentParsing
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.lib.gdx.editor.document.AbyssusDocumentFormat
import net.nevinsky.abyssus.plugin.projectView.HdrPreviewSource
import net.nevinsky.abyssus.lib.raytracing.MetalRayBackendFactory
import net.nevinsky.abyssus.lib.raytracing.VulkanRayBackendFactory
import net.nevinsky.abyssus.lib.gdx.editor.ray.RayBackendService
import net.nevinsky.abyssus.plugin.terrain.NewTerrainFactory
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainAssetWriter
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainGenerator
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainRecipeCodec
import net.nevinsky.abyssus.lib.gdx.editor.terrain.FastNoiseSamplerFactory
import org.slf4j.Logger
import java.util.concurrent.Executors

class DocumentServices(val json: JsonProcessor, val format: AbyssusDocumentFormat, log: Logger) {
    val parsing = DocumentParsing(json, log, format)
}

class AssetServices(json: JsonProcessor, format: AbyssusDocumentFormat, log: Logger) {
    val metaFiles = MetaFiles(AssetMetaReader(json, format))
    val fields = AssetFieldDescriptions()
    val editor = AssetMetaEditor(fields)
    val sceneShaders = ShaderStorage().withResources("/shader/scene", AbyssusCore::class.java)
    val loading = AssetLoading(json, log, AppExecutorUtil.getAppExecutorService(), ShaderStorage())
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
        RayBackendService(rayBackendSelectorFromStartup(providers = mapOf(
            "metal" to { MetalRayBackendFactory(log = log) }, "vulkan" to { VulkanRayBackendFactory(log = log) },
        ), log = log), EditorBundle, publish = { SwingUtilities.invokeLater(it) }, log = log)
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
