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

package net.nevinsky.abyssus.projectView

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
        myFixture.addFileToProject("$root/P.abss", """{"name":"P"}""")
        val used = assets.filterValues { !it }.keys.joinToString(",") { """{"asset":{"assetName":"$it"}}""" }
        for (i in 1..scenes) {
            val ecs = if (i == 1 && used.isNotEmpty()) """{"ecs":{"entities":{"0":{"components":{"RenderComponent":{"renderable":[$used]}}}}}}""" else "{}"
            myFixture.addFileToProject("$root/scenes/S$i.scene", ecs)
        }
        for (name in assets.keys) myFixture.addFileToProject("$root/assets/$name/meta.json", """{"uuid":"u-$name","type":"MODEL","additional":{}}""")
    }

    fun testCountsOneProject() {
        projectWith("p", 2, mapOf("a" to false, "b" to false, "c" to true, "d" to true))
        assertEquals(FooterCounts(scenes = 2, assets = 4, unused = 2), footerCounts(project))
    }

    fun testCountsSumProjectsAndCountAStandaloneSceneAsOne() {
        projectWith("p", 2, mapOf("a" to true))
        projectWith("q", 1, mapOf("x" to true, "y" to true))
        myFixture.addFileToProject("loose.scene", "{}")
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
