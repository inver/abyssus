/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson

class RowTextTest : BasePlatformTestCase() {
    private fun entry(name: String, value: Any?, vararg keys: String) =
        DtoEntry("/p/$name", name, value, null, null, null, keys.toList())

    private fun json(text: String) = parseDocument(text)

    fun testFoldersAreLabelledAndCounted() {
        assertEquals(RowText("Scenes", "2"), rowText(entry("scenes", listOf(1, 2))))
        assertEquals(RowText("Assets", "0"), rowText(entry("assets", FolderRow(FolderKind.ASSETS, emptyList()))))
        assertEquals(RowText("UI", "0"), rowText(entry("ui", FolderRow(FolderKind.UI, emptyList()))))
        assertEquals(RowText("Skyboxes", "0"), rowText(entry("SKYBOX", TypeGroup("SKYBOX", emptyList()))))
    }

    fun testEcsShowsItsEntityCount() {
        assertEquals(RowText("ecs", "2 entities"), rowText(entry("ecs", json("""{"0":{},"1":{}}"""))))
        assertEquals("1 entity", rowText(entry("ecs", json("""{"0":{}}"""))).secondary)
        assertEquals("0 entities", rowText(entry("ecs", json("""{}"""))).secondary)
    }

    fun testEntityUsesItsNameAndComponentCount() {
        val e = entry("0", json("""{"components":{"NameComponent":{"name":"Model 0"}}}"""), "ecs")
        assertEquals(RowText("Model 0", "1 component"), rowText(e))
    }

    fun testUnnamedEntityFallsBackToItsId() {
        val e = entry("5", json("""{"components":{}}"""), "ecs")
        assertEquals(RowText("5", "0 components"), rowText(e))
        val blank = entry("6", json("""{"components":{"NameComponent":{"name":" "}}}"""), "ecs")
        assertEquals("6", rowText(blank).label)
    }

    fun testComponentsLoseTheComponentSuffix() {
        val c = entry("PositionComponent", json("""{}"""), "ecs", "0", "components")
        assertEquals(RowText("Position"), rowText(c))
    }

    fun testOtherRowsAreUnchanged() {
        assertEquals(RowText("fog"), rowText(entry("fog", json("""{"a":1}"""))))
        assertEquals(RowText("Custom"), rowText(entry("fog", json("""{}""")), "Custom"))
        // a nested `ecs`-named key is not the scene's ecs
        assertEquals(RowText("ecs"), rowText(entry("ecs", json("""{"0":{}}"""), "fog")))
        assertEquals(RowText("scenes"), rowText(entry("scenes", "x")))
    }

    fun testEcsRowsListTheEntities() {
        val rows = ecsRows(json("""{"0":{},"1":{}}"""))
        assertEquals(listOf("0", "1"), rows.map { it.name })
        assertEquals(emptyList<String>(), rows[0].via)
    }

    fun testEntityRowsAreItsComponentsOnly() {
        val rows = entityRows(json("""{"components":{"A":{},"B":{}}}"""))
        assertEquals(listOf("A", "B"), rows.map { it.name })
        assertTrue(rows.all { it.via == listOf("components") })
        assertTrue(entityRows(json("""{}""")).isEmpty())
    }
}
