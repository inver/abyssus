package net.nevinsky.abyssus.assets.files

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import java.io.File

/**
 * One folder under a project's `assets`. The tree shows only its `type` and `uuid` as rows; [name] is the folder,
 * [references] the `uuid`s its `meta.json` holds in fields Abyssus resolves to other assets, and [unused] marks an
 * asset no scene reaches.
 */
@JsonPropertyOrder("type", "uuid")
data class Asset<T>(
    @get:JsonIgnore val name: String,
    @get:JsonIgnore val meta: MetaBase<T>,
    @get:JsonIgnore val baseDir: File,
    @get:JsonIgnore val references: List<String> = emptyList(),
    @get:JsonIgnore val unused: Boolean = false
) {
    val type: String get() = meta.type.name
    val uuid: String? get() = meta.uuid?.toString()
}