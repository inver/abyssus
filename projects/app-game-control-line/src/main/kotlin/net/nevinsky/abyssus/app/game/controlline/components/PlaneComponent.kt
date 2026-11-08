/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.components

import com.badlogic.ashley.core.Component

/**
 * The kind of control-line plane, shown on plane select. [SCALE] is a replica of a real aircraft (see
 * `ScaleComponent`).
 */
enum class PlaneClass { SPEED, STUNT, TRAINER, SCALE }

/**
 * A control-line plane: its lines, engine and aerodynamics. Its name is the entity's `NameComponent`; its mass and
 * collider are the physics components.
 *
 * The plane's frame: the nose is +Z, up is +Y and the inboard (left) wing, towards the pilot, is +X. The leadouts are
 * the plane's child dummies `leadoutUp` and `leadoutDown`, at points in that frame (see `PlaneLeadouts`).
 */
class PlaneComponent : Component {
    var planeClass = PlaneClass.TRAINER

    var lineLength = 15f

    var lineDiameter = 0.0005f

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
