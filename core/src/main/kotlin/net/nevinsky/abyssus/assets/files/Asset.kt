package net.nevinsky.abyssus.assets.files

import java.io.File
import java.util.*

/**
 * One folder under a project's `assets`. The tree shows only its `type` and `uuid` as rows; [name] is the folder,
 * [references] the `uuid`s its `meta.json` holds in fields Abyssus resolves to other assets, and [unused] marks an
 * asset no scene reaches.
 */
data class Asset<T>(
    val baseDir: File,
    val meta: AssetMeta<T>,
    val references: List<String> = emptyList(),
    val unused: Boolean = false
) {
    val name: String get() = meta.name
    val type: MetaType get() = meta.type
    val uuid: UUID get() = meta.uuid
}