/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.core.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSkyMeta
import net.nevinsky.abyssus.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.core.assets.texture.TextureMeta
import java.util.UUID
import net.nevinsky.abyssus.core.assets.loading.CompositeAssetLoader
import net.nevinsky.abyssus.core.assets.loading.PreparedAsset
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotStore
import net.nevinsky.abyssus.core.assets.loading.ShaderSource
import net.nevinsky.abyssus.core.assets.model.ModelLoader
import net.nevinsky.abyssus.core.assets.model.ModelMeta
import net.nevinsky.abyssus.core.assets.model.ModelRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.model.RayModelMaterialInfo
import net.nevinsky.abyssus.core.assets.model.RayModelSnapshot
import net.nevinsky.abyssus.core.assets.model.RayModelSource
import net.nevinsky.abyssus.core.assets.sky.RaySkySnapshot
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.ExrLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrPreview
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSkyRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.core.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.core.assets.sky.procedural.ProceduralSkyRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.terrain.RayTerrainSnapshot
import net.nevinsky.abyssus.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.core.assets.terrain.TerrainRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.texture.TextureLoader
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.model.PbrModelMaterial
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Executor

/**
 * Builds the asset loading graph of a project from what the caller provides: [json] for `meta.json`, [log] for
 * problems, [executor] for the off-GL-thread `prepare` step and [skyShaders] for the sky programs (`/shader/sky` in this
 * module). Everything per project (file access, meta reading, the [AssetStorage] that owns the built assets, the
 * optional CPU ray snapshots) is made by [project] and owned by one view.
 */
class AssetLoading(
    val json: JsonProcessor,
    val log: Logger,
    private val executor: Executor,
    private val skyShaders: ShaderSource,
) {
    val toneCurve = ToneCurve()

    /** Thumbnails of HDR skies. The decoder reads whatever file it is given, so it needs no project of its own. */
    val hdrPreview = HdrPreview(ExrLoader(FileLoader(File("."))), toneCurve)

    private val assimp = AssimpModelLoader()

    /**
     * The assets of the project in [projectDir]. Metadata is read from the disk except for the files in [unsaved] (as the
     * editors hold them), which a view replaces with [ProjectAssets.unsaved] as they change.
     */
    fun project(projectDir: File, unsaved: Map<File, String> = emptyMap()): ProjectAssets =
        ProjectAssets(projectDir, unsaved, json, log, executor, skyShaders, toneCurve, assimp)

    /** The model file the `meta.json` of asset [name] names, or null when it names none or the asset is not a model. */
    fun modelFile(projectDir: File, name: String): File? {
        val files = FileLoader(projectDir)
        val meta = AssetMetaLoader(json, files, log).loadBaseMeta(name)?.takeIf { it.type == MetaType.MODEL } ?: return null
        return runCatchingKeepingCancellation {
            files.loadAssetFile(name, meta.typedAdditional<ModelMeta>().file)
        }.getOrNull()
    }

    /** A model's material identifiers and PBR flags, read on the caller's thread without images or GL. */
    fun rayModelMaterials(projectDir: File, name: String): List<RayModelMaterialInfo>? {
        val file = FileHandle(modelFile(projectDir, name) ?: return null)
        return assimp.loadData(file).materials.map { RayModelMaterialInfo(it.id, it is PbrModelMaterial) }
    }
}

/**
 * The loading graph of one project folder: [storage] owns every built asset (models, terrains, textures and skies) and
 * the three [RaySnapshotStore]s hold the optional CPU snapshots ray mode reads. [unsaved] is what the editors hold
 * for `meta.json` files that is not on the disk yet; assign it and later loads read it. GL thread, except what the
 * pool threads of [storage] and the stores run themselves.
 */
