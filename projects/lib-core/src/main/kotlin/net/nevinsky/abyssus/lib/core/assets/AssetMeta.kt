package net.nevinsky.abyssus.lib.core.assets

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

const val META_VERSION_DEFAULT = 1

/**
 * The `meta.json` of one asset folder. [name] is the folder, not a JSON field: [AssetMetaLoader] sets it. [additional]
 * is the typed block of [type] (see [AssetMetaLoader]); the other fields default so a sparse meta still loads, and [uuid] is null for an asset that declares none.
 */
open class AssetMeta<T>(
    type: MetaType,
    name: String = "",
    val formatVersion: Int = 1,
    val version: Int = 1,
    val lastModified: Long = 0,
    val additional: T,
    val uuid: UUID? = null
) : BaseMeta(type, name) {
    @Suppress("UNCHECKED_CAST")
    fun <T> typedAdditional(): T {
        return additional as T
    }
}

open class BaseMeta(
    val type: MetaType = MetaType.UNKNOWN,
    @param:JsonProperty("assetName") @get:JsonProperty("assetName") val name: String = "",
) {
    @JsonIgnore
    fun isTerrain(): Boolean = type == MetaType.TERRAIN
}

enum class MetaType {
    @JsonEnumDefaultValue
    UNKNOWN,
    SKYBOX,
    SKYBOX_PROCEDURAL,
    SKYBOX_HDR,
    TEXTURE,
    PIXMAP_TEXTURE,
    MATERIAL,
    SHADER,
    MODEL,
    TERRAIN,
    CLOUDS,
    FOLIAGE,
    UI
}
