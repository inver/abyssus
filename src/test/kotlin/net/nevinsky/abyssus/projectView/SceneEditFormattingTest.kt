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

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.filetype.SceneJson

class SceneEditFormattingTest : BasePlatformTestCase() {
    private val compact = """{"id":0,"name":"Main","skyboxName":null,"fogEnabled":true,"fog":{"density":0.001}}"""

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
        val original = """{"id":0,"name":"Ololo","fogEnabled":true,"fog":{"density":0.001,"gradient":1.5},"skyboxName":null,"ecs":{"entities":{"0":{"x":-3.035308,"far":100,"fieldOfView":67}},"metadata":{"version":1}}}"""
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

    fun testNumberTextSurvivesAnEdit() {
        val source = """{"fogEnabled":true,"fog":{"density":1.0E-4,"gradient":2.50,"offset":-0.0,"big":12345678901234567890}}"""
        val file = myFixture.addFileToProject("p/Numbers.scene", source).virtualFile
        assertTrue(toggleFog(file))
        assertEquals(source.replace("\"fogEnabled\":true", "\"fogEnabled\":false"), text(file))
    }
}
