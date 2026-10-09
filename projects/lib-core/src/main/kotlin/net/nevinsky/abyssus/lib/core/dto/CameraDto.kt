/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.dto

import com.badlogic.gdx.math.Vector3

data class CameraDto(
    val viewPointPosition: Vector3? = null,
    val position: Vector3? = null,
    val far: Float? = null,
    val near: Float? = null,
    val fieldOfView: Float? = null,
)

data class CameraWrapper(val camera: CameraDto)