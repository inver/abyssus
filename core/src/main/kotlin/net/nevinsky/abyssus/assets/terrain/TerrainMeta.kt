package net.nevinsky.abyssus.assets.terrain

data class TerrainMeta(
    val file: String? = null,
    val size: Int = 0,
    val uv: Float = 0f,
    val splatMap: String? = null,
    val splatBase: String? = null,
    val splatR: String? = null,
    val splatG: String? = null,
    val splatB: String? = null,
    val splatA: String? = null,
) {
    companion object {
        const val SPLAT_MAP_KEY = "splatMap"
    }
}