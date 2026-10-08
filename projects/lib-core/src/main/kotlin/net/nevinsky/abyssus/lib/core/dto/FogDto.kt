/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.dto

data class FogDto(
    val color: ColorDto? = null,
    val density: Float? = null,
    val gradient: Float? = null
)
