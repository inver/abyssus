package net.nevinsky.abyssus

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.projectView.AbyssusProjectViewPane

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
