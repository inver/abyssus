/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class RayOpticsTest {
    private val optics=RayOptics()
    private val normal=RayVec3(0f,0f,1f)
    @Test fun iorOneNeverBendsOrReflects() {
        val incident=RayVec3(.6f,0f,-.8f)
        assertEquals(incident,optics.refract(incident,normal,1f,1f))
        assertEquals(0f,optics.fresnel(.8f,1f,1f),0f)
    }
    @Test fun airGlassEntryAndExitObeySnellAndRestoreParallelDirection() {
        val incident=RayVec3(.6f,0f,-.8f)
        val entered=optics.refract(incident,normal,1f,1.5f)!!
        assertEquals(.4f,entered.x,1e-6f)
        assertEquals(-sqrt(1f-.16f),entered.z,1e-6f)
        val exited=optics.refract(entered,normal,1.5f,1f)!!
        assertEquals(incident.x,exited.x,1e-6f);assertEquals(incident.z,exited.z,1e-6f)
        assertEquals(.04f,optics.fresnel(1f,1f,1.5f),1e-6f)
        assertTrue(optics.fresnel(.1f,1f,1.5f)>.04f)
    }
    @Test fun criticalAngleGivesTotalInternalReflectionWithoutNaNs() {
        assertNull(optics.refract(RayVec3(.9f,0f,-sqrt(1f-.81f)),normal,1.5f,1f))
        assertEquals(1f,optics.fresnel(sqrt(1f-.81f),1.5f,1f),0f)
        assertNotNull(optics.refract(RayVec3(.6f,0f,-.8f),normal,1.5f,1f))
    }
    @Test fun separateCountersEnforceMixedPathsAndInternalReflectionLimits() {
        val path=RayOpticalPath(2,2)
        assertTrue(path.consume(RayOpticalEvent.TRANSMISSION))
        assertTrue(path.consume(RayOpticalEvent.REFLECTION))
        assertTrue(path.consume(RayOpticalEvent.TRANSMISSION))
        assertFalse(path.consume(RayOpticalEvent.TRANSMISSION))
        assertTrue(path.consume(RayOpticalEvent.REFLECTION))
        assertFalse(path.consume(RayOpticalEvent.REFLECTION))
        assertFalse(RayOpticalPath(0,0).consume(RayOpticalEvent.REFLECTION))
    }
    @Test fun offsetsAreOnTheContinuationSideAndIndependentSeedsAreRepeatable() {
        val p=RayVec3(0f,0f,0f)
        assertTrue(optics.offset(p,normal,RayVec3(0f,0f,-1f)).z<0)
        assertTrue(optics.offset(p,normal,RayVec3(0f,0f,1f)).z>0)
        val seeds=(0 until 256).map { optics.sample(10,20,it,3) }
        assertEquals(seeds,(0 until 256).map { optics.sample(10,20,it,3) })
        assertTrue(seeds.all { it>=0 && it<1 });assertTrue(seeds.toSet().size>240)
    }
    @Test fun startingInsideInfersGlassAndNestedMediaAreRejected() {
        val medium=RayOpticalMedium()
        assertEquals(1.5f,medium.incidentIor(7,1.5f,entering=false),0f)
        assertTrue("A reflected first backface must retain the inferred glass medium",medium.inside)
        medium.cross(7,1.5f,entering=false)
        assertFalse(medium.inside)
        medium.cross(7,1.5f,entering=true)
        assertTrue(medium.inside)
        assertEquals(1.5f,medium.incidentIor(7,1.5f,entering=false),0f)
        assertThrows(IllegalArgumentException::class.java) { medium.incidentIor(8,1.2f,entering=true) }
        medium.cross(7,1.5f,entering=false);assertFalse(medium.inside)
    }
}
