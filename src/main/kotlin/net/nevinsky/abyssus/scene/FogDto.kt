/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
