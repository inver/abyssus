/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing
import org.junit.Assert.*
import org.junit.Test
class RayLightTest {
    private val reference=RayMaterialReference()
    @Test fun rangeFalloffUsesInverseSquareAndSmoothLastQuarter() {
        val light=RayLight("point",RayLightKind.POINT,RayColor(1f,1f,1f),range=4f)
        assertEquals(.2f,reference.attenuation(light,RayVec3(0f,0f,2f)),1e-6f)
        assertEquals(0f,reference.attenuation(light,RayVec3(0f,0f,4f)),1e-6f)
        assertEquals(.5f/(1+3.5f*3.5f),reference.attenuation(light,RayVec3(0f,0f,3.5f)),1e-6f)
    }
    @Test fun spotConeFacesAwayFromLightAndHonorsHardEdge() {
        val light=RayLight("spot",RayLightKind.SPOT,RayColor(1f,1f,1f),direction=RayVec3(0f,0f,-1f),range=10f,cutoffAngle=30f,exponent=0f)
        assertEquals(.5f,reference.attenuation(light,RayVec3(0f,0f,-1f)),1e-6f)
        assertEquals(0f,reference.attenuation(light,RayVec3(0f,0f,1f)),1e-6f)
    }
}
