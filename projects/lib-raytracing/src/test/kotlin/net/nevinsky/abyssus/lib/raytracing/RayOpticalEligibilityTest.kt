/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test

class RayOpticalEligibilityTest {
    private val matrix=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)
    private val positions=floatArrayOf(0f,0f,0f,1f,0f,0f,0f,1f,0f,0f,0f,1f)
    private val closed=intArrayOf(0,2,1,0,1,3,0,3,2,1,2,3)
    private fun scene(indices:IntArray=closed,material:RayMaterial=RayMaterial(kind=RayMaterialKind.PBR,transmission=1f)) =
        RaySceneSnapshot(listOf(RayMesh("solid",positions,indices)),listOf(RayInstance("solid",0,0,matrix)),listOf(material),emptyList(),emptyList())
    @Test fun closedOutwardSolidIsEligibleButOpenNonmanifoldAndInwardSurfacesAreRejected() {
        assertNull(RayOpticalEligibility().unsupportedReason(scene()))
        assertTrue(RayOpticalEligibility().unsupportedReason(scene(closed.copyOf(9)))!!.contains("closed"))
        assertNotNull(RayOpticalEligibility().unsupportedReason(scene(closed+intArrayOf(0,2,1))))
        val inward=closed.toList().chunked(3).flatMap { listOf(it[0],it[2],it[1]) }.toIntArray()
        assertNotNull(RayOpticalEligibility().unsupportedReason(scene(inward)))
    }
    @Test fun onlyOpaquePbrTransmissionIsEligibleAndOrdinaryOpenGeometryIsUnchanged() {
        for(alpha in listOf(RayAlphaMode.MASK,RayAlphaMode.BLEND)) assertNotNull(RayOpticalEligibility().unsupportedReason(scene(material=RayMaterial(kind=RayMaterialKind.PBR,alphaMode=alpha,transmission=1f))))
        assertNotNull(RayOpticalEligibility().unsupportedReason(scene(material=RayMaterial(transmission=1f))))
        assertNull(RayOpticalEligibility().unsupportedReason(scene(closed.copyOf(3),RayMaterial())))
    }
    @Test fun materialPartsCanTogetherFormOneClosedSolid() {
        val meshes=listOf(RayMesh("a",positions,closed.copyOf(6)),RayMesh("b",positions,closed.copyOfRange(6,12)))
        val scene=RaySceneSnapshot(meshes,listOf(RayInstance("entity/a",0,0,matrix),RayInstance("entity/b",1,0,matrix)),
            listOf(RayMaterial(kind=RayMaterialKind.PBR,transmission=1f)),emptyList(),emptyList())
        assertNull(RayOpticalEligibility().unsupportedReason(scene))
    }
    @Test fun nativeQueryAndMediumErrorsRejectTheWholeFrame() {
        assertThrows(RayQualityLimitException::class.java) { requireNativeOpticalFrame(floatArrayOf(1f,1f,1f,-1f)) }
        assertThrows(UnsupportedOperationException::class.java) { requireNativeOpticalFrame(floatArrayOf(1f,1f,1f,1f,0f,0f,0f,-2f)) }
    }
}
