package net.nevinsky.abyssus.scene

import com.google.gson.JsonElement
import net.nevinsky.abyssus.dto.DtoProperty
import net.nevinsky.abyssus.dto.DtoSource
import net.nevinsky.abyssus.dto.DtoValue
import net.nevinsky.abyssus.dto.toDtoValue

data class SceneDto(
    val id: Long? = null,
    val name: String? = null,
    val ambientLightEnabled: Boolean? = null,
    val ambientLight: BaseLightDto? = null,
    val fogEnabled: Boolean? = null,
    val fog: FogDto? = null,
    val skyboxEnabled: Boolean? = null,
    val skyboxName: String? = null,
    val ecs: JsonElement? = null,
) : DtoSource {
    override fun properties() = listOf(
        DtoProperty("id", DtoValue.Scalar(id)),
        DtoProperty("name", DtoValue.Scalar(name)),
        DtoProperty("ambientLightEnabled", DtoValue.Scalar(ambientLightEnabled)),
        DtoProperty("ambientLight", ambientLight?.toValue() ?: DtoValue.Scalar(null)),
        DtoProperty("fogEnabled", DtoValue.Scalar(fogEnabled)),
        DtoProperty("fog", fog?.toValue() ?: DtoValue.Scalar(null)),
        DtoProperty("skyboxEnabled", DtoValue.Scalar(skyboxEnabled)),
        DtoProperty("skyboxName", DtoValue.Scalar(skyboxName)),
        DtoProperty("ecs", ecs?.toDtoValue() ?: DtoValue.Scalar(null)),
    )
}
