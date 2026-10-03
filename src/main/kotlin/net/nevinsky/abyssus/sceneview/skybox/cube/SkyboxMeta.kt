package net.nevinsky.abyssus.sceneview.skybox.cube

import net.nevinsky.abyssus.sceneview.MetaBase
import net.nevinsky.abyssus.sceneview.MetaType
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
