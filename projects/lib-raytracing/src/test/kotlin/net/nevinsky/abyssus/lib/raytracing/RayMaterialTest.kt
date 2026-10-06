/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test

class RayMaterialTest {
    private val reference = RayMaterialReference()
    @Test fun defaultDiffuseSpecularAndEmissionAreIndependent() {
        val material = RayMaterial(baseColor = RayColor(.4f,.2f,.1f), specular = RayColor(.1f,.1f,.1f), emissive = RayColor(.3f,0f,0f))
        val value = reference.direct(material, material.baseColor, RayVec3(0f,0f,1f), RayVec3(0f,0f,1f), RayVec3(0f,0f,1f), RayColor(2f,2f,2f))
        assertEquals(1f,value.r,1e-5f)
        assertEquals(.6f,value.g,1e-5f)
    }
    @Test fun pbrRoughMetalHasRasterGgxPeak() {
        val material = RayMaterial(kind=RayMaterialKind.PBR,baseColor=RayColor(.8f,.4f,.2f),metallic=1f,roughness=1f)
        val value = reference.direct(material,material.baseColor,RayVec3(0f,0f,1f),RayVec3(0f,0f,1f),RayVec3(0f,0f,1f),RayColor(1f,1f,1f))
        assertEquals(.2f,value.r,1e-5f)
        assertEquals(.1f,value.g,1e-5f)
    }
    @Test fun terrainUsesOrderedMixesAndSkipsAbsentLayers() {
        val textures=listOf(texture("splat",RayColor(.5f,.5f,1f,1f)),texture("red",RayColor(1f,0f,0f)),texture("green",RayColor(0f,1f,0f)))
        val material=RayMaterial(kind=RayMaterialKind.TERRAIN,terrain=RayTerrainMaterial(RayTextureBinding(0),listOf(null,RayTextureBinding(1),RayTextureBinding(2),null,null),1f))
        val value=reference.baseColor(material,textures,.5f,.5f,.5f,.5f)
        assertEquals(.45f,value.r,1e-5f); assertEquals(.7f,value.g,1e-5f); assertEquals(.2f,value.b,1e-5f)
    }
    @Test fun samplerMatchesUploadRowsAndRepeatAcrossSeam() {
        val pixels=floatArrayOf(1f,0f,0f,1f,0f,0f,1f,1f)
        val texture=RayTexture("two",2,1,pixels)
        pixels[0]=0f
        assertEquals(1f,reference.sample(texture,.25f,.5f).r,1e-6f)
        assertEquals(.5f,reference.sample(texture,0f,.5f).r,1e-6f)
        assertEquals(1f,reference.sample(texture,1.25f,.5f).r,1e-6f)
    }
    @Test fun skyLookupFollowsTheRasterEquirectConventionAndScalesByExposure() {
        // 4x2 texels: column 1 is red, column 2 is green, row 0 is up
        val rgba=FloatArray(32).also { for(x in 0..3) for(y in 0..1) { val p=(y*4+x)*4; it[p+3]=1f; if(x==1) it[p]=2f; if(x==2) it[p+1]=2f } }
        val sky=RayTexture("sky",4,2,rgba,RayWrap.REPEAT,RayWrap.CLAMP_TO_EDGE,RayFilter.NEAREST)
        val environment=RayEnvironment(texture=0,intensity=3f)
        // -Z is the centre column, +X is a quarter turn right of it, straight up is the top row
        assertEquals(0f,reference.skyColor(environment,listOf(sky),RayVec3(-.1f,0f,-1f)).g,1e-6f)
        assertEquals(6f,reference.skyColor(environment,listOf(sky),RayVec3(-.1f,.3f,-1f)).r,1e-5f)
        assertEquals(6f,reference.skyColor(environment,listOf(sky),RayVec3(.1f,.3f,-1f)).g,1e-5f)
        val turned=environment.copy(rotation=90f)
        assertNotEquals(reference.skyColor(environment,listOf(sky),RayVec3(-.1f,.3f,-1f)),reference.skyColor(turned,listOf(sky),RayVec3(-.1f,.3f,-1f)))
        assertEquals(RayColor(.05f,.1f,.2f),reference.skyColor(RayEnvironment(),emptyList(),RayVec3(0f,1f,0f)))
    }
    @Test fun fogIsTheRasterQuadraticRamp() {
        assertEquals(0f,reference.fogAmount(.5f,0f),1e-7f)
        assertEquals(1f-1f/Math.E.toFloat(),reference.fogAmount(.5f,2f),1e-5f) // 1 - 1/e at distance 1 / density
        assertEquals(1f,reference.fogAmount(.5f,50f),0f)
        assertEquals(0f,reference.fogAmount(0f,100f),0f)
    }
    @Test fun reflectionSamplingIsDeterministicAndStaysAboveTheSurface() {
        val normal=RayVec3(0f,1f,0f); val view=RayVec3(0f,1f,1f).unit()
        val mirror=RayVec3(0f,1f,-1f).unit()
        val smooth=reference.reflectionDirection(normal,view,.04f,3,5)
        assertEquals(1f,smooth.dot(mirror),1e-3f)
        assertEquals(smooth,reference.reflectionDirection(normal,view,.04f,3,5))
        for(x in 0 until 16) for(y in 0 until 16) assertTrue(reference.reflectionDirection(normal,view,1f,x,y).dot(normal)>0f)
        assertTrue("rough sampling varies per pixel",(0 until 16).flatMap { x -> (0 until 16).map { y -> reference.reflectionDirection(normal,view,1f,x,y) } }.toSet().size>32)
    }
    private fun texture(id:String,c:RayColor)=RayTexture(id,1,1,floatArrayOf(c.r,c.g,c.b,c.a))
}
