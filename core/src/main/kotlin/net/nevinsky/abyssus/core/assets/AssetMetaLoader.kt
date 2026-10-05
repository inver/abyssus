package net.nevinsky.abyssus.core.assets

import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.assets.model.ModelMeta
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSkyMeta
import net.nevinsky.abyssus.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.core.assets.texture.TextureMeta
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AssetMetaLoader(
    private val json: JsonProcessor,
    private val fileLoader: FileLoader,
    private val log: Logger = NOPLogger.NOP_LOGGER
) {
    private val metasCache = ConcurrentHashMap<File, CachedMeta>()

    fun loadBaseMeta(assetName: String?): AssetMeta<Any>? {
        if (assetName.isNullOrBlank()) return null
        val dir = fileLoader.folder(assetName) ?: return null

        val file = File(dir, META_FILE)
        val stamp = if (file.isFile) file.lastModified() to file.length() else null
        val cached = metasCache[dir]
        if (stamp != null && cached?.stamp == stamp) {
            return cached.meta
        }
        val text = runCatchingKeepingCancellation { file.takeIf { it.isFile }?.readText() }.getOrNull() ?: return null
        if (cached != null && cached.text == text) {
            return cached.meta
        }
        val result = runCatchingKeepingCancellation { parse(assetName, text) }
        val meta = result.getOrNull()
        val error = result.exceptionOrNull()
        metasCache[dir] = CachedMeta(stamp, text, meta, error)
        if (error != null) {
            log.warn("$file: ${error.message}", error)
        }
        return meta
    }

    /** Binds [text] to an [AssetMeta] named [assetName], its `additional` block bound to the class of its `type`. */
    private fun parse(assetName: String, text: String): AssetMeta<Any> {
        val node = json.readObject(text)
        val type =
            node["type"]?.asText()?.let { name -> MetaType.entries.firstOrNull { it.name == name } } ?: MetaType.UNKNOWN
        val block = node["additional"]?.takeIf { it.isObject } ?: json.readObject("{}")
        val additional: Any = additionalClass(type)?.let { json.bind(block, it) } ?: json.bind(block, Map::class.java)
        return AssetMeta(
            name = assetName,
            formatVersion = node["formatVersion"]?.asInt(1) ?: 1,
            version = node["version"]?.asInt(1) ?: 1,
            lastModified = node["lastModified"]?.asLong(0) ?: 0,
            type = type,
            additional = additional,
            uuid = node["uuid"]?.asText()?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        )
    }

    /** The class of the `additional` block of a [type]; null for kinds with no typed block. */
    private fun additionalClass(type: MetaType): Class<*>? = when (type) {
        MetaType.MODEL -> ModelMeta::class.java
        MetaType.TERRAIN -> TerrainMeta::class.java
        MetaType.SKYBOX -> SkyboxMeta::class.java
        MetaType.SKYBOX_PROCEDURAL -> ProceduralSkyMeta::class.java
        MetaType.SKYBOX_HDR -> HdrSkyMeta::class.java
        MetaType.TEXTURE, MetaType.PIXMAP_TEXTURE -> TextureMeta::class.java
        else -> null
    }

    private data class CachedMeta(
        val stamp: Pair<Long, Long>?,
        val text: String,
        val meta: AssetMeta<Any>?,
        val error: Throwable?
    )

}