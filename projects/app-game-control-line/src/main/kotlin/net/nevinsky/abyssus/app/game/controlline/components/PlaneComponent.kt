/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3

/** The kind of control-line plane, shown on plane select. */
enum class PlaneClass { SPEED, STUNT, TRAINER }

/**
 * A control-line plane: its lines, engine and aerodynamics. Its name is the entity's `NameComponent`; its mass and
 * collider are the physics components.
 *
 * The plane's frame: the nose is +Z, up is +Y and the inboard (left) wing, towards the pilot, is +X. Leadouts are
 * points in that frame.
 */
class PlaneComponent : Component {
    var planeClass = PlaneClass.TRAINER

    var lineLength = 15f

    var lineDiameter = 0.0004f

    var leadoutUp = Vector3(0.45f, 0f, 0.02f)

    var leadoutDown = Vector3(0.45f, 0f, -0.04f)

    var thrust = 4f

    var fuelTime = 120f

    var wingArea = 0.2f

    var liftSlope = 4.5f

    var zeroLiftDrag = 0.03f

    var elevatorEffect = 0.5f

    var pitchDamping = 12f

    var rudderOffset = 2f

    var engineOffset = 2f
}
