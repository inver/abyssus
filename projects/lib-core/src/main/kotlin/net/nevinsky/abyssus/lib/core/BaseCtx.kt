package net.nevinsky.abyssus.lib.core

import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.ExrLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.assets.texture.TextureLoader
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Context for dependency injection
 */
class BaseCtx(val projectDir: String, shadersPath: String, shadersAnchor: Class<*>, val logger: Logger) {
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
        shaderStorage = ShaderStorage().withResources(shadersPath, shadersAnchor)
        assetStorage = AssetStorage(log, executor, metaLoader::loadBaseMeta).also {
            it.registerAll(
                mapOf(
                    MetaType.MODEL to ModelLoader(metaLoader, AssimpModelLoader(), fileLoader),
                    MetaType.TERRAIN to TerrainLoader(fileLoader, metaLoader),
                    MetaType.TEXTURE to TextureLoader(fileLoader, metaLoader),
                    MetaType.PIXMAP_TEXTURE to TextureLoader(fileLoader, metaLoader),
                    MetaType.SKYBOX to SkyboxLoader(fileLoader, metaLoader, shaderStorage),
                    MetaType.SKYBOX_PROCEDURAL to ProceduralSkyLoader(fileLoader, metaLoader),
                    MetaType.SKYBOX_HDR to HdrSkyLoader(metaLoader, ExrLoader(fileLoader), shaderStorage, ToneCurve()),
                ),
            )
        }
        componentRegistry = ComponentRegistry()
        sceneLoader = SceneLoader(jsonProcessor, fileLoader)
        ecsLoader = EcsLoader(jsonProcessor, assetStorage, componentRegistry)
    }
}