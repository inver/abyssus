/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.scene.SceneContext
import net.nevinsky.abyssus.lib.gdx.scene.RuntimeSceneLoader
import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import net.nevinsky.abyssus.app.game.controlline.components.CONTROL_LINE_COMPONENTS
import net.nevinsky.abyssus.lib.gdx.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.gdx.ecs.EcsLoader
import net.nevinsky.abyssus.lib.gdx.scene.SceneLoader
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.gdx.ecs.component.NameComponent
import java.nio.file.Path

/** The bundled `ControlLine` project, supplied by Gradle so tests do not depend on their working directory. */
fun bundledProject(): Path =
    Path.of(checkNotNull(System.getProperty("controlLine.project")) { "run through Gradle: controlLine.project is not set" })

/** The bundled field scene with the game's components; [messages] collects the log. */
fun loadField(messages: MutableList<String> = mutableListOf()): SceneContext {
    val log = warningsTo(messages)
    val json = JsonProcessor(log)
    val registry = ComponentRegistry().also { it.registerAll(CONTROL_LINE_COMPONENTS) }
    val loader = RuntimeSceneLoader(
        SceneLoader(json, FileLoader(bundledProject().toFile())),
        EcsLoader(json, registry),
        log,
    )
    return requireNotNull(loader.load("Field.scene")) { messages.joinToString("\n") }
}

/** A physics world over [scene] of the bundled project. */
fun fieldWorld(scene: SceneContext, messages: MutableList<String> = mutableListOf()): PhysicsWorld =
    PhysicsWorld(scene.engine, PhysicsAssets(bundledProject().toFile()),
        warningsTo(messages), JoltNatives())

/** The entity named [name]. */
fun SceneContext.named(name: String): Entity =
    engine.entities.single { it.getComponent(NameComponent::class.java)?.name == name }
