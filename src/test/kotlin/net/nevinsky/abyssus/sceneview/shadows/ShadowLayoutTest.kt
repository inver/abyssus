/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview.shadows

import net.nevinsky.abyssus.sceneview.*
import org.junit.Assert.*
import org.junit.Test

class ShadowLayoutTest {
    private val color = Rgba(1f,1f,1f,1f)
    private fun lights(points: List<String>, spots: List<String> = emptyList(), dirs: List<String> = emptyList()) = LightSet(
        dirs.map { DirectionalSource(Vec3(0f,-1f,0f),color,it) },
        points.map { PointSource(Vec3(0f,0f,0f),color,10f,it) },
        spots.map { SpotSource(it,Vec3(0f,0f,0f),Vec3(0f,0f,-1f),color,10f,SpotCone(45f,0.2f)) })

    @Test fun budgetsAndUniqueTilesAreBounded() {
        val allocated = ShadowLayout().allocate(lights(listOf("p3","p1","p2"),listOf("s4","s3","s2","s1"),listOf("d2","d1")))
        assertEquals(setOf("d1","p1","p2","s1","s2","s3"), allocated.map { it.entityId }.toSet())
        val tiles=allocated.flatMap { it.tiles }
        assertEquals(16,tiles.size)
        assertEquals(16,tiles.map { it.index }.toSet().size)
        for(tile in tiles) {
            assertEquals(1024,tile.size)
            assertEquals(4096,tile.atlasSize)
            assertTrue(tile.x in 0..3072 && tile.y in 0..3072)
        }
    }

    @Test fun smallLightSetsUseAvailableAtlasResolution() {
        val layout = ShadowLayout()
        assertEquals(4096, layout.allocate(lights(emptyList(), dirs = listOf("sun"))).single().tiles.single().size)
        val pair = layout.allocate(lights(emptyList(), listOf("spot"), listOf("sun")))
        assertTrue(pair.flatMap { it.tiles }.all { it.size == 2048 })
        assertEquals(pair, layout.allocate(lights(emptyList(), listOf("spot"), listOf("sun"))))
    }

    @Test fun reorderRemovalAndReplacementPreserveSurvivingTiles() {
        val layout=ShadowLayout()
        val first=layout.allocate(lights(listOf("p1","p2"),listOf("s1","s2"),listOf("d1"))).associateBy { it.entityId }
        val reorder=layout.allocate(lights(listOf("p2","p1"),listOf("s2","s1"),listOf("d1"))).associateBy { it.entityId }
        assertEquals(first,reorder)
        val removal=layout.allocate(lights(listOf("p2"),listOf("s2","s3"),listOf("d1"))).associateBy { it.entityId }
        for(id in listOf("p2","s2","d1")) assertEquals(first[id],removal[id])
        assertFalse(removal.containsKey("p1"))
        assertEquals(emptyList<ShadowAllocation>(),layout.allocate(LightSet.NONE))
    }
}
