/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.Frustum
import java.util.IdentityHashMap
import kotlin.math.max

/**
 * Frustum culling for model instances. `ModelBatch` draws everything it is given, so a field of hundreds of buildings
 * and lamps costs the same whichever way the camera looks. A model's bounding sphere (its rest-pose box, once per
 * [Model]) is moved by the instance's transform and tested against a frustum; an instance wholly outside it is left
 * out. Not thread safe; GL thread only, like the renderer that owns it.
 *
 * The spheres follow the asset storage (design decision D6): [track] drops the sphere of a model the storage built
 * again, and [clear] drops all of them when the scene changes, so no sphere outlives its model.
 */
class ModelCulling {
    private class Sphere(val center: Vector3, val radius: Float)
    private class Tracked(val model: Model, val version: Long)

    private val spheres = IdentityHashMap<Model, Sphere>()
    private val tracked = HashMap<String, Tracked>()
    private val box = BoundingBox()
    private val center = Vector3()

    /** How many models have a sphere kept. */
    val cached: Int get() = spheres.size

    /**
     * [model] is the asset the storage built under [name] as its [version]th build. When that is another model or another
     * build than last time, the earlier model's sphere is dropped, so the next [visible] measures the new bounds.
     */
    fun track(name: String, version: Long, model: Model) {
        val known = tracked[name]
        if (known != null && known.model === model && known.version == version) return
        known?.let { spheres.remove(it.model) }
        spheres.remove(model)
        tracked[name] = Tracked(model, version)
    }

    /** Drops every sphere: the scene changed, and the models of the last one may be gone. */
    fun clear() {
        spheres.clear()
        tracked.clear()
    }

    /** Whether any part of [instance] may be inside [frustum]. Errs towards true: an empty model is kept. */
    fun visible(instance: ModelInstance, frustum: Frustum): Boolean {
        val sphere = spheres.getOrPut(instance.model) { sphereOf(instance.model) }
        if (sphere.radius < 0f) return true
        val transform = instance.transform
        center.set(sphere.center).mul(transform)
        val scale = max(transform.getScaleX(), max(transform.getScaleY(), transform.getScaleZ()))
        return frustum.sphereInFrustum(center, sphere.radius * scale)
    }

    private fun sphereOf(model: Model): Sphere {
        model.calculateBoundingBox(box.inf())
        if (!box.isValid) return Sphere(Vector3(), -1f)
        val middle = box.getCenter(Vector3())
        // a sliver of margin so a part on the edge of the box is not dropped by rounding
        return Sphere(middle, box.getDimensions(Vector3()).len() * 0.5f * 1.02f)
    }
}
