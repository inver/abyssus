/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.components

import com.badlogic.ashley.core.Component

/**
 * A table of the display row plane select stands the planes on, in entity id order. [topHeight] is the height of the
 * table-top above the table entity's origin in metres.
 */
class VitrineTableComponent : Component {
    var topHeight = 0.75f
}
