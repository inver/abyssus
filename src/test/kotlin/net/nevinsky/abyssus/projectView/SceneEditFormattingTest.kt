package net.nevinsky.abyssus.projectView

import com.google.gson.JsonParser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.dto.DtoValue
import net.nevinsky.abyssus.filetype.SceneJson

class SceneEditFormattingTest : BasePlatformTestCase() {
    private val compact = """{"id":0,"name":"Main","skyboxName":null,"fogEnabled":true,"fog":{"density":0.001}}"""

    private fun toggleFog(file: VirtualFile): Boolean {
        val entry = DtoEntry(
            path = "x/fog", name = "fog", value = DtoValue.Scalar(null),
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
        assertEquals(JsonParser.parseString(compact.replace("\"fogEnabled\":true", "\"fogEnabled\":false")), JsonParser.parseString(after))
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
}
