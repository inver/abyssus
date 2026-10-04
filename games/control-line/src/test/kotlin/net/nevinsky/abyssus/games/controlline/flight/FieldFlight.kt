/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flight

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.assets.AssetLog
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.games.controlline.bundledProject
import net.nevinsky.abyssus.games.controlline.components.PilotComponent
import net.nevinsky.abyssus.games.controlline.components.PlaneComponent
import net.nevinsky.abyssus.games.controlline.loadField
import net.nevinsky.abyssus.games.controlline.named
import net.nevinsky.abyssus.physics.PHYSICS_STEP
import net.nevinsky.abyssus.physics.PhysicsAssets
import net.nevinsky.abyssus.physics.jolt.JoltNatives
import net.nevinsky.abyssus.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.runtime.ecs.LoadedScene
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent

/**
 * A flight of the bundled field's [planeName], stepped as the game steps it: [Flight.update] before each fixed step.
 * [change] edits the plane's component before the world is built.
 */
class FieldFlight(planeName: String, change: PlaneComponent.() -> Unit = {}) : AutoCloseable {
    val scene: LoadedScene = loadField()
    val plane: Entity = scene.named(planeName)
    val settings: PlaneComponent = plane.getComponent(PlaneComponent::class.java).apply(change)
    private val assets = PhysicsAssets(AssetFiles(bundledProject().toFile(), JsonProcessor()))
    val world = PhysicsWorld(scene.engine, assets, AssetLog { m, _ -> error(m) }, JoltNatives())
    val ground = groundOf(scene.engine, assets)
    val pilot: Entity = scene.engine.entities.single { it.getComponent(PilotComponent::class.java) != null }
    val rig = LineRig(world, pilot, plane, ground)
    val flight = Flight(world, rig, ground).also { it.takeoff() }

    val position: Vector3 get() = plane.getComponent(PositionComponent::class.java).localPosition
    val rotation: Quaternion get() = plane.getComponent(PositionComponent::class.java).localRotation
    val height: Float get() = position.y - ground.heightAt(position.x, position.z)
    val speed: Float get() = world.bodyOf(plane)!!.velocity().len()

    /** One fixed step. */
    fun step() {
        flight.update(PHYSICS_STEP)
        world.step()
    }

    /** Steps for [seconds] (or until the flight ends) with the handle at [tilt]; [each] runs after every step. */
    fun fly(seconds: Float, tilt: Float = rig.tilt, each: () -> Unit = {}) {
        rig.tilt = tilt
        repeat(Math.round(seconds / PHYSICS_STEP)) {
            if (flight.finished) return
            step()
            each()
        }
    }

    override fun close() = world.close()
}
