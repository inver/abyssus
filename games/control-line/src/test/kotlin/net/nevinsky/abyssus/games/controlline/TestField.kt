/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline

import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.testing.warningsTo
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.games.controlline.components.ControlLineComponents
import net.nevinsky.abyssus.physics.PhysicsAssets
import net.nevinsky.abyssus.physics.jolt.JoltNatives
import net.nevinsky.abyssus.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.runtime.SceneLoading
import net.nevinsky.abyssus.runtime.ecs.LoadedScene
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import java.nio.file.Path

/** The bundled `ControlLine` project, supplied by Gradle so tests do not depend on their working directory. */
fun bundledProject(): Path =
    Path.of(checkNotNull(System.getProperty("controlLine.project")) { "run through Gradle: controlLine.project is not set" })

/** The bundled field scene with the game's components; [messages] collects the log. */
fun loadField(messages: MutableList<String> = mutableListOf()): LoadedScene {
    val loading = SceneLoading(JsonProcessor(), warningsTo(messages), registry = ControlLineComponents())
    return requireNotNull(loading.load(bundledProject().resolve("scenes/Field.scene"))) { messages.joinToString("\n") }
}

/** A physics world over [scene] of the bundled project. */
fun fieldWorld(scene: LoadedScene, messages: MutableList<String> = mutableListOf()): PhysicsWorld =
    PhysicsWorld(scene.engine, PhysicsAssets(AssetFiles(bundledProject().toFile(), JsonProcessor())),
        warningsTo(messages), JoltNatives())

/** The entity named [name]. */
fun LoadedScene.named(name: String): Entity =
    engine.entities.single { it.getComponent(NameComponent::class.java)?.name == name }
