/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.components

import com.badlogic.ashley.core.Component

/**
 * The real aircraft a scale replica copies, shown on its plane-select card: the [prototype]'s name, the year of its
 * first flight, its real [wingspan] in metres and the model [scale] as a denominator (8 is 1:8). On a plane entity of
 * class [PlaneClass.SCALE]; the replica's flight numbers are its `PlaneComponent`.
 */
class ScaleComponent : Component {
    var prototype = ""

    var firstFlight = 0

    var wingspan = 0f

    var scale = 1
}
