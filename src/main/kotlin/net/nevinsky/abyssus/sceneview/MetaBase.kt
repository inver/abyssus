package net.nevinsky.abyssus.sceneview

import java.util.*

open class MetaBase<T>(
    val version: Int,
    val lastModified: Long,
    val type: MetaType,
    val additional: T,
    val uuid: UUID? = null
)

enum class MetaType {
    SKYBOX,
    MODEL,
    TERRAIN
}
