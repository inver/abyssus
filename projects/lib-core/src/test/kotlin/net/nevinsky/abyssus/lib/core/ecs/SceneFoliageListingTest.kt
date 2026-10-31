/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import net.nevinsky.abyssus.lib.core.testing.loadEcs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneFoliageListingTest {
    private val warnings = ArrayList<String>()

    private fun terrain(id: Int, foliage: String?) = entity(id, "TERRAIN", "terrain_$id", foliage)

    private fun entity(id: Int, type: String, asset: String, foliage: String?) = """
        "$id": {"components": {
          "RenderComponent": {"type": "$type", "assetName": "$asset"}
          ${foliage?.let { ""","FoliageComponent": $it""" } ?: ""}
        }}"""

    private fun list(vararg entities: String): List<SceneFoliage> {
        val engine = SceneEngine()
        loadEcs("""{${entities.joinToString(",")}}""", engine)
        return SceneFoliageListing { warnings += it }.list(engine)
    }

    @Test
    fun listsTerrainEntitiesInIdOrder() {
        val listed = list(
            terrain(300, """{"assetName": "foliage_outer"}"""),
            terrain(1, """{"assetName": "foliage_meadow"}"""),
        )
        assertEquals(listOf(1L, 300L), listed.map { it.entityId })
        assertEquals(listOf("foliage_meadow", "foliage_outer"), listed.map { it.foliage })
        assertEquals(listOf("terrain_1", "terrain_300"), listed.map { it.terrain })
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun aModelOwnerIsOmittedWithOneWarningNamingIt() {
        val listing = list(entity(7, "MODEL", "tree", """{"assetName": "foliage_meadow"}"""))
        assertTrue(listing.isEmpty())
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), "entity 7" in warnings.single())
    }

    @Test
    fun missingNullOrEmptyNamesAreOmittedQuietly() {
        val listed = list(
            terrain(1, "{}"),
            terrain(2, """{"assetName": null}"""),
            terrain(3, """{"assetName": ""}"""),
            terrain(4, null),
        )
        assertTrue(listed.isEmpty())
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun anUnknownFolderNameIsStillListed() {
        assertEquals("nope", list(terrain(1, """{"assetName": "nope"}""")).single().foliage)
    }
}
