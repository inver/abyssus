/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3

/** Somewhere to draw colored line segments; the marker and gizmo geometry is written against it so tests need no GL. */
interface LineSink {
    fun line(from: Vector3, to: Vector3, color: Color)

    /** A line [widthPx] screen pixels wide; a sink that cannot draw width draws it one pixel wide. */
    fun thickLine(from: Vector3, to: Vector3, color: Color, widthPx: Float) = line(from, to, color)
}
