/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.core

import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool

/**
 * Returns a list of [Renderable] instances to be rendered by a [com.badlogic.gdx.graphics.g3d.ModelBatch].
 *
 * @author badlogic
 */
interface RenderableProvider {
    /**
     * Returns [Renderable] instances. Renderables are obtained from the provided [Pool] and added to the
     * provided array. The Renderables obtained using [Pool.obtain] will later be put back into the pool, do not
     * store them internally. The resulting array can be rendered via a [ModelBatch].
     *
     * @param renderables the output array
     * @param pool        the pool to obtain Renderables from
     */
    fun getRenderables(renderables: Array<Renderable>, pool: Pool<Renderable>)
}
