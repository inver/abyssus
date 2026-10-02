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

import com.fasterxml.jackson.databind.JsonNode
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
    val ecs: JsonNode? = null,
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
