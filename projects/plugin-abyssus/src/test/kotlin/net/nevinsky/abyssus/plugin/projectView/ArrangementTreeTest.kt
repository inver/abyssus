/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The tree squashes the linked copies of a path arrangement into the arrangement's row. */
class ArrangementTreeTest : BasePlatformTestCase() {
    private val ecs = ObjectMapper().readTree(
        """
        {"entities": {
          "1": {"components": {"NameComponent": {"name": "Ground"}}},
          "2": {"components": {"NameComponent": {"name": "Railway"}, "TypeComponent": {"type": "GROUP"},
                "PathArrangementComponent": {"slots": [3, 4]}}},
          "3": {"components": {"NameComponent": {"name": "Path 2 #0"}, "ParentComponent": {"parentEntityId": 2}}},
          "4": {"components": {"NameComponent": {"name": "Path 2 #1"}, "ParentComponent": {"parentEntityId": 2}}},
          "5": {"components": {"NameComponent": {"name": "Detached"}}}
        }}
        """,
    )

    fun testCopiesAreNotListedUnderEcs() {
        assertEquals(listOf("1", "2", "5"), ecsRows(ecs).map { it.name })
    }

    fun testTheArrangementRowCountsItsCopies() {
        val rows = ecsRows(ecs)
        val entry = { id: String ->
            val row = rows.first { it.name == id }
            DtoEntry("/ecs/$id", id, row.value, null, null, null, listOf("ecs", "entities"))
        }
        assertEquals("Railway", rowText(entry("2")).label)
        assertEquals("3 components, 2 copies", rowText(entry("2")).secondary)
        assertEquals("1 component", rowText(entry("1")).secondary)
    }

    fun testASceneWithoutArrangementsListsEveryEntity() {
        val plain = ObjectMapper().readTree("""{"entities": {"1": {"components": {}}, "2": {"components": {}}}}""")
        assertEquals(listOf("1", "2"), ecsRows(plain).map { it.name })
    }
}
