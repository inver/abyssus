/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.dto

data class RenderableDto(
    val shaderKey: String?,
    val assetName: String,
)

data class RenderableWrapper(val renderable: RenderableDto)