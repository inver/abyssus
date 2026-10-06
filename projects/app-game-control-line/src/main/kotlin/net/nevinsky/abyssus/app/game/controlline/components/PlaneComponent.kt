/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.runtime.schema.Field
import net.nevinsky.abyssus.lib.runtime.schema.SceneComponent

/** The kind of control-line plane, shown on plane select. */
enum class PlaneClass { SPEED, STUNT, TRAINER }

/**
 * A control-line plane: its lines, engine and aerodynamics. Its name is the entity's `NameComponent`; its mass and
 * collider are the physics components.
 *
 * The plane's frame: the nose is +Z, up is +Y and the inboard (left) wing, towards the pilot, is +X. Leadouts are
 * points in that frame.
 */
@SceneComponent("PlaneComponent", label = "Plane")
class PlaneComponent : Component {
    @Field(label = "Class")
    var planeClass = PlaneClass.TRAINER

    @Field(label = "Line length (m)", group = "Lines", min = 5.0, max = 30.0)
    var lineLength = 15f

    @Field(label = "Line diameter (m)", group = "Lines", min = 0.0, minExclusive = true)
    var lineDiameter = 0.0004f

    @Field(label = "Up-line leadout", group = "Lines")
    var leadoutUp = Vector3(0.45f, 0f, 0.02f)

    @Field(label = "Down-line leadout", group = "Lines")
    var leadoutDown = Vector3(0.45f, 0f, -0.04f)

    @Field(label = "Thrust (N)", group = "Engine", min = 0.0)
    var thrust = 4f

    @Field(label = "Fuel time (s)", group = "Engine", min = 0.0)
    var fuelTime = 120f

    @Field(label = "Wing area (m²)", group = "Aerodynamics", min = 0.0, minExclusive = true)
    var wingArea = 0.2f

    @Field(label = "Lift slope (per rad)", group = "Aerodynamics", min = 0.0)
    var liftSlope = 4.5f

    @Field(label = "Zero-lift drag", group = "Aerodynamics", min = 0.0)
    var zeroLiftDrag = 0.03f

    @Field(label = "Elevator effect", group = "Aerodynamics", min = 0.0)
    var elevatorEffect = 0.5f

    @Field(label = "Pitch damping", group = "Aerodynamics", min = 0.0)
    var pitchDamping = 12f

    @Field(label = "Rudder offset (°)", group = "Trim", min = -15.0, max = 15.0)
    var rudderOffset = 2f

    @Field(label = "Engine offset (°)", group = "Trim", min = -15.0, max = 15.0)
    var engineOffset = 2f
}
