package net.nevinsky.abyssus.lib.core.assets.model

data class ModelMeta(
    val file: String? = null,
    val format: Format? = null,
    val binary: Boolean = false,
    val materials: List<String> = listOf()
) {
    enum class Format {
        GLTF
    }
}