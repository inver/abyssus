package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings

class PanelStateTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    fun testProjectFixturesUseTheirOwnPhysicsSettings() {
        val settings = project.service<AbyssusProjectSettings>()
        val off = myFixture.copyFileToProject("Untitled/Untitled.abss")
        val on = myFixture.copyFileToProject("Physics/Physics.abss")
        assertFalse(readProjectState(off, off.name, settings).settings.physicsEnabled)
        assertTrue(readProjectState(on, on.name, settings).settings.physicsEnabled)
        val unsupported = myFixture.addFileToProject("bad.abss", """{"format":"abyssus","formatVersion":2,"physicsEnabled":true}""").virtualFile
        val state = readProjectState(unsupported, unsupported.name, settings)
        assertFalse(state.settings.supported)
        assertFalse(state.settings.physicsEnabled)
        assertTrue(state.settings.problems.isNotEmpty())
    }
}
