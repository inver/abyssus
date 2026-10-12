/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.components

import com.badlogic.ashley.core.Component

/**
 * The pilot at the circle's centre, holding the handle. Its entity is a kinematic body: the lines run from two points on
 * its vertical axis, [handleSpacing] apart around [handleHeight] above it.
 */
class PilotComponent : Component {
    var handleHeight = 1.5f

    var handleSpacing = 0.1f
}
