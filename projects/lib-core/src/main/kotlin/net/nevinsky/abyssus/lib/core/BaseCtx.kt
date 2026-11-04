package net.nevinsky.abyssus.lib.core

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.standard.StandardAssetLoaders
import net.nevinsky.abyssus.lib.core.dto.EcsLoader
import net.nevinsky.abyssus.lib.core.dto.entityWriter
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import org.slf4j.Logger

/**
 * Context for dependency injection. It owns its [executor] and its [assetStorage]: [close] releases both, and whoever
 * made the context calls it, on the GL thread that built the storage's assets, once nothing draws with them.
 */
class BaseCtx(val projectDir: String, shadersPath: String?, shadersAnchor: Class<*>?, val logger: Logger) :
    AutoCloseable {
    val jsonProcessor: JsonProcessor
    val fileLoader: FileLoader
    val metaLoader: AssetMetaLoader
    val executor: ExecutorService
    val assetStorage: AssetStorage
    val componentRegistry: ComponentRegistry
    val shaderStorage: ShaderStorage
    val sceneLoader: SceneLoader
    val ecsLoader: EcsLoader
    val log: Logger = logger

    init {
        componentRegistry = ComponentRegistry()
        jsonProcessor = JsonProcessor(log, listOf(entityWriter(componentRegistry)))
        fileLoader = FileLoader(File(projectDir))
        metaLoader = AssetMetaLoader(jsonProcessor, fileLoader, log)
        // todo migrate to virtual threads
        executor =
            Executors.newFixedThreadPool(2) { r ->
                Thread(r, "asset-prepare").apply { isDaemon = true }
            }
        shaderStorage =
            if (shadersPath != null && shadersAnchor != null) {
                ShaderStorage().withResources(shadersPath, shadersAnchor)
            } else {
                ShaderStorage()
            }
        assetStorage =
            AssetStorage(log, executor, metaLoader::loadBaseMeta).also {
                it.registerAll(StandardAssetLoaders(fileLoader, metaLoader, shaderStorage, log = log).byType)
            }
        sceneLoader = SceneLoader(jsonProcessor, fileLoader, componentRegistry)
        ecsLoader = EcsLoader()
    }

    /**
     * Disposes the asset storage, then stops the asset-prepare threads and waits up to [CLOSE_WAIT_SECONDS] for them to
     * end. Closing again does nothing more.
     */
    override fun close() {
        try {
            assetStorage.dispose()
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)
        }
    }
}

/** How long [BaseCtx.close] waits for an asset preparation in progress to end. */
const val CLOSE_WAIT_SECONDS = 5L
