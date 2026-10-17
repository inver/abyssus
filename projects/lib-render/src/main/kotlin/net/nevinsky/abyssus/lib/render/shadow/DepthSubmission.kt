/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.shadow

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext

/** Finds the depth shader of a renderable; null when it casts nothing. A host passes its `ModelDepthShaderProvider`. */
fun interface DepthShaders {
    fun shaderFor(renderable: Renderable): Shader?
}

/**
 * Draws casters' depth through a host's depth [shaders] into whatever target the host has bound: one render context for
 * the whole submission, then each caster's shader begun, rendered and ended. The host owns the target, the camera,
 * the depth shaders and the pass state; this borrows [shaders] and disposes nothing it did not create.
 * Needs a current GL context.
 */
class DepthSubmission(private val shaders: DepthShaders) {
    private val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))

    /** Draws [casters] seen from [camera]; a caster without a depth shader is skipped. */
    fun submit(camera: Camera, casters: Iterable<Renderable>) {
        context.begin()
        try {
            for (renderable in casters) {
                val shader = shaders.shaderFor(renderable) ?: continue
                shader.begin(camera, context)
                try {
                    shader.render(renderable)
                } finally {
                    shader.end()
                }
            }
        } finally {
            context.end()
        }
    }
}
