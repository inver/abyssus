/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.dto

import net.nevinsky.abyssus.lib.core.assets.MetaType

const val ASSET_RENDERABLE_KIND = "asset"

data class RenderableDto(
    val kind: String = ASSET_RENDERABLE_KIND,
    val shaderKey: String?,
    val assetType: MetaType,
    val assetName: String?,
)

data class RenderableWrapper(val renderable: RenderableDto)