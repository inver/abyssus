/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.vitrine

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3

/** How far apart the planes beyond the last table stand when there is no pair of tables to take the spacing from (m). */
const val GROUND_SPACING = 2.5f

/**
 * One table of the display row: [position] is its entity's origin (on the ground), [yaw] its heading in radians about Y
 * (the way its +Z points, where the camera looks from), and [topHeight] the table-top's height above the origin.
 */
class VitrineTable(val position: Vector3, val yaw: Float, val topHeight: Float)

/**
 * A plane as the vitrine sees it: its authored [position] and [rotation], and its model's vertices [points] in model
 * space (null when the model could not be read: the plane then rests on its origin).
 */
class VitrinePlane(val position: Vector3, val rotation: Quaternion, val points: List<Vector3>?)

/** Where a plane stands in the vitrine. */
class VitrinePose(val position: Vector3, val rotation: Quaternion)

/**
 * The poses of the planes on plane select (design decision 4): plane `i` stands on the `i`th table, facing the way the
 * table faces, keeping the tilt it was authored with (a gear stance, say), with its lowest point on the table-top. A
 * plane beyond the last table stands on the ground in line with the tables, spaced like them. Without tables nothing
 * moves. Plain math, no GL: runs on the game's main thread at start-up.
 */
fun vitrinePoses(planes: List<VitrinePlane>, tables: List<VitrineTable>): List<VitrinePose?> {
    if (tables.isEmpty()) return planes.map { null }
    val last = tables.last()
    val stride = if (tables.size >= 2) Vector3(last.position).sub(tables[tables.size - 2].position).also { it.y = 0f }
    else Vector3(0f, 0f, GROUND_SPACING)
    return planes.mapIndexed { i, plane ->
        val table = tables.getOrNull(i)
        val rotation = facing(plane.rotation, (table ?: last).yaw)
        val lowest = lowestPoint(plane.points, rotation)
        val base = table?.position ?: Vector3(last.position).mulAdd(stride, (i - tables.size + 1).toFloat())
        val top = table?.topHeight ?: 0f
        VitrinePose(Vector3(base.x, base.y + top - lowest, base.z), rotation)
    }
}

/** [authored] with its heading replaced by [yaw]: the same tilt, facing the table's way. */
private fun facing(authored: Quaternion, yaw: Float): Quaternion {
    val nose = authored.transform(Vector3(0f, 0f, 1f))
    val authoredYaw = MathUtils.atan2(nose.x, nose.z)
    val tilt = Quaternion().setFromAxisRad(Vector3.Y, authoredYaw).conjugate().mul(authored)
    return Quaternion().setFromAxisRad(Vector3.Y, yaw).mul(tilt)
}

/** The lowest height of [points] once turned by [rotation], relative to the plane's origin; 0 without points. */
private fun lowestPoint(points: List<Vector3>?, rotation: Quaternion): Float {
    if (points.isNullOrEmpty()) return 0f
    val turned = Vector3()
    var lowest = Float.MAX_VALUE
    for (p in points) {
        turned.set(p)
        rotation.transform(turned)
        if (turned.y < lowest) lowest = turned.y
    }
    return lowest
}
