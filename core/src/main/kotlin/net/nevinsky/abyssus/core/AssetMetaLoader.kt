package net.nevinsky.abyssus.core

import net.nevinsky.abyssus.assets.files.AssetMeta
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.core.AbyssusProjectLayout.Companion.META_FILE
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AssetMetaLoader(
    private val json: JsonProcessor,
    private val fileLoader: FileLoader,
    private val log: org.slf4j.Logger = org.slf4j.helpers.NOPLogger.NOP_LOGGER
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