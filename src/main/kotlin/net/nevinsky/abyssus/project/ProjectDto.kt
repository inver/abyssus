package net.nevinsky.abyssus.project

import net.nevinsky.abyssus.dto.DtoProperty
import net.nevinsky.abyssus.dto.DtoSource
import net.nevinsky.abyssus.dto.DtoValue
import net.nevinsky.abyssus.scene.SceneDto

class ProjectDto(
    val name: String,
    val scenes: List<SceneDto>
) : DtoSource {
    override fun properties() = listOf(
        DtoProperty("name", DtoValue.Scalar(name)),
        DtoProperty("scenes", DtoValue.Items(scenes.mapIndexed { i, s -> s.toValue(sceneLabel(s, i)) })),
    )
}

fun sceneLabel(scene: SceneDto, index: Int): String =
    scene.name?.takeIf { it.isNotBlank() } ?: net.nevinsky.abyssus.AbyssusBundle.message("dtoListElementLabel", "scenes", index)
