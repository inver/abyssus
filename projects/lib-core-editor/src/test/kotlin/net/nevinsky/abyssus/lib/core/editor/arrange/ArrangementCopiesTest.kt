/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrangementCopiesTest {
    private fun ecs(text: String): JsonNode = ObjectMapper().readTree(text)

    private val scene = """
        {"entities": {
          "1": {"components": {"NameComponent": {"name": "Ground"}}},
          "2": {"components": {"NameComponent": {"name": "Railway"}, "PathArrangementComponent": {"slots": [3, -1, 4, 9, 6]}}},
          "3": {"components": {"ParentComponent": {"parentEntityId": 2}}},
          "4": {"components": {"ParentComponent": {"parentEntityId": 2}}},
          "5": {"components": {"NameComponent": {"name": "Detached"}}},
          "6": {"components": {"ParentComponent": {"parentEntityId": 5}}}
        }}
    """

    @Test
    fun theCopiesAreTheEntitiesAnArrangementListsAndThatPointBackToIt() {
        // 9 does not exist, -1 is an empty slot and 6 points at another entity: none of them is a linked copy
        assertEquals(setOf("3", "4"), arrangementCopyIds(ecs(scene)))
    }

    @Test
    fun aSceneWithoutArrangementsHasNoCopies() {
        assertEquals(emptySet<String>(), arrangementCopyIds(ecs("""{"entities": {"1": {"components": {}}}}""")))
        assertEquals(emptySet<String>(), arrangementCopyIds(ecs("{}")))
    }

    @Test
    fun theFlatEntityLayoutIsReadToo() {
        val flat = ecs("""{"1": {"components": {"PathArrangementComponent": {"slots": [2]}}}, "2": {"components": {"ParentComponent": {"parentEntityId": 1}}}}""")
        assertEquals(setOf("2"), arrangementCopyIds(flat))
    }

    @Test
    fun theCountIsTheSlotsThatNameAnEntity() {
        val entities = ecs(scene)["entities"]
        assertEquals(4, arrangementCopyCount(entities["2"]))
        assertEquals(0, arrangementCopyCount(entities["1"]))
    }
}