class ProjectAssets internal constructor(
    val projectDir: File,
    unsaved: Map<File, String>,
    json: JsonProcessor,
    log: Logger,
    executor: Executor,
    skyShaders: ShaderSource,
    toneCurve: ToneCurve,
    assimp: AssimpModelLoader,
) : Disposable {
    @Volatile
    var unsaved: Map<File, String> = unsaved

    val files = FileLoader(projectDir.absoluteFile)
    val metas = AssetMetaLoader(json, files, log)

    val rayModels: RaySnapshotStore<RayModelSnapshot, RayModelSource> =
        RaySnapshotStore(executor, metas, ModelRaySnapshotLoader(files, assimp), "model")

    private val terrainLoader = TerrainLoader(files, metas)
    private val textureLoader = TextureLoader(files, metas)
    val rayTerrains: RaySnapshotStore<RayTerrainSnapshot, Nothing> =
        RaySnapshotStore(executor, metas, TerrainRaySnapshotLoader(terrainLoader, textureLoader), "terrain")

    private val skyboxLoader = SkyboxLoader(files, metas, skyShaders)
    private val hdrSkyLoader = HdrSkyLoader(metas, ExrLoader(files), skyShaders, toneCurve)
    val raySkies: RaySnapshotStore<RaySkySnapshot, Nothing> = RaySnapshotStore(
        executor, metas,
        SkyRaySnapshotLoader(
            SkyboxRaySnapshotLoader(skyboxLoader), HdrSkyRaySnapshotLoader(hdrSkyLoader), ProceduralSkyRaySnapshotLoader(),
        ),
        "sky",
    )

    val storage: AssetStorage<PreparedAsset, Disposable> = AssetStorage(
        executor,
        UnsavedMetaLoader(json, files, { unsaved }, CompositeAssetLoader(
            metas,
            mapOf(
                MetaType.MODEL to ModelLoader(metas, assimp, files, rayModels),
                MetaType.TERRAIN to terrainLoader,
                MetaType.TEXTURE to textureLoader,
                MetaType.PIXMAP_TEXTURE to textureLoader,
                MetaType.SKYBOX to skyboxLoader,
                MetaType.SKYBOX_PROCEDURAL to ProceduralSkyLoader(files, metas),
                MetaType.SKYBOX_HDR to hdrSkyLoader,
            ),
        )),
        log,
    )

    override fun dispose() = storage.dispose()
}

/**
 * Loads an asset from the `meta.json` text an editor holds when that is not on the disk yet (core's loaders read the
 * disk), and from the disk through [delegate] otherwise. Assets an asset needs (a terrain's textures) are still found
 * from the saved metadata.
 */
private class UnsavedMetaLoader(
    private val json: JsonProcessor,
    private val files: FileLoader,
    private val unsaved: () -> Map<File, String>,
    private val delegate: CompositeAssetLoader,
) : AssetLoader<PreparedAsset, Disposable> by delegate {
    override fun prepare(name: String): PreparedAsset? {
        val file = files.folder(name)?.let { File(it, META_FILE).absoluteFile }
        val text = file?.let { unsaved()[it] } ?: return delegate.prepare(name)
        return delegate.loadPrepared(parse(name, text) ?: return null)
    }

    private fun parse(name: String, text: String): AssetMeta<Any>? = runCatchingKeepingCancellation {
        val node = json.readObject(text)
        val type = node["type"]?.asText()?.let { n -> MetaType.entries.firstOrNull { it.name == n } } ?: MetaType.UNKNOWN
        val block = node["additional"]?.takeIf { it.isObject } ?: json.readObject("{}")
        val additional: Any = additionalClass(type)?.let { json.bind(block, it) } ?: json.bind(block, Map::class.java)
        AssetMeta(
            name = name, formatVersion = node["formatVersion"]?.asInt(1) ?: 1, version = node["version"]?.asInt(1) ?: 1,
            lastModified = node["lastModified"]?.asLong(0) ?: 0, type = type, additional = additional,
            uuid = node["uuid"]?.asText()?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        )
    }.getOrNull()

    private fun additionalClass(type: MetaType): Class<*>? = when (type) {
        MetaType.MODEL -> ModelMeta::class.java
        MetaType.TERRAIN -> TerrainMeta::class.java
        MetaType.SKYBOX -> SkyboxMeta::class.java
        MetaType.SKYBOX_PROCEDURAL -> ProceduralSkyMeta::class.java
        MetaType.SKYBOX_HDR -> HdrSkyMeta::class.java
        MetaType.TEXTURE, MetaType.PIXMAP_TEXTURE -> TextureMeta::class.java
        else -> null
    }
}

/** One ray sky store for every sky kind: the loader of the asset's own type reads it. */
private class SkyRaySnapshotLoader(
    private val skybox: SkyboxRaySnapshotLoader,
    private val hdr: HdrSkyRaySnapshotLoader,
    private val procedural: ProceduralSkyRaySnapshotLoader,
) : RaySnapshotLoader<RaySkySnapshot, Nothing> {
    override fun load(meta: AssetMeta<Any>): RaySkySnapshot? = when (meta.type) {
        MetaType.SKYBOX -> skybox.load(meta)
        MetaType.SKYBOX_HDR -> hdr.load(meta)
        MetaType.SKYBOX_PROCEDURAL -> procedural.load(meta)
        else -> null
    }
}
