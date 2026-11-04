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
import java.util.IdentityHashMap

/** Finds the depth shader of a renderable; null when it casts nothing. A host passes its `ModelDepthShaderProvider`. */
fun interface DepthShaders {
    fun shaderFor(renderable: Renderable): Shader?
}

/**
 * Draws casters' depth through a host's depth [shaders] into whatever target the host has bound: one render context for
 * the whole submission, the casters grouped by their depth shader (in the order each shader first appears; within a
 * group the casters keep their order), and each shader begun and ended once per group (design decision D12 of
 * `improve-control-line-performance-stability`: unsorted, 423 casters made 849 program switches). The map does not
 * depend on the order, as the depth test keeps the nearest. The host owns the target, the camera, the depth shaders and
 * the pass state; this borrows [shaders] and disposes nothing it did not create. Needs a current GL context.
 */
class DepthSubmission(private val shaders: DepthShaders) {
    private val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))

    // the groups of one submission, kept for the next so a steady frame allocates nothing
    private val groups = IdentityHashMap<Shader, ArrayList<Renderable>>()
    private val order = ArrayList<Shader>()

    /** How many shader groups the last [submit] drew: each shader was begun once per group. */
    var lastGroups = 0
        private set

    /** Draws [casters] seen from [camera]; a caster without a depth shader is skipped. */
    fun submit(camera: Camera, casters: Iterable<Renderable>) {
        order.clear()
        for (renderable in casters) {
            val shader = shaders.shaderFor(renderable) ?: continue
            val group = groups.getOrPut(shader) { ArrayList() }
            if (group.isEmpty()) order += shader
            group += renderable
        }
        lastGroups = order.size
        context.begin()
        try {
            for (i in order.indices) {
                val shader = order[i]
                val group = groups.getValue(shader)
                shader.begin(camera, context)
                try {
                    for (j in group.indices) shader.render(group[j])
                } finally {
                    shader.end()
                }
            }
        } finally {
            context.end()
            for (i in order.indices) groups.getValue(order[i]).clear()
            // a shader that drew nothing this time may be gone: keep only the groups just used
            if (groups.size > order.size) groups.keys.retainAll(order.toSet())
        }
    }
}
