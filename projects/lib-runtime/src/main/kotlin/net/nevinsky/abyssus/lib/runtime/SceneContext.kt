package net.nevinsky.abyssus.lib.runtime

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.scene.SceneEcsDocument
import net.nevinsky.abyssus.lib.core.scene.SceneEngine

/** One loaded scene: its [engine] with the entities, the [scene] settings, and what the loader kept of the `ecs` block. */
data class SceneContext(
    val engine: SceneEngine,
    val scene: SceneDto,
    val document: SceneEcsDocument,
)
