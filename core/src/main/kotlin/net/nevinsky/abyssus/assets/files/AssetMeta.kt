package net.nevinsky.abyssus.assets.files

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import java.util.*

open class AssetMeta<T>(
    val version: Int,
    val lastModified: Long,
    val type: MetaType,
    val additional: T,
    val uuid: UUID? = null
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
