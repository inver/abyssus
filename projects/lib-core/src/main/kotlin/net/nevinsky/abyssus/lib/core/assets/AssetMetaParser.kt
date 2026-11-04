package net.nevinsky.abyssus.lib.core.assets

import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/**
 * The one place that turns asset `meta.json` text into an [AssetMeta] and back: [read] binds text with an
 * [AssetMetaBinder], [write] serializes through [JsonProcessor]. It has no file access and no state, so the
 * loader (which adds files and a cache) and the editor (which changes `additional`) share it.
 */
class AssetMetaParser(
    private val json: JsonProcessor,
    private val binder: AssetMetaBinder = AssetMetaBinder(json),
) {
    /**
     * Binds [text] to an [AssetMeta] named [assetName], its `additional` block bound to the class of its `type`. Throws
     * when [text] is not a JSON object.
     */
    fun read(assetName: String, text: String): AssetMeta<Any> = binder.bind(assetName, json.readObject(text))

    /** [meta] as one line of JSON, or indented with a trailing newline when [pretty]. */
    fun write(meta: AssetMeta<*>, pretty: Boolean = false): String = if (pretty) {
        json.pretty(meta)
    } else {
        json.toString(meta)
    }

    /** A copy of [meta] whose `additional` block is [additional]; the other fields are kept. */
    fun withAdditional(meta: AssetMeta<Any>, additional: Any): AssetMeta<Any> = AssetMeta(
        type = meta.type, name = meta.name, formatVersion = meta.formatVersion, version = meta.version,
        lastModified = meta.lastModified, additional = additional, uuid = meta.uuid,
    )
}
