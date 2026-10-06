/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.editor.document.SceneJson

class SceneEditFormattingTest : BasePlatformTestCase() {
    private val compact = """{"format":"abyssus","formatVersion":1,"id":0,"name":"Main","skyboxName":null,"fogEnabled":true,"fog":{"density":0.001}}"""

    private fun toggleFog(file: VirtualFile): Boolean {
        val entry = DtoEntry(
            path = "x/fog", name = "fog", value = null,
            enabled = true, toggleName = "fogEnabled", source = file, parentKeys = emptyList(),
        )
        return toggleEnabled(project, entry)
    }

    private fun text(file: VirtualFile) = String(file.contentsToByteArray())

    fun testPrettyFileStaysPrettyAndLosesNothingWhenToggled() {
        val pretty = SceneJson.pretty(compact)!!
        val file = myFixture.addFileToProject("p/Main.scene", pretty).virtualFile
        assertTrue(toggleFog(file))
        val after = text(file)
        assertTrue(after, after.lines().size > 5)
        assertTrue(after.contains("\"fogEnabled\": false"))
        assertEquals(SceneJson.parse(compact.replace("\"fogEnabled\":true", "\"fogEnabled\":false")), SceneJson.parse(after))
        assertTrue("null member kept", after.contains("\"skyboxName\": null"))
    }

    fun testCompactFileStaysCompact() {
        val file = myFixture.addFileToProject("p/Compact.scene", compact).virtualFile
        assertTrue(toggleFog(file))
        assertEquals(compact.replace("\"fogEnabled\":true", "\"fogEnabled\":false"), text(file))
    }

    fun testRenameKeepsPrettyFormatting() {
        val file = myFixture.addFileToProject("p/Rename.scene", SceneJson.pretty(compact)!!).virtualFile
        assertTrue(renameScene(project, file, "Forest"))
        val after = text(file)
        assertTrue(after, after.contains("\n  \"name\": \"Forest\""))
        assertTrue(after.contains("\"skyboxName\": null"))
    }

    fun testTogglingTwiceRestoresTheOriginalBytes() {
        val original = """{"format":"abyssus","formatVersion":1,"id":0,"name":"Ololo","fogEnabled":true,"fog":{"density":0.001,"gradient":1.5},"skyboxName":null,"ecs":{"entities":{"0":{"x":-3.035308,"far":100,"fieldOfView":67}},"metadata":{"version":1}}}"""
        val file = myFixture.addFileToProject("p/Main Scene.scene", original).virtualFile
        val toggle = { enabled: Boolean ->
            toggleEnabled(
                project,
                DtoEntry("x/fog", "fog", null, enabled, "fogEnabled", file, emptyList()),
            )
        }
        assertTrue(toggle(true))
        assertTrue(text(file).contains("\"fogEnabled\":false"))
        assertTrue(toggle(false))
        assertEquals(original, text(file))
    }

    fun testSkyboxEyeKeepsTheSkyboxNameKey() {
        val source = """{"format":"abyssus","formatVersion":1,"skyboxEnabled":true,"skyboxName":"sky"}"""
        val file = myFixture.addFileToProject("p/Sky.scene", source).virtualFile
        val entry = DtoEntry("x/skyboxName", "skyboxName", "sky", true, "skyboxEnabled", file, emptyList())
        assertTrue(toggleEnabled(project, entry))
        assertEquals("""{"format":"abyssus","formatVersion":1,"skyboxEnabled":false,"skyboxName":"sky"}""", text(file))
        assertNull(SceneJson.parse(text(file)).get("skybox"))
    }

    fun testSettingASkyboxKeepsFormattingAndTheEnabledFlag() {
        val source = """{"format":"abyssus","formatVersion":1,"id":0,"name":"Main","skyboxEnabled":false,"skyboxName":null,"fogEnabled":true,"fog":{"density":0.001}}"""
        val file = myFixture.addFileToProject("p/SetSky.scene", SceneJson.pretty(source)!!).virtualFile
        assertTrue(setSkybox(project, file, "skybox_default"))
        val after = text(file)
        assertTrue(after, after.lines().size > 5)
        assertTrue(after.contains("\"skyboxName\": \"skybox_default\""))
        assertEquals(SceneJson.parse(source.replace("\"skyboxName\":null", "\"skyboxName\":\"skybox_default\"")), SceneJson.parse(after))
    }

    fun testClearingTheSkyboxWritesNull() {
        val file = myFixture.addFileToProject("p/Clear.scene", """{"format":"abyssus","formatVersion":1,"skyboxEnabled":true,"skyboxName":"skybox_default"}""").virtualFile
        assertTrue(setSkybox(project, file, null))
        assertEquals("""{"format":"abyssus","formatVersion":1,"skyboxEnabled":true,"skyboxName":null}""", text(file))
    }

    fun testSettingTheSameSkyboxWritesNothing() {
        val source = """{"format":"abyssus","formatVersion":1,"skyboxName":"sky"}"""
        val file = myFixture.addFileToProject("p/Same.scene", source).virtualFile
        val stamp = file.modificationStamp
        assertFalse(setSkybox(project, file, "sky"))
        assertEquals(stamp, file.modificationStamp)
        val noKey = myFixture.addFileToProject("p/NoKey.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile
        assertFalse("a missing key already means no skybox", setSkybox(project, noKey, null))
        assertEquals(source, text(file))
    }

    fun testNumberTextSurvivesAnEdit() {
        val source = """{"format":"abyssus","formatVersion":1,"fogEnabled":true,"fog":{"density":1.0E-4,"gradient":2.50,"offset":-0.0,"big":12345678901234567890}}"""
        val file = myFixture.addFileToProject("p/Numbers.scene", source).virtualFile
        assertTrue(toggleFog(file))
        assertEquals(source.replace("\"fogEnabled\":true", "\"fogEnabled\":false"), text(file))
    }
}
