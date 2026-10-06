/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.document.RayDataEdit
import net.nevinsky.abyssus.editor.ray.RayMaterialIdentity
import net.nevinsky.abyssus.editor.ray.RayOpticalOverride
import net.nevinsky.abyssus.editor.ray.RayOpticalField
import net.nevinsky.abyssus.editor.ray.RayMaterialOverrides

import net.nevinsky.abyssus.editor.document.SceneJson
import org.junit.Assert.*
import org.junit.Test

class RayMaterialOverrideTest {
    private val codec=RayMaterialOverrides()
    private val eligible=listOf(RayMaterialIdentity("glass",true),RayMaterialIdentity("paint",false))
    private fun render(text:String="{}")=SceneJson().parseObject(text)
    @Test fun separateInstancesNeverChangeSharedData() {
        val a=render();val b=render()
        assertEquals(RayDataEdit.Changed,codec.edit(a,"glass",RayOpticalField.TRANSMISSION,null,"1",eligible))
        assertEquals(RayOpticalOverride(1.0,1.5),codec.read(a).values.getValue("glass"))
        assertTrue(codec.read(b).values.isEmpty())
        assertFalse(a["rayTracingMaterials"]["glass"].has("ior"))
    }
    @Test fun invalidOpticsAndIneligibleIdsWriteNothing() {
        val r=render()
        for(value in listOf("-0.1","1.1","NaN","Infinity","abc")) assertTrue(codec.edit(r,"glass",RayOpticalField.TRANSMISSION,null,value,eligible) is RayDataEdit.Rejected)
        for(value in listOf("0","3.1","NaN")) assertTrue(codec.edit(r,"glass",RayOpticalField.IOR,null,value,eligible) is RayDataEdit.Rejected)
        for(id in listOf("paint","absent","")) assertTrue(codec.edit(r,id,RayOpticalField.IOR,null,"1.2",eligible) is RayDataEdit.Rejected)
        assertTrue(codec.edit(r,"glass",RayOpticalField.IOR,null,"1.2",eligible+eligible.first()) is RayDataEdit.Rejected)
        assertEquals("{}",SceneJson().compact(r))
    }
    @Test fun defaultsPruneOnlyKnownEmptyMapsAndKeepNumberText() {
        val r=render("""{"rayTracingMaterials":{"glass":{"transmission":0.50,"future":2.500},"lost":{"ior":1.20}},"other":-0.00}""")
        assertEquals(RayDataEdit.Changed,codec.edit(r,"glass",RayOpticalField.TRANSMISSION,r["rayTracingMaterials"]["glass"]["transmission"],"0",eligible))
        assertEquals("""{"rayTracingMaterials":{"glass":{"future":2.500},"lost":{"ior":1.20}},"other":-0.00}""",SceneJson().compact(r))
        val empty=render("""{"rayTracingMaterials":{"glass":{"ior":2}}}""")
        assertEquals(RayDataEdit.Changed,codec.edit(empty,"glass",RayOpticalField.IOR,empty["rayTracingMaterials"]["glass"]["ior"],"1.5",eligible))
        assertEquals("{}",SceneJson().compact(empty))
    }
    @Test fun assetReplacementKeepsUnresolvedOverridesAndAmbiguousIdsAreReported() {
        val r=render("""{"rayTracingMaterials":{"lost":{"transmission":1},"glass":{"ior":1.2}}}""")
        val before=SceneJson().compact(r)
        assertEquals(setOf("lost"),codec.unresolved(r,eligible))
        assertEquals(setOf("lost","glass"),codec.unresolved(r,eligible+eligible.first()))
        assertEquals(before,SceneJson().compact(r))
    }
    @Test fun malformedPresentFieldsAndContainersReportErrors() {
        for(text in listOf("""{"rayTracingMaterials":null}""","""{"rayTracingMaterials":[]}""","""{"rayTracingMaterials":{"glass":null}}""","""{"rayTracingMaterials":{"glass":{"ior":null}}}""","""{"rayTracingMaterials":{"glass":{"transmission":true}}}""")) assertTrue(codec.read(render(text)).errors.isNotEmpty())
    }
    @Test fun equalStaleAndMalformedOtherFieldsArePreserved() {
        val r=render("""{"rayTracingMaterials":{"glass":{"transmission":0.50,"ior":null}}}""")
        val expected=r["rayTracingMaterials"]["glass"]["transmission"]
        val before=SceneJson().compact(r)
        assertEquals(RayDataEdit.Unchanged,codec.edit(r,"glass",RayOpticalField.TRANSMISSION,expected,"0.5",eligible))
        assertEquals(RayDataEdit.Conflict,codec.edit(r,"glass",RayOpticalField.TRANSMISSION,null,"1",eligible))
        assertEquals(before,SceneJson().compact(r))
        assertEquals(RayDataEdit.Changed,codec.edit(r,"glass",RayOpticalField.IOR,r["rayTracingMaterials"]["glass"]["ior"],"1.4",eligible))
        assertEquals("0.50",r["rayTracingMaterials"]["glass"]["transmission"].asText())
    }
}
