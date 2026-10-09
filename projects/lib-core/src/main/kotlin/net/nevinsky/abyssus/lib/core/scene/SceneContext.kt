package net.nevinsky.abyssus.lib.core.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import net.nevinsky.abyssus.lib.core.ecs.EcsLoadingWarns

/** One loaded scene: its [engine] with the entities, the [scene] settings, and what the loader kept of the `ecs` block. */
data class SceneContext(
    val engine: SceneEngine,
    val scene: SceneDto,
    val loadingWarnings: EcsLoadingWarns? =null,
)
