/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.shadow

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import net.nevinsky.abyssus.lib.gdx.g3d.ModelDepthShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.ShadowDepthPass

/**
 * What a host's shadow map needs to draw casters' depth: the depth [pass] state, the depth shaders built from it, and
 * the [renderables] of the frame with the [pool] they come from. A host collects casters into [renderables] after
 * [begin], then [submit]s them seen from a light's camera into its own target. Needs a current GL context.
 */
class DepthCasters : Disposable {
    val pass = ShadowDepthPass()
    private val shaders = ModelDepthShaderProvider(pass)
    private val submission = DepthSubmission { shaders.get(it) }
    val pool = object : Pool<Renderable>() {
        override fun newObject() = Renderable()
    }
    val renderables = Array<Renderable>()

    /** Returns the last frame's renderables to the pool; collect this frame's casters next. */
    fun begin() {
        pool.freeAll(renderables)
        renderables.clear()
    }

    /** Draws [casters] (the collected [renderables] by default) seen from [camera]. */
    fun submit(camera: Camera, casters: Iterable<Renderable> = renderables) = submission.submit(camera, casters)

    /** Forgets the renderables and the pool without touching the shaders, for a context that is gone. */
    fun abandon() {
        renderables.clear()
        pool.clear()
    }

    override fun dispose() {
        shaders.dispose()
        abandon()
    }
}
