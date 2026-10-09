/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.pick

import net.nevinsky.abyssus.lib.gdx.editor.content.Rgba
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3

/** Somewhere to draw colored line segments; the marker and gizmo geometry is written against it so tests need no GL. */
interface LineSink {
    fun line(from: Vec3, to: Vec3, color: Rgba)
}
