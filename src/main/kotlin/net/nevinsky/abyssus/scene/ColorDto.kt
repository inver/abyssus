package net.nevinsky.abyssus.scene

import net.nevinsky.abyssus.dto.DtoProperty
import net.nevinsky.abyssus.dto.DtoSource
import net.nevinsky.abyssus.dto.DtoValue

data class ColorDto(val r: Float, val g: Float, val b: Float, val a: Float) : DtoSource {
    override fun properties() = listOf(
        DtoProperty("r", DtoValue.Scalar(r)),
        DtoProperty("g", DtoValue.Scalar(g)),
        DtoProperty("b", DtoValue.Scalar(b)),
        DtoProperty("a", DtoValue.Scalar(a)),
    )
}
