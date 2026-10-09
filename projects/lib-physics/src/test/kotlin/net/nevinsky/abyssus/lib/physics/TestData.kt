/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import org.slf4j.Logger
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.scene.SceneContext
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import java.io.File

/** Shared native test fixtures, supplied by Gradle so tests do not depend on their working directory. */
fun testProject(name: String): File =
    File(checkNotNull(System.getProperty("abyssus.testData")) { "run through Gradle: abyssus.testData is not set" }, "project/$name")

/**
 * A scene loader for the project in [project]: the physics components are registered unless [physics] is false, plus
 * the [extra] game components. [log] receives what the load warns about. Nothing is drawn, so no asset loader is
 * registered.
 */
fun physicsLoader(
    project: File,
    log: Logger,
    physics: Boolean = true,
    extra: Map<String, Class<out Component>> = emptyMap(),
): RuntimeSceneLoader {
    val json = JsonProcessor(log)
    val registry = ComponentRegistry().also {
        if (physics) it.registerAll(PHYSICS_COMPONENTS)
        it.registerAll(extra)
    }
    return RuntimeSceneLoader(
        SceneLoader(json, FileLoader(project)),
        EcsLoader(json, registry),
        log,
    )
}

/** `Main Scene` of the `Physics` fixture with the physics components registered; [messages] collects the log. */
fun loadPhysicsScene(messages: MutableList<String> = mutableListOf()): SceneContext {
    val loader = physicsLoader(testProject("Physics"), warningsTo(messages))
    return requireNotNull(loader.load("Main Scene.scene")) { messages.joinToString("\n") }
}

/** The entity named [name]. */
fun SceneContext.named(name: String): Entity =
    engine.entities.single { it.getComponent(NameComponent::class.java)?.name == name }
