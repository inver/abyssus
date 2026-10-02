package net.nevinsky.abyssus.sceneview

open class MetaBase<T>(
    val version: Int,
    val lastModified: Long,
    val type: MetaType,
    val additional: T
)

enum class MetaType {
    SKYBOX
}
