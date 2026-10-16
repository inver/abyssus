/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.RenderableProvider
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool

/**
 * A [RenderableProvider] that hands on only the renderables of [source] that [accept] takes, so a pass can skip some of
 * a stock `ModelBatch`'s input (a water pass leaves out the water itself). A refused renderable stays with the pool,
 * which takes it back when the batch flushes.
 */
class FilteredRenderables(
    private val source: RenderableProvider,
    private val accept: (Renderable) -> Boolean
) : RenderableProvider {
    private val offered = Array<Renderable>()

    override fun getRenderables(renderables: Array<Renderable>, pool: Pool<Renderable>) {
        offered.clear()
        source.getRenderables(offered, pool)
        for (renderable in offered) {
            if (accept(renderable)) {
                renderables.add(renderable)
            }
        }
        offered.clear()
    }
}
