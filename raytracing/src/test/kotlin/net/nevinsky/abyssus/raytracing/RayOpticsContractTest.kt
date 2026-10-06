/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing
import org.junit.Assert.*
import org.junit.Test

class RayOpticsContractTest {
    private val scene=RaySceneSnapshot(emptyList(),emptyList(),listOf(RayMaterial(kind=RayMaterialKind.PBR,transmission=1f,ior=1.4f)),emptyList(),emptyList())
    private val camera=RaySliceCamera(listOf(0f,0f,2f),listOf(0f,0f,-1f),listOf(0f,1f,0f),60f,.1f,100f)
    @Test fun requestRenderPlanKeepsAllFrozenTransportLimits() {
        val original=RaySceneRequest(RayFrameKey(1,1,1,1),8,8,camera,scene,maxReflectionBounces=3,maxRefractionBounces=4,maxRaysPerFrame=67108864,settingsRevision=7)
        val planned=original.withRenderPlan(4,4,8,257,5)
        assertEquals(3,planned.maxReflectionBounces);assertEquals(4,planned.maxRefractionBounces)
        assertEquals(67108864L,planned.maxRaysPerFrame);assertEquals(7L,planned.settingsRevision)
        assertEquals(257,planned.sampleOffset);assertEquals(5L,planned.accumulationEpoch)
        assertEquals(28,planned.nativeCamera().size)
    }
    @Test fun nativeMaterialLayoutCarriesTransmissionAndIor() {
        val payload=RaySceneEncoding(scene).encode().floats
        val material=payload[0].toInt()
        assertEquals(1f,payload[material+21],0f);assertEquals(1.4f,payload[material+22],0f)
    }
    @Test fun requestsEnforceBudgetAndUnsupportedBackendOpticsExplicitly() {
        val request=RaySceneRequest(RayFrameKey(1,1,1,1),8,8,camera,scene,maxReflectionBounces=16,maxRefractionBounces=16,maxRaysPerFrame=1)
        assertThrows(RayQualityLimitException::class.java) { request.requireWithinBudget() }
        val capabilities=RayCapabilities(true,true,true,4096,128L*1024*1024)
        assertThrows(UnsupportedOperationException::class.java) { request.requireOptics(capabilities) }
        request.requireOptics(capabilities.copy(sceneOptics=true))
    }
    @Test fun materialOpticsRejectNonfiniteAndOutOfRangeInputs() {
        for(value in listOf(-1f,1.1f,Float.NaN)) assertThrows(IllegalArgumentException::class.java) { RayMaterial(transmission=value) }
        for(value in listOf(0f,3.1f,Float.POSITIVE_INFINITY)) assertThrows(IllegalArgumentException::class.java) { RayMaterial(ior=value) }
    }
}
