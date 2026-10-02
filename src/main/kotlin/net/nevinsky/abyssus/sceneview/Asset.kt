package net.nevinsky.abyssus.sceneview

import java.io.File

data class Asset<T>(
    val name: String,
    val meta: MetaBase<T>,
    val baseDir: File,
    val references: List<String> = emptyList(),
    val unused: Boolean = false
)