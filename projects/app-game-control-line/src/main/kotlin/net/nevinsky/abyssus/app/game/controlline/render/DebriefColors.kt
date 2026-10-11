/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.app.game.controlline.pattern.TrackKind

/** The debrief's ideal track. */
val IDEAL_COLOR = Color(0.25f, 0.9f, 0.35f, 1f)

/** The debrief's flown track. */
val FLOWN_COLOR = Color(1f, 0.55f, 0.1f, 1f)

/** The debrief's guides: the base and the 45° parallel, faint. */
val GUIDE_COLOR = Color(1f, 1f, 1f, 0.35f)

/** The colour a track of [kind] is drawn in, and the width of its line in pixels. */
fun TrackKind.color(): Color = when (this) {
    TrackKind.IDEAL -> IDEAL_COLOR
    TrackKind.FLOWN -> FLOWN_COLOR
    TrackKind.GUIDE -> GUIDE_COLOR
}

fun TrackKind.lineWidth(): Float = if (this == TrackKind.GUIDE) 1f else 3f
