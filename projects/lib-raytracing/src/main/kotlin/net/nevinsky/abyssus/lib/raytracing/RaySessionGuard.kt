/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

// The session protocol shell the Metal and Vulkan backends share: who may call, and what a geometry or frame may cost.
// Each backend keeps only its transport calls; the limits are passed in.

/** The largest vertex plus index data a session accepts in one geometry upload. */
internal const val MAX_GEOMETRY_BYTES = 32L * 1024 * 1024

/** Session calls stay on the worker that created the session, and only while it is open. */
internal fun checkSessionOwner(backend: String, worker: Thread, open: Boolean) {
    check(Thread.currentThread() === worker) { "$backend session must stay on its owner worker" }
    check(open) { "$backend session is closed" }
}

/** The validated vertex and index arrays of a geometry upload, one entry per mesh. */
internal class GeometryData(val vertices: List<FloatArray>, val indices: List<IntArray>)

/** Checks the mesh count against [limits] and the data size against [MAX_GEOMETRY_BYTES]. */
internal fun checkedGeometry(meshes: List<RaySliceMesh>, limits: RayLimits): GeometryData {
    require(meshes.isNotEmpty() && meshes.size <= limits.maxInstances)
    val vertices = meshes.map { it.vertices() }
    val indices = meshes.map { it.indices() }
    require(vertices.sumOf { it.size.toLong() * 4 } + indices.sumOf { it.size.toLong() * 4 } <= MAX_GEOMETRY_BYTES)
    return GeometryData(vertices, indices)
}

/** Checks a frame size against [limits]. */
internal fun requireFrameWithin(width: Int, height: Int, limits: RayLimits) =
    require(width in 1..limits.maxDimension && height in 1..limits.maxDimension && width.toLong() * height <= limits.maxPixels)
