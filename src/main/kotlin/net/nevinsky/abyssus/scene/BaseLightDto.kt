package net.nevinsky.abyssus.scene

import net.nevinsky.abyssus.dto.DtoProperty
import net.nevinsky.abyssus.dto.DtoSource
import net.nevinsky.abyssus.dto.DtoValue

data class BaseLightDto(val color: ColorDto?, val intensity: Float?) : DtoSource {
    override fun properties() = listOf(
        DtoProperty("color", color?.toValue() ?: DtoValue.Scalar(null)),
        DtoProperty("intensity", DtoValue.Scalar(intensity)),
    )
}
