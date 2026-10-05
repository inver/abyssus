package net.nevinsky.abyssus.runtime

import net.nevinsky.abyssus.core.scene.Scene
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsDocument
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEngine

/** One loaded scene: its [engine] with the entities, the [scene] settings, and what the loader kept of the `ecs` block. */
data class SceneContext(
    val engine: SceneEngine,
    val scene: Scene,
    val document: SceneEcsDocument,
)
