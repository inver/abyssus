package net.nevinsky.abyssus.core.assets

import net.nevinsky.abyssus.core.FileLoader
import java.util.UUID

/**
 * Finds the asset folder of a `uuid`, which is how one asset's `meta.json` refers to another (a terrain's splat
 * textures). One index serves one project folder. It keeps no state of its own: every call lists the asset folders and
 * reads their metas through [metaLoader], whose per-file cache makes a repeated scan a stat per folder, so a replaced
 * or deleted asset is never answered from a stale index. Where two folders claim a `uuid` the first by name wins.
 */
class AssetIndex(private val fileLoader: FileLoader, private val metaLoader: AssetMetaLoader) {
    /** Every asset folder by the `uuid` its meta declares. Metas that are unreadable or declare no `uuid` are left out. */
    fun folders(): Map<UUID, String> {
        val out = LinkedHashMap<UUID, String>()
        for (name in fileLoader.assetNames()) {
            val meta = metaLoader.loadBaseMeta(name) ?: continue
            meta.uuid?.let { out.putIfAbsent(it, name) }
        }
        return out
    }

    /** The asset folder whose meta declares [uuid] (as text); null when [uuid] is not a `uuid` or no folder has it. */
    fun folder(uuid: String?): String? {
        val id = uuid?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return folders()[id]
    }
}
