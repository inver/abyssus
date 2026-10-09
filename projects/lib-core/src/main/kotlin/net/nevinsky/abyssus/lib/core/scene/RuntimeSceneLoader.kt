/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.scene

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import net.nevinsky.abyssus.lib.core.ecs.EcsLoadingWarns
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.ecs.system.LookAtSystem
import net.nevinsky.abyssus.lib.core.ecs.system.SynchronizeCameraComponentSystem
import org.slf4j.Logger

/**
 * Loads the scenes of one project folder into independent Ashley engines, without editor or GL services. Each load
 * owns its engine, entities, resolver and warnings, and logs under the scene's name. The game components of [registry]
 * are checked here, so a registration that fails (see [net.nevinsky.abyssus.lib.runtime.schema.ComponentRegistrationException])
 * fails the construction and no scene loads.
 *
 * A scene that cannot be read is logged once, with its cause, and loads as null, so a caller can carry on with the
 * others.
 */
class RuntimeSceneLoader(
    private val sceneLoader: SceneLoader,
    private val ecsLoader: EcsLoader,
    private val log: Logger,
) {

    /** The scene file [sceneName] (its name in the project's `scenes` folder, extension included). */
    fun load(sceneName: String): SceneContext? = reported(sceneName) {
        loadContext(sceneLoader.load(sceneName))
    }

    /** The scene [text] holds, including unsaved edits; it never reads or writes a scene file. */
    fun loadFromText(text: String): SceneContext? = reported("the scene text") {
        loadContext(sceneLoader.parse(text))
    }

    private fun loadContext(scene: SceneDto): SceneContext {
        val engine = SceneEngine().also {
            it.addSystem(LookAtSystem(it.ids, 0))
            it.addSystem(SynchronizeCameraComponentSystem(it.ids, 2))
        }
        if (scene.ecs == null) {
            return SceneContext(engine, scene)
        }
        val warnings = ecsLoader.loadToEngine(scene.ecs, engine)
        return SceneContext(engine, scene, warnings)
    }

    private fun <T> reported(source: String, read: () -> T?): T? = runCatchingKeepingCancellation(read)
        .getOrElse { error -> log.warn("Could not load $source: ${error.message}", error); null }
}
