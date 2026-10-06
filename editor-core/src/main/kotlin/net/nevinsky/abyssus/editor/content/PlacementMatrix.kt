/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.content

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3

/** The world matrix of [this] placement transform. */
fun PlacementTransform.toMatrix(out: Matrix4 = Matrix4()): Matrix4 = out.set(
    Vector3(position.x, position.y, position.z),
    Quaternion(rotation.x, rotation.y, rotation.z, rotation.w),
    Vector3(scale.x, scale.y, scale.z),
)
