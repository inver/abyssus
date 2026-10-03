/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Engine
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.ecs.component.IdComponent
import net.nevinsky.abyssus.ecs.render.AssetResolver
import net.nevinsky.abyssus.ecs.scene.SceneEcsDocument
import net.nevinsky.abyssus.ecs.scene.SceneEcsLoader
import net.nevinsky.abyssus.ecs.scene.SceneEngine
import net.nevinsky.abyssus.ecs.system.LookAtSystem
import net.nevinsky.abyssus.ecs.system.RenderComponentSystem
import net.nevinsky.abyssus.ecs.system.SynchronizeCameraComponentSystem
import net.nevinsky.abyssus.ecs.system.SynchronizeRenderComponentSystem
import net.nevinsky.abyssus.ecs.system.SynchronizeRenderPoint2PointSystem

/** A scene's [engine] with its [document] (what the loader kept besides the entities). */
class LoadedScene(val engine: SceneEngine, val document: SceneEcsDocument)

/**
 * Creates the scene [Engine]: look-at, render placement, camera sync, point-to-point placement, then the render pass
 * (system priorities, as Mundus registers them), and loads a scene's `ecs` block into it.
 */
open class EcsConfigurator(private val resolver: AssetResolver = AssetResolver { _, _ -> null }) {
    fun createEngine(): SceneEngine = SceneEngine().also(::configure)

    fun load(ecs: JsonNode): LoadedScene {
        val engine = createEngine()
        return LoadedScene(engine, SceneEcsLoader(resolver).load(ecs, engine))
    }

    protected open fun configure(engine: SceneEngine) {
        engine.addSystem(LookAtSystem(engine.ids, 0))
        engine.addSystem(SynchronizeRenderComponentSystem(1))
        engine.addSystem(SynchronizeCameraComponentSystem(engine.ids, 2))
        engine.addSystem(SynchronizeRenderPoint2PointSystem(engine.ids, 3))
        engine.addSystem(RenderComponentSystem(4))
    }
}

object WorldUtils {
    /** Converts every entity with a [clazz] component, given its file id and the component. */
    fun <R, C : Component> getFromWorld(engine: Engine, clazz: Class<C>, converter: (Int, C) -> R): List<R> =
        engine.entities.mapNotNull { entity ->
            val component = entity.getComponent(clazz) ?: return@mapNotNull null
            val id = entity.getComponent(IdComponent::class.java)?.id ?: return@mapNotNull null
            converter(id, component)
        }
}

fun <R, C : Component> Engine.getFromWorld(clazz: Class<C>, converter: (Int, C) -> R): List<R> =
    WorldUtils.getFromWorld(this, clazz, converter)
