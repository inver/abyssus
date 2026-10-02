package net.nevinsky.abyssus.sceneview.skybox

import net.nevinsky.abyssus.sceneview.MetaBase
import net.nevinsky.abyssus.sceneview.MetaType

class SkyboxMeta(
    version: Int, lastModified: Long, type: MetaType, additional: SkyboxAdditional
) : MetaBase<SkyboxAdditional>(version, lastModified, type, additional)

class SkyboxAdditional(
    val top: String?,
    val bottom: String?,
    val left: String?,
    val right: String?,
    val front: String?,
    val back: String?,
)