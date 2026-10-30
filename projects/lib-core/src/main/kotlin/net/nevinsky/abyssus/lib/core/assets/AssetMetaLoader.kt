package net.nevinsky.abyssus.lib.core.assets

import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.FileLoader
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AssetMetaLoader(
    private val json: JsonProcessor,
    private val fileLoader: FileLoader,
    private val log: Logger = NOPLogger.NOP_LOGGER,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
    private val binder: AssetMetaBinder = AssetMetaBinder(json)
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
        format.requireSupported(node, DocumentKind.ASSET)
        return binder.bind(assetName, node)
    }

    private data class CachedMeta(
        val stamp: Pair<Long, Long>?,
        val text: String,
        val meta: AssetMeta<Any>?,
        val error: Throwable?
    )

}