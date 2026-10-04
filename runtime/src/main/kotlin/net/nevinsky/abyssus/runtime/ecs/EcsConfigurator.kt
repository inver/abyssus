/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Engine
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.runtime.ecs.component.IdComponent
import net.nevinsky.abyssus.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsDocument
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsLoader
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEngine
import net.nevinsky.abyssus.runtime.ecs.system.LookAtSystem
import net.nevinsky.abyssus.runtime.ecs.system.RenderComponentSystem
import net.nevinsky.abyssus.runtime.ecs.system.SynchronizeCameraComponentSystem
import net.nevinsky.abyssus.runtime.ecs.system.SynchronizeRenderComponentSystem
import net.nevinsky.abyssus.runtime.ecs.system.SynchronizeRenderPoint2PointSystem

/** A scene's [engine] with its [document] (what the loader kept besides the entities). */
class LoadedScene(
    val engine: SceneEngine,
    val document: SceneEcsDocument,
    val scene: net.nevinsky.abyssus.runtime.scene.SceneDto = net.nevinsky.abyssus.runtime.scene.SceneDto(),
)

/**
 * Creates the scene [Engine]: look-at, render placement, camera sync, point-to-point placement, then the render pass
 * (system priorities, in evaluation order), and loads a scene's `ecs` block into it.
 */
open class EcsConfigurator(
    private val resolver: AssetResolver = AssetResolver { _, _ -> null },
    private val log: net.nevinsky.abyssus.assets.AssetLog = net.nevinsky.abyssus.assets.AssetLog { _, _ -> },
) {
    fun createEngine(): SceneEngine = SceneEngine().also(::configure)

    fun load(ecs: JsonNode): LoadedScene {
        val engine = createEngine()
        return LoadedScene(engine, SceneEcsLoader(resolver, log).load(ecs, engine))
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
fun <R, C : Component> Engine.getFromWorld(clazz: Class<C>, converter: (Int, C) -> R): List<R> =
    entities.mapNotNull { entity ->
        val component = entity.getComponent(clazz) ?: return@mapNotNull null
        val id = entity.getComponent(IdComponent::class.java)?.id ?: return@mapNotNull null
        converter(id, component)
    }
