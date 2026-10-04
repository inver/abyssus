package net.nevinsky.abyssus.assets.sky.cube

import net.nevinsky.abyssus.assets.files.MetaBase
import net.nevinsky.abyssus.assets.files.MetaType
import java.util.*

class SkyboxMeta(
    version: Int,
    lastModified: Long,
    type: MetaType,
    additional: SkyboxAdditional,
    uuid: UUID? = null
) : MetaBase<SkyboxAdditional>(version, lastModified, type, additional, uuid)

class SkyboxAdditional(
    val top: String?,
    val bottom: String?,
    val left: String?,
    val right: String?,
    val front: String?,
    val back: String?,
)

/** The faces of a skybox in `meta.json`'s order: the property names of [SkyboxAdditional]. */
val SKYBOX_FACES = listOf("top", "bottom", "left", "right", "front", "back")
