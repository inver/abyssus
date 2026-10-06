/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test

class RayWorkBudgetTest {
    private val budget=RayWorkBudget()
    private val identity=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)
    private fun scene(alpha: RayAlphaMode, lights: Int = 0): RaySceneSnapshot {
        val mesh=RayMesh("plane",floatArrayOf(-10f,-10f,0f,10f,-10f,0f,0f,10f,0f),intArrayOf(0,1,2))
        return RaySceneSnapshot(listOf(mesh),(0..20).map { i -> RayInstance("$i",0,0,identity.copyOf().also { it[14]=-i.toFloat() }) },
            listOf(RayMaterial(kind=RayMaterialKind.PBR,alphaMode=alpha,baseColor=RayColor(1f,1f,1f,.2f),opacity=.5f)),emptyList(),
            (0 until lights).map { RayLight("$it",RayLightKind.DIRECTIONAL,RayColor(1f,1f,1f),direction=RayVec3(0f,0f,1f)) })
    }
    @Test fun actualReferenceQueriesNeverExceedCameraSampleBound() {
        val camera=RaySliceCamera(listOf(0f,0f,2f),listOf(0f,0f,-1f),listOf(0f,1f,0f),60f,.1f,100f)
        for(alpha in RayAlphaMode.entries) for(lights in listOf(0,1,12)) {
            val scene=scene(alpha,lights)
            var queries=0L
            RaySceneReferenceRenderer { queries++ }.render(RaySceneRequest(RayFrameKey(1,1,1,1),1,1,camera,scene))
            assertTrue("$alpha/$lights: $queries",queries<=budget.perCameraSample(scene,1,0))
        }
    }
    @Test fun defaultAndNonreflectiveWorkDoesNotPayForUnusedContinuation() {
        val scene=scene(RayAlphaMode.OPAQUE,2)
        assertEquals(6L,budget.perCameraSample(scene,1,0))
        val plain=RaySceneSnapshot(scene.meshes,scene.instances,listOf(RayMaterial()),emptyList(),scene.lights)
        assertEquals(3L,budget.perCameraSample(plain,16,16))
        assertEquals(3L,budget.perCameraSample(scene,0,0))
    }
    @Test fun allLimitArithmeticFitsAndFrameMultiplicationNeverWraps() {
        val scene=scene(RayAlphaMode.MASK,12)
        val cost=budget.perCameraSample(scene,16,16)
        assertTrue(cost>budget.perCameraSample(scene,1,0))
        assertTrue(budget.frameQueries(4096,4096,8,cost)>67108864)
        assertThrows(ArithmeticException::class.java) { budget.frameQueries(Int.MAX_VALUE,Int.MAX_VALUE,8,cost) }
        assertThrows(IllegalArgumentException::class.java) { budget.perCameraSample(scene,17,0) }
    }
}
