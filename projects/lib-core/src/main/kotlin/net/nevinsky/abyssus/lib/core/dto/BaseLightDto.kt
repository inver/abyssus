/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.dto

import com.badlogic.gdx.graphics.Color

data class BaseLightDto(
    val color: Color? = null,
    val intensity: Float? = null
)
