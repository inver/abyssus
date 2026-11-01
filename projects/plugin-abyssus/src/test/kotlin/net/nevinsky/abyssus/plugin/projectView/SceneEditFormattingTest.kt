/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.parsedScene
import net.nevinsky.abyssus.plugin.prettyScene
import net.nevinsky.abyssus.plugin.printScene

/** Scene edits write the scene pretty-printed by `JsonProcessor` (the file's own layout and number text are not kept). */
class SceneEditFormattingTest : BasePlatformTestCase() {
    private val compact = """{"formatVersion":1,"id":"0","name":"Main","fogEnabled":true,"fog":{"density":0.001}}"""

    private fun toggleFog(file: VirtualFile): Boolean {
        val entry = DtoEntry(
            path = "x/fog", name = "fog", value = null,
            enabled = true, toggleName = "fogEnabled", source = file, parentKeys = emptyList(),
        )
        return toggleEnabled(project, entry)
    }

    private fun text(file: VirtualFile) = String(file.contentsToByteArray())

    fun testTogglingWritesAPrettyFileAndLosesNothing() {
        val file = myFixture.addFileToProject("p/Main.scene", compact).virtualFile
        assertTrue(toggleFog(file))
        val after = text(file)
        assertTrue(after, after.lines().size > 5)
        assertTrue(after.contains("\"fogEnabled\": false"))
        assertEquals(printScene(parsedScene(compact).copy(fogEnabled = false)), after)
    }

    fun testRenameKeepsPrettyFormatting() {
        val file = myFixture.addFileToProject("p/Rename.scene", prettyScene(compact)).virtualFile
        assertTrue(renameScene(project, file, "Forest"))
        val after = text(file)
        assertTrue(after, after.contains("\n  \"name\": \"Forest\""))
        assertEquals("Forest", parsedScene(after).name)
    }

    fun testTogglingTwiceRestoresThePrintedBytes() {
        val original = prettyScene("""{"formatVersion":1,"id":"0","name":"Ololo","fogEnabled":true,"fog":{"density":0.001,"gradient":1.5}}""")
        val file = myFixture.addFileToProject("p/Main Scene.scene", original).virtualFile
        val toggle = { enabled: Boolean ->
            toggleEnabled(project, DtoEntry("x/fog", "fog", null, enabled, "fogEnabled", file, emptyList()))
        }
        assertTrue(toggle(true))
        assertTrue(text(file).contains("\"fogEnabled\": false"))
        assertTrue(toggle(false))
        assertEquals(original, text(file))
    }

    fun testSkyboxEyeKeepsTheSkyboxName() {
        val source = prettyScene("""{"formatVersion":1,"skyboxEnabled":true,"skyboxName":"sky"}""")
        val file = myFixture.addFileToProject("p/Sky.scene", source).virtualFile
        val entry = DtoEntry("x/skyboxName", "skyboxName", "sky", true, "skyboxEnabled", file, emptyList())
        assertTrue(toggleEnabled(project, entry))
        val after = parsedScene(text(file))
        assertFalse(after.skyboxEnabled)
        assertEquals("sky", after.skyboxName)
    }

    fun testSettingASkyboxKeepsFormattingAndTheEnabledFlag() {
        val source = """{"formatVersion":1,"id":"0","name":"Main","skyboxEnabled":false,"fogEnabled":true,"fog":{"density":0.001}}"""
        val file = myFixture.addFileToProject("p/SetSky.scene", prettyScene(source)).virtualFile
        assertTrue(setSkybox(project, file, "skybox_default"))
        val after = text(file)
        assertTrue(after, after.lines().size > 5)
        assertTrue(after.contains("\"skyboxName\": \"skybox_default\""))
        assertEquals(printScene(parsedScene(source).copy(skyboxName = "skybox_default")), after)
    }

    fun testClearingTheSkyboxDropsTheName() {
        val file = myFixture.addFileToProject("p/Clear.scene", """{"formatVersion":1,"skyboxEnabled":true,"skyboxName":"skybox_default"}""").virtualFile
        assertTrue(setSkybox(project, file, null))
        val after = parsedScene(text(file))
        assertNull(after.skyboxName)
        assertTrue(after.skyboxEnabled)
    }

    fun testSettingTheSameSkyboxWritesNothing() {
        val source = prettyScene("""{"formatVersion":1,"skyboxName":"sky"}""")
        val file = myFixture.addFileToProject("p/Same.scene", source).virtualFile
        val stamp = file.modificationStamp
        assertFalse(setSkybox(project, file, "sky"))
        assertEquals(stamp, file.modificationStamp)
        val noKey = myFixture.addFileToProject("p/NoKey.scene", """{"formatVersion":1}""").virtualFile
        assertFalse("a missing key already means no skybox", setSkybox(project, noKey, null))
        assertEquals(source, text(file))
    }
}
