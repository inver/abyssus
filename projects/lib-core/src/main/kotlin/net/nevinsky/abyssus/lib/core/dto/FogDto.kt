/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.gdx.graphics.Color

data class FogDto(
    val color: Color? = null,
    val density: Float? = null,
    val gradient: Float? = null
)
