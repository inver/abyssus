package net.nevinsky.abyssus.lib.core

import net.nevinsky.abyssus.lib.core.assets.standard.StandardAssetLoaders
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Context for dependency injection
 */
class BaseCtx(val projectDir: String, shadersPath: String?, shadersAnchor: Class<*>?, val logger: Logger) {
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
        jsonProcessor = JsonProcessor(log)
        fileLoader = FileLoader(File(projectDir))
        metaLoader = AssetMetaLoader(jsonProcessor, fileLoader, log)
        //todo migrate to virtual threads
        executor = Executors.newFixedThreadPool(2) { r ->
            Thread(r, "asset-prepare").apply { isDaemon = true }
        }
        shaderStorage = if (shadersPath != null && shadersAnchor != null) {
            ShaderStorage().withResources(shadersPath, shadersAnchor)
        } else {
            ShaderStorage()
        }
        assetStorage = AssetStorage(log, executor, metaLoader::loadBaseMeta).also {
            it.registerAll(StandardAssetLoaders(fileLoader, metaLoader, shaderStorage, log = log).byType)
        }
        componentRegistry = ComponentRegistry()
        sceneLoader = SceneLoader(jsonProcessor, fileLoader, componentRegistry)
        ecsLoader = EcsLoader()
    }
}