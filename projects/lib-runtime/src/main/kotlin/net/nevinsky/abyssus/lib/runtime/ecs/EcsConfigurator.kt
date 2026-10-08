/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Engine
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.IdComponent
import net.nevinsky.abyssus.lib.core.ecs.system.LookAtSystem
import net.nevinsky.abyssus.lib.core.ecs.system.RenderComponentSystem
import net.nevinsky.abyssus.lib.core.ecs.system.SynchronizeCameraComponentSystem
import net.nevinsky.abyssus.lib.core.ecs.system.SynchronizeRenderComponentSystem
import net.nevinsky.abyssus.lib.core.ecs.system.SynchronizeRenderPoint2PointSystem
import net.nevinsky.abyssus.lib.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.lib.core.scene.SceneEcsDocument
import net.nevinsky.abyssus.lib.core.scene.SceneEngine
import net.nevinsky.abyssus.lib.runtime.schema.GameComponents
import org.slf4j.Logger

/** A scene's [engine] with its [document] (what the loader kept besides the entities). */
class LoadedScene(
    val engine: SceneEngine,
    val document: SceneEcsDocument,
    val scene: SceneDto = SceneDto(),
)

/**
 * Creates the scene [Engine]: look-at, render placement, camera sync, point-to-point placement, then the render pass
 * (system priorities, in evaluation order), and loads a scene's `ecs` block into it.
 */
open class EcsConfigurator(
    private val mapper: ObjectMapper,
    private val resolver: AssetResolver = AssetResolver { _, _ -> null },
    private val log: Logger,
    private val game: GameComponents,
) {
    fun createEngine(): SceneEngine = SceneEngine().also(::configure)

    fun load(ecs: JsonNode): LoadedScene {
        val engine = createEngine()
        return LoadedScene(engine, EcsLoader(mapper, resolver, log, game).load(ecs, engine))
    }

    protected open fun configure(engine: SceneEngine) {
        engine.addSystem(LookAtSystem(engine.ids, 0))
        engine.addSystem(SynchronizeRenderComponentSystem(1))
        engine.addSystem(SynchronizeCameraComponentSystem(engine.ids, 2))
        engine.addSystem(SynchronizeRenderPoint2PointSystem(engine.ids, 3))
        engine.addSystem(RenderComponentSystem(4))
    }
}

/** Converts every entity with a [clazz] component, given its file id and the component. */
fun <R, C : Component> Engine.getFromWorld(clazz: Class<C>, converter: (Long, C) -> R): List<R> =
    entities.mapNotNull { entity ->
        val component = entity.getComponent(clazz) ?: return@mapNotNull null
        val id = entity.getComponent(IdComponent::class.java)?.id ?: return@mapNotNull null
        converter(id, component)
    }
