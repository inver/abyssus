package net.nevinsky.abyssus.lib.gdx.scene

import net.nevinsky.abyssus.lib.gdx.dto.SceneDto

/** One loaded scene: its [engine] with the entities, the [scene] settings, and what the loader kept of the `ecs` block. */
data class SceneContext(
    val engine: SceneEngine,
    val scene: SceneDto,
    val loadingWarnings: EcsLoadingWarns? =null,
)
