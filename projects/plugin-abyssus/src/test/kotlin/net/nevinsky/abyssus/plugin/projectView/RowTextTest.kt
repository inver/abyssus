/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.editor.document.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.editor.parseScene

class RowTextTest : BasePlatformTestCase() {
    private fun entry(name: String, value: Any?, vararg keys: String) =
        DtoEntry("/p/$name", name, value, null, null, null, keys.toList())

    private fun scene(ecs: String) = parseScene("""{"formatVersion":1,"ecs":$ecs}""")

    fun testFoldersAreLabelledAndCounted() {
        assertEquals(RowText("Scenes", "2"), rowText(entry("scenes", listOf(1, 2))))
        assertEquals(RowText("Assets", "0"), rowText(entry("assets", FolderRow(FolderKind.ASSETS, emptyList()))))
        assertEquals(RowText("UI", "0"), rowText(entry("ui", FolderRow(FolderKind.UI, emptyList()))))
        assertEquals(RowText("Skyboxes", "0"), rowText(entry("SKYBOX", TypeGroup("SKYBOX", emptyList()))))
    }

    fun testEcsShowsItsEntityCount() {
        assertEquals(RowText("ecs", "2 entities"), rowText(entry("ecs", EcsNode(scene("""{"0":{"components":{}},"1":{"components":{}}}""")))))
        assertEquals("1 entity", rowText(entry("ecs", EcsNode(scene("""{"0":{"components":{}}}""")))).secondary)
        assertEquals("0 entities", rowText(entry("ecs", EcsNode(scene("""{}""")))).secondary)
    }

    fun testEntityUsesItsNameAndComponentCount() {
        val e = entry("0", EntityNode(scene("""{"0":{"components":{"NameComponent":{"name":"Model 0"}}}}"""), 0), "ecs")
        assertEquals(RowText("Model 0", "1 component"), rowText(e))
    }

    fun testUnnamedEntityFallsBackToItsId() {
        val e = entry("5", EntityNode(scene("""{"5":{"components":{}}}"""), 5), "ecs")
        assertEquals(RowText("5", "0 components"), rowText(e))
        val blank = entry("6", EntityNode(scene("""{"6":{"components":{"NameComponent":{"name":" "}}}}"""), 6), "ecs")
        assertEquals("6", rowText(blank).label)
    }

    fun testComponentsLoseTheComponentSuffix() {
        val c = entry("PositionComponent", ComponentNode(scene("""{"0":{"components":{"PositionComponent":{}}}}"""), 0, "PositionComponent"), "ecs", "0", "components")
        assertEquals(RowText("Position"), rowText(c))
    }

    fun testOtherRowsAreUnchanged() {
        assertEquals(RowText("fog"), rowText(entry("fog", mapOf("a" to 1))))
        assertEquals(RowText("Custom"), rowText(entry("fog", emptyMap<String, Any>()), "Custom"))
        // a nested `ecs`-named key is not the scene's ecs
        assertEquals(RowText("ecs"), rowText(entry("ecs", mapOf("0" to emptyMap<String, Any>()), "fog")))
        assertEquals(RowText("scenes"), rowText(entry("scenes", "x")))
    }

    fun testEcsRowsListTheEntities() {
        val rows = ecsRows(scene("""{"0":{"components":{}},"1":{"components":{}}}"""))
        assertEquals(listOf("0", "1"), rows.map { it.name })
        assertEquals(emptyList<String>(), rows[0].via)
    }

    fun testEntityRowsAreItsComponentsOnly() {
        val rows = entityRows(EntityNode(scene("""{"0":{"components":{"A":{},"B":{}}}}"""), 0))
        assertEquals(listOf("A", "B"), rows.map { it.name })
        assertTrue(entityRows(EntityNode(scene("""{"0":{"components":{}}}"""), 0)).isEmpty())
    }
}
