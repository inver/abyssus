package net.nevinsky.abyssus.core.assets

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*

const val META_VERSION_DEFAULT = 1

open class AssetMeta<T>(
    @JsonIgnore
    val name: String,
    val formatVersion: Int,
    val version: Int,
    val lastModified: Long,
    val type: net.nevinsky.abyssus.core.assets.MetaType,
    val additional: T,
    val uuid: UUID = UUID.randomUUID()
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> typedAdditional(): T {
        return additional as T
    }
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
    TERRAIN
}
