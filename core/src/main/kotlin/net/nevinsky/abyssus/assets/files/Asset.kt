package net.nevinsky.abyssus.assets.files

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import java.io.File

/**
 * One folder under a project's `assets`. The tree shows only its `type` and `uuid` as rows; [name] is the folder,
 * [references] the `uuid`s its `meta.json` holds in fields Abyssus resolves to other assets, and [unused] marks an
 * asset no scene reaches.
 */
data class Asset<T>(
    val name: String,
    val meta: AssetMeta<T>,
    val baseDir: File,
    val references: List<String> = emptyList(),
    val unused: Boolean = false
) {
    val type: String get() = meta.type.name
    val uuid: String? get() = meta.uuid?.toString()
}