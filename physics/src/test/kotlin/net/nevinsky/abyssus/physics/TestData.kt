/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics

import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.testing.warningsTo
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.runtime.SceneLoading
import net.nevinsky.abyssus.runtime.ecs.LoadedScene
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import java.io.File

/** Shared native test fixtures, supplied by Gradle so tests do not depend on their working directory. */
fun testProject(name: String): File =
    File(checkNotNull(System.getProperty("abyssus.testData")) { "run through Gradle: abyssus.testData is not set" }, "project/$name")

/** `Main Scene` of the `Physics` fixture with the physics components registered; [messages] collects the log. */
fun loadPhysicsScene(messages: MutableList<String> = mutableListOf()): LoadedScene {
    val loading = SceneLoading(JsonProcessor(), warningsTo(messages), registry = PhysicsComponents())
    return requireNotNull(loading.load(testProject("Physics").toPath().resolve("scenes/Main Scene.scene"))) { messages.joinToString("\n") }
}

/** The entity named [name]. */
fun LoadedScene.named(name: String): Entity =
    engine.entities.single { it.getComponent(NameComponent::class.java)?.name == name }
