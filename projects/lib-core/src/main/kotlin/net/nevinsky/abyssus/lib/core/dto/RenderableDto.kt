/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.dto

import net.nevinsky.abyssus.lib.gdx.assets.MetaType

/** A render component's `renderable` block: `{"kind": "asset", "shaderKey": ..., "asset": {"type", "assetName"}}`. */
data class RenderableDto(
    val shaderKey: String? = null,
    val type: MetaType? = null,
    val assetName: String = "",
)