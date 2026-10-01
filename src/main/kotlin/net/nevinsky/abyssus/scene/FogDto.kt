package net.nevinsky.abyssus.scene

import net.nevinsky.abyssus.dto.DtoProperty
import net.nevinsky.abyssus.dto.DtoSource
import net.nevinsky.abyssus.dto.DtoValue

data class FogDto(val color: ColorDto?, val density: Float?, val gradient: Float?) : DtoSource {
    override fun properties() = listOf(
        DtoProperty("color", color?.toValue() ?: DtoValue.Scalar(null)),
        DtoProperty("density", DtoValue.Scalar(density)),
        DtoProperty("gradient", DtoValue.Scalar(gradient)),
    )
}
