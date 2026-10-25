/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import net.nevinsky.abyssus.lib.core.assets.MetaType

/** A point of a [PathArrangementComponent] line, in world coordinates. */
data class PathPoint(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f)

/** The project model an arrangement places along its line. */
data class PathAsset(var type: MetaType = MetaType.MODEL, var assetName: String = "")


/**
 * A line of [points] on the terrain and the settings that place copies of one model [asset] along it. Data only: the
 * editor writes the copies as ordinary entities, each with a `ParentComponent` pointing at this entity, and lists them
 * in [slots] (`NO_ENTITY`, `-1`, for a slot with no copy). The runtime runs no system for it.
 *
 * The points and the copies are in **world coordinates**. Moving the arrangement entity does not move them, and a
 * transform hierarchy built on `ParentComponent` must not compose it for the copies of an arrangement.
 */
class PathArrangementComponent : Component {
    var points: MutableList<PathPoint> = ArrayList()
    var sides: PathSides = PathSides.CENTER

    var closed: Boolean = false
    var asset: PathAsset = PathAsset()
    var spacing: Float = 10f
    var offset: Float = 0f
    var alignToPath: Boolean = true
    var yawOffset: Float = 0f

    var slots: MutableList<Int> = ArrayList()
}

/** Which side of the line a copy stands on. */
enum class PathSides {
    @JsonEnumDefaultValue
    CENTER,
    LEFT,
    RIGHT,
    BOTH,
}
