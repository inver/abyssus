/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.dto

import net.nevinsky.abyssus.lib.core.assets.MetaType

/** A render component's `renderable` block: `{"kind": "asset", "shaderKey": ..., "asset": {"type", "assetName"}}`. */
data class RenderableDto(
    val shaderKey: String? = null,
    val type: MetaType? = null,
    val assetName: String = "",
)