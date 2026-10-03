/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.shadows

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import org.junit.Assert.*
import org.junit.Test

class ShadowProjectionTest {
    init { com.badlogic.gdx.utils.GdxNativesLoader.load() }
    private fun bounds(min:Vector3,max:Vector3)=BoundingBox(min,max)
    private fun inside(view:ShadowView, point:Vector3):Boolean {
        val clip=point.cpy().prj(view.combined)
        return clip.x in -1.001f..1.001f && clip.y in -1.001f..1.001f && clip.z in -1.001f..1.001f
    }
    @Test fun pointAxesAndSeamsUseSameFaceProjection() {
        val views=ShadowProjection().point(Vector3.Zero,20f)
        assertEquals(6,views.size)
        for(face in PointShadowFace.entries) {
            assertEquals(face,PointShadowFace.select(face.direction))
            assertTrue(inside(views[face.ordinal],face.direction.cpy().scl(5f)))
        }
        for(ray in listOf(Vector3(1f,1f,0f),Vector3(-1f,0f,1f),Vector3(0f,-1f,-1f),Vector3(1f,1f,1f))) {
            val face=PointShadowFace.select(ray)
            assertTrue(inside(views[face.ordinal],ray.cpy().nor().scl(5f)))
        }
        assertEquals(0.25f,PointShadowFace.radialDepth(5f,20f),0f)
        assertEquals(1f,PointShadowFace.radialDepth(25f,20f),0f)
    }
    @Test fun spotMatchesConeAndRange() {
        val view=ShadowProjection().spot(Vector3.Zero,Vector3(0f,0f,-1f),60f,20f)!!
        assertTrue(inside(view,Vector3(2f,0f,-5f)))
        assertFalse(inside(view,Vector3(4f,0f,-5f)))
        assertTrue(Vector3(0f,0f,-30f).prj(view.combined).z > 1f)
        assertNull(ShadowProjection().spot(Vector3.Zero,Vector3.Zero,60f,20f))
        assertNull(ShadowProjection().spot(Vector3.Zero,Vector3.Z,180f,20f))
    }
    @Test fun directionalIncludesUpstreamOffscreenCastersWithoutUnrelatedExpansion() {
        val receivers=bounds(Vector3(-2f,-1f,-2f),Vector3(2f,1f,2f))
        val caster=bounds(Vector3(-1f,8f,-1f),Vector3(1f,10f,1f))
        val unrelated=bounds(Vector3(100f,0f,100f),Vector3(110f,100f,110f))
        val projection=ShadowProjection()
        val fitted=projection.directional(Vector3(0f,-1f,0f),receivers,listOf(caster))!!
        assertTrue(inside(fitted,Vector3(0f,10f,0f)))
        assertTrue(inside(fitted,Vector3(2f,1f,2f)))
        val other=projection.directional(Vector3(0f,-1f,0f),receivers,listOf(caster,unrelated))!!
        assertArrayEquals(fitted.combined.`val`,other.combined.`val`,0f)
    }
    @Test fun directionalTexelSnapAndDegenerateBoundsStayFinite() {
        val projection=ShadowProjection()
        val first=projection.directional(Vector3(0f,0f,-1f),bounds(Vector3(-2f,-2f,-2f),Vector3(2f,2f,2f)),emptyList())!!
        val moved=projection.directional(Vector3(0f,0f,-1f),bounds(Vector3(-1.9999f,-2f,-2f),Vector3(2.0001f,2f,2f)),emptyList())!!
        assertArrayEquals(first.combined.`val`,moved.combined.`val`,0.000001f)
        val degenerate=projection.directional(Vector3(0f,-1f,0f),bounds(Vector3.Zero,Vector3.Zero),emptyList())!!
        assertTrue(degenerate.combined.`val`.all { it.isFinite() })
        assertNull(projection.directional(Vector3.Zero,bounds(Vector3.Zero,Vector3.Zero),emptyList()))
        val invalid = BoundingBox().also { it.min.set(1f,1f,1f); it.max.set(0f,0f,0f) }
        assertNull(projection.directional(Vector3.Y,invalid,emptyList()))
    }
}
