package net.nevinsky.abyssus.sceneview.skybox

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import net.nevinsky.abyssus.sceneview.MetaBase
import net.nevinsky.abyssus.sceneview.MetaType

/** Bound from a skybox's `meta.json`; the creators are explicit because Jackson has no Kotlin module here to find them. */
class SkyboxMeta @JsonCreator constructor(
    @param:JsonProperty("version") version: Int,
    @param:JsonProperty("lastModified") lastModified: Long,
    @param:JsonProperty("type") type: MetaType,
    @param:JsonProperty("additional") additional: SkyboxAdditional,
) : MetaBase<SkyboxAdditional>(version, lastModified, type, additional)

class SkyboxAdditional @JsonCreator constructor(
    @param:JsonProperty("top") val top: String?,
    @param:JsonProperty("bottom") val bottom: String?,
    @param:JsonProperty("left") val left: String?,
    @param:JsonProperty("right") val right: String?,
    @param:JsonProperty("front") val front: String?,
    @param:JsonProperty("back") val back: String?,
)
