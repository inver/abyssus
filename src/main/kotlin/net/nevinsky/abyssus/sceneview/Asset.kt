package net.nevinsky.abyssus.sceneview

import java.io.File

data class Asset<T>(
    val metaBase: MetaBase<T>,
    val baseDir: File
)