/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test

class RayFrameAccumulatorTest {
    private val camera=RaySliceCamera(listOf(0f,0f,1f),listOf(0f,0f,-1f),listOf(0f,1f,0f),60f,.1f,100f)
    private val scene=RaySceneSnapshot(emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
    private fun request(samples:Int,offset:Int,epoch:Long=1,revision:Long=1)=RaySceneRequest(RayFrameKey(1,1,1,1),1,1,camera,scene,samples,offset,epoch,settingsRevision=revision)
    private fun frame(value:Float)=RayFrame(RayFrameKey(1,1,1,1),1,1,floatArrayOf(value,value,value,1f),floatArrayOf(.5f))
    @Test fun batchesUseSampleWeightedMeansAndResetOnRevisionOrEpoch() {
        val accumulator=RayFrameAccumulator()
        accumulator.add(request(8,0),frame(1f))
        assertEquals(.8f,accumulator.add(request(2,8),frame(0f)).colorValues()[0],1e-6f)
        assertEquals(.2f,accumulator.add(request(1,0,revision=2),frame(.2f)).colorValues()[0],0f)
        assertEquals(.4f,accumulator.add(request(1,0,epoch=2),frame(.4f)).colorValues()[0],0f)
    }
    @Test fun missingAndIncompatibleHistoryNeverContributes() {
        val accumulator=RayFrameAccumulator()
        accumulator.add(request(8,0),frame(1f))
        assertEquals(0f,accumulator.add(request(2,9),frame(0f)).colorValues()[0],0f)
        accumulator.clear()
        assertEquals(.5f,accumulator.add(request(1,10),frame(.5f)).colorValues()[0],0f)
    }
}
