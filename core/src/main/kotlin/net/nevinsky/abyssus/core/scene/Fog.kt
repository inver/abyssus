/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.scene

data class Fog(
    val color: Color? = null,
    val density: Float? = null,
    val gradient: Float? = null
)
