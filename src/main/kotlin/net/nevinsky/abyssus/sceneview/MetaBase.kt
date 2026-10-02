package net.nevinsky.abyssus.sceneview

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import java.util.*

open class MetaBase<T>(
    val version: Int,
    val lastModified: Long,
    val type: MetaType,
    val additional: T,
    val uuid: UUID? = null
)

enum class MetaType {
    /** A type this plugin does not know, or a `meta.json` that could not be bound. */
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
