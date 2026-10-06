package net.nevinsky.abyssus.lib.runtime

import net.nevinsky.abyssus.lib.core.scene.Scene
import net.nevinsky.abyssus.lib.runtime.ecs.scene.SceneEcsDocument
import net.nevinsky.abyssus.lib.runtime.ecs.scene.SceneEngine

/** One loaded scene: its [engine] with the entities, the [scene] settings, and what the loader kept of the `ecs` block. */
data class SceneContext(
    val engine: SceneEngine,
    val scene: Scene,
    val document: SceneEcsDocument,
)
