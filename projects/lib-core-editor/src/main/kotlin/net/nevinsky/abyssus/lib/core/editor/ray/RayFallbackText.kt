/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.ray

import net.nevinsky.abyssus.lib.gdx.editor.EditorMessages

/** Why the view went back to raster, as the user reads it. */
fun RaySceneFallback.message(messages: EditorMessages): String = messages.message(
    when (this) {
        RaySceneFallback.ASSET_FAILURE -> "rayFallbackAssetFailure"
        RaySceneFallback.RESOURCE_LIMIT -> "rayFallbackResourceLimit"
        RaySceneFallback.UNSUPPORTED_GEOMETRY -> "rayFallbackUnsupportedGeometry"
    }
)
