/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.dto

data class BaseLightDto(
    val color: ColorDto? = null,
    val intensity: Float? = null
)
