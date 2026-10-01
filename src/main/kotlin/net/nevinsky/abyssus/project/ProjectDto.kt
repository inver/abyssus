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

/** `Main Scene (6275127)`: the scene name followed by its id; the index stands in for a missing name. */
fun sceneLabel(scene: SceneDto, index: Int): String {
    val name = scene.name?.takeIf { it.isNotBlank() }
        ?: net.nevinsky.abyssus.AbyssusBundle.message("dtoListElementLabel", "scenes", index)
    return scene.id?.let { "$name ($it)" } ?: name
}
