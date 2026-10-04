/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs.component

/*
 * The value a scene file means when it omits a field, as Mundus reads it. The scene view, the Properties panel and the
 * component codecs all take them from here, so they cannot disagree.
 */

const val LIGHT_INTENSITY = 1f

/** Reach of a point or spot light that does not name one. */
const val LIGHT_RANGE = 100f
const val LIGHT_CONE_ANGLE = 45f
const val LIGHT_EDGE_SOFTNESS = 0.2f

/** libGDX `PerspectiveCamera` defaults, for the fields a camera leaves out. */
const val CAMERA_NEAR = 1f
const val CAMERA_FAR = 100f
const val CAMERA_FOV = 67f
