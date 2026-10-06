/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AbyssusFooterTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        UnusedFilter.set(project, false)
    }

    override fun tearDown() {
        try {
            UnusedFilter.set(project, false)
        } finally {
            super.tearDown()
        }
    }

    private fun projectWith(root: String, scenes: Int, assets: Map<String, Boolean>) {
        myFixture.addFileToProject("$root/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        val used = assets.filterValues { !it }.keys.joinToString(",") { """{"asset":{"assetName":"$it"}}""" }
        for (i in 1..scenes) {
            val ecs = if (i == 1 && used.isNotEmpty()) """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"0":{"components":{"RenderComponent":{"renderable":[$used]}}}}}}""" else """{"format":"abyssus","formatVersion":1}"""
            myFixture.addFileToProject("$root/scenes/S$i.scene", ecs)
        }
        for (name in assets.keys) myFixture.addFileToProject("$root/assets/$name/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"uuid":"${java.util.UUID.nameUUIDFromBytes(name.toByteArray())}","type":"MODEL","additional":{}}""")
    }

    fun testCountsOneProject() {
        projectWith("p", 2, mapOf("a" to false, "b" to false, "c" to true, "d" to true))
        assertEquals(FooterCounts(scenes = 2, assets = 4, unused = 2), footerCounts(project))
    }

    fun testCountsSumProjectsAndCountAStandaloneSceneAsOne() {
        projectWith("p", 2, mapOf("a" to true))
        projectWith("q", 1, mapOf("x" to true, "y" to true))
        myFixture.addFileToProject("loose.scene", """{"format":"abyssus","formatVersion":1}""")
        assertEquals(FooterCounts(scenes = 4, assets = 3, unused = 3), footerCounts(project))
    }

    fun testFilterDoesNotChangeTheFooter() {
        projectWith("p", 1, mapOf("a" to false, "b" to true))
        UnusedFilter.set(project, true)
        assertEquals(FooterCounts(1, 2, 1), footerCounts(project))
    }

    fun testFooterShowsTheCountsAsText() {
        val footer = AbyssusFooter(project, testRootDisposable)
        footer.show(FooterCounts(2, 1, 0))
        assertEquals(listOf("2 scenes", "1 asset", "0 unused"), footer.texts())
        footer.show(FooterCounts(0, 4, 2))
        assertEquals(listOf("0 scenes", "4 assets", "2 unused"), footer.texts())
    }

    fun testPaneWrapsItsComponentWithTheFooterAndKeepsOneWrapper() {
        val pane = AbyssusProjectViewPane(project)
        try {
            val component = pane.createComponent()
            assertSame(component, pane.createComponent())
            val parts = (component as java.awt.Container).components.toList()
            assertEquals(2, parts.size)
            assertTrue(parts.any { it is AbyssusFooter })
        } finally {
            Disposer.dispose(pane)
        }
    }
}
