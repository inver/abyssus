/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.components

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.runtime.schema.Field
import net.nevinsky.abyssus.lib.runtime.schema.SceneComponent

/**
 * The pilot at the circle's centre, holding the handle. Its entity is a kinematic body: the lines run from two points on
 * its vertical axis, [handleSpacing] apart around [handleHeight] above it.
 */
@SceneComponent("PilotComponent", label = "Pilot")
class PilotComponent : Component {
    @Field(label = "Handle height (m)", group = "Handle", min = 0.0)
    var handleHeight = 1.5f

    @Field(label = "Line spacing at the handle (m)", group = "Handle", min = 0.0, minExclusive = true)
    var handleSpacing = 0.1f
}
