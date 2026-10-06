/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.collision.BoundingBox

/** A model entity as the last frame drew it: [local] bounds in its own space and its [world] matrix. */
class SnapshotBox(val id: String, val local: BoundingBox, val world: Matrix4)

/**
 * What the renderer drew in a frame, copied so that picking and gizmo queries need neither the renderer nor GL: the
 * [camera] (updated, so it can unproject), the drawn model [boxes] and [terrains], and the [drawnVersion] that changes
 * when the drawn set does. Immutable by convention: everything is a copy of what the renderer goes on to change.
 */
class FrameSnapshot(
    val camera: PerspectiveCamera,
    val boxes: List<SnapshotBox>,
    val terrains: List<TerrainTarget>,
    val drawnVersion: Long,
)

/** A copy of [source] as of now, with its matrices updated. */
fun copyOfCamera(source: PerspectiveCamera): PerspectiveCamera =
    PerspectiveCamera(source.fieldOfView, source.viewportWidth, source.viewportHeight).also {
        it.position.set(source.position)
        it.direction.set(source.direction)
        it.up.set(source.up)
        it.near = source.near
        it.far = source.far
        it.update()
    }

fun snapshotBoxOf(id: String, local: BoundingBox, world: Matrix4) = SnapshotBox(id, BoundingBox(local), Matrix4(world))

fun snapshotTerrainOf(target: TerrainTarget) = TerrainTarget(target.entityId, target.data, Matrix4(target.world))

