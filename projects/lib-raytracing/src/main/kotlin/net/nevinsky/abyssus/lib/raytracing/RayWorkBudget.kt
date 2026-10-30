/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

/** Shared native traversal bounds, counting intersection queries rather than conceptual rays. */
const val RAY_PRIMARY_BLEND_LAYERS = 16
const val RAY_PRIMARY_QUERY_LIMIT = RAY_PRIMARY_BLEND_LAYERS + RAY_CUTOUT_HOLE_DEPTH

/** One sampled continuation per vertex: reflection and transmission never produce an exponential ray tree. */
class RayWorkBudget {
    fun perCameraSample(scene: RaySceneSnapshot, reflections: Int, refractions: Int): Long {
        require(reflections in 0..16 && refractions in 0..16)
        val cutouts=scene.materials.any { it.alphaMode==RayAlphaMode.MASK }
        val blends=scene.materials.any { it.alphaMode==RayAlphaMode.BLEND }
        val primary=if(cutouts || blends) RAY_PRIMARY_QUERY_LIMIT.toLong() else 1L
        val shaded=if(blends) primary else 1L
        val retry=if(cutouts) RAY_CUTOUT_HOLE_DEPTH.toLong() else 1L
        val reflective=scene.materials.any { it.kind==RayMaterialKind.PBR }
        val transmissive=scene.materials.any { it.transmission>0f }
        val events=(if(reflective) reflections else 0)+(if(transmissive) refractions else 0)
        val shadows=scene.lights.count { it.castsShadow }.toLong()
        return Math.addExact(primary,Math.multiplyExact(shaded,
            Math.addExact(events*retry,(events+1L)*shadows*retry)))
    }
    fun frameQueries(width: Int,height: Int,samples: Int,cost: Long): Long {
        require(width>0 && height>0 && samples>0 && cost>0)
        return Math.multiplyExact(Math.multiplyExact(Math.multiplyExact(width.toLong(),height.toLong()),samples.toLong()),cost)
    }
}
