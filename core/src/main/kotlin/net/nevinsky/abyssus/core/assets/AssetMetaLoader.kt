package net.nevinsky.abyssus.core.assets

import net.nevinsky.abyssus.core.assets.json.JsonProcessor
import net.nevinsky.abyssus.core.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.core.FileLoader
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AssetMetaLoader(
    private val json: net.nevinsky.abyssus.core.assets.json.JsonProcessor,
    private val fileLoader: FileLoader,
    private val log: Logger = NOPLogger.NOP_LOGGER
) {
    private val metasCache = ConcurrentHashMap<File, CachedMeta>()

    fun loadBaseMeta(assetName: String?): net.nevinsky.abyssus.core.assets.AssetMeta<Any>? {
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
        val result = runCatchingKeepingCancellation { json.parse(text, AssetMeta::class.java) }
        val meta = result.getOrNull() as AssetMeta<Any>?
        val error = result.exceptionOrNull()
        metasCache[dir] = CachedMeta(stamp, text, meta, error)
        if (error != null) {
            log.warn("$file: ${error.message}", error)
        }
        return meta
    }

    private data class CachedMeta(
        val stamp: Pair<Long, Long>?,
        val text: String,
        val meta: AssetMeta<Any>?,
        val error: Throwable?
    )

}