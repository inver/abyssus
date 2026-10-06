/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.projectView.AbyssusProjectViewPane

class AbyssusProjectViewPaneTest : BasePlatformTestCase() {
    /** ProjectViewImpl.addProjectPane logs a SEVERE error unless the pane's select-in target reports the pane's own id. */
    fun testSelectInTargetMatchesPaneId() {
        val pane = AbyssusProjectViewPane(project)
        try {
            val target = pane.createSelectInTarget()
            assertEquals(pane.id, target.minorViewId)
            assertFalse(target.javaClass.name, target.javaClass.name.endsWith("ProjectPaneSelectInTarget"))
        } finally {
            com.intellij.openapi.util.Disposer.dispose(pane)
        }
    }
}
