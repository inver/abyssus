/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.app.game.controlline.components.PilotComponent
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.app.game.controlline.named
import net.nevinsky.abyssus.lib.gdx.testing.failOnWarnings
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.RigidBodyComponent
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives

/**
 * A [FlightSession] of the bundled field's [planeName], built as the game builds one, flown by [control] and marked by
 * [judge]. [change] edits the plane's component first.
 */
fun fieldSession(
    planeName: String, judge: FlightJudge? = null, control: FlightControlSource = FlightControlSource { 0f },
    change: PlaneComponent.() -> Unit = {},
): FlightSession = fieldSession(planeName, change) { _, _ -> judge to control }

/**
 * As above, with the judge and the control source made by [make] from the plane's (changed) settings and mass, as the
 * game makes them.
 */
fun fieldSession(
    planeName: String, change: PlaneComponent.() -> Unit = {},
    make: (PlaneComponent, Float) -> Pair<FlightJudge?, FlightControlSource>,
): FlightSession {
    val scene = loadField()
    val plane = scene.named(planeName)
    val settings = plane.getComponent(PlaneComponent::class.java).apply(change)
    val mass = plane.getComponent(RigidBodyComponent::class.java).mass
    val pilot = scene.engine.entities.single { it.getComponent(PilotComponent::class.java) != null }
    val (judge, control) = make(settings, mass)
    return FlightSession(scene.engine, pilot, plane, PhysicsAssets(bundledProject().toFile()), failOnWarnings(), JoltNatives(), judge, control)
}
