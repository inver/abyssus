/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Engine
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.ecs.component.IdComponent
import net.nevinsky.abyssus.lib.core.ecs.system.*
import net.nevinsky.abyssus.lib.core.scene.EcsLoadingWarns
import net.nevinsky.abyssus.lib.core.scene.SceneEngine
import net.nevinsky.abyssus.lib.runtime.SceneContext

/** A scene's [engine] with its [document] (what the loader kept besides the entities). */
class LoadedScene(
    val engine: SceneEngine,
    val document: EcsLoadingWarns,
    val scene: SceneDto = SceneDto(),
)

/**
 * Creates the scene [Engine]: look-at, render placement, camera sync, point-to-point placement, then the render pass
 * (system priorities, in evaluation order), and loads a scene's `ecs` block into it.
 */
open class EcsConfigurator(private val ecsLoader: EcsLoader) {
    fun createEngine(): SceneEngine = SceneEngine().also(::configure)

    fun load(scene: SceneDto, ecs: JsonNode?): SceneContext {
        val engine = createEngine()
        if (ecs == null) {
            return SceneContext(engine, scene)
        }
        val warnings = ecsLoader.loadToEngine(ecs, engine)
        return SceneContext(engine, scene, warnings)
    }

    protected open fun configure(engine: SceneEngine) {
        engine.addSystem(LookAtSystem(engine.ids, 0))
        engine.addSystem(SynchronizeCameraComponentSystem(engine.ids, 2))
    }
}

/** Converts every entity with a [clazz] component, given its file id and the component. */
fun <R, C : Component> Engine.getFromWorld(clazz: Class<C>, converter: (Long, C) -> R): List<R> =
    entities.mapNotNull { entity ->
        val component = entity.getComponent(clazz) ?: return@mapNotNull null
        val id = entity.getComponent(IdComponent::class.java)?.id ?: return@mapNotNull null
        converter(id, component)
    }
