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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.dto.AssetInfo
import net.nevinsky.abyssus.dto.ProjectDto
import net.nevinsky.abyssus.dto.SceneReader
import net.nevinsky.abyssus.filetype.SceneJson

class SkyboxChoicesTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun faces(vararg files: String): String {
        val keys = listOf("top", "bottom", "left", "right", "front", "back")
        return """{"type":"SKYBOX","additional":{${files.mapIndexed { i, f -> "\"${keys[i]}\":\"$f\"" }.joinToString(",")}}}"""
    }

    private fun scene(sky: String?) = SceneReader.parse("""{"skyboxName":${sky?.let { "\"$it\"" } ?: "null"}}""")

    fun testOnlySkyboxAssetsInNameOrder() {
        val dto = ProjectDto("P", emptyList(), listOf(
            AssetInfo("nebula", "1", "SKYBOX"),
            AssetInfo("hdr", "2", "SKYBOX_HDR"),
            AssetInfo("reef", "3", "MODEL"),
            AssetInfo("dusk", "4", "SKYBOX"),
            AssetInfo("caustics", "5", "SHADER"),
        ))
        assertEquals(listOf("dusk", "nebula"), skyboxChoices(dto, emptyMap()).map { it.name })
    }

    fun testDetailLineCountsFacesAndSortsFormats() {
        val dto = ProjectDto("P", emptyList(), listOf(AssetInfo("sky", "1", "SKYBOX")))
        val meta = SceneJson.parse(faces("a.png", "b.PNG", "c.jpg", "d.png", "e.jpg", "f.png"))
        val choice = skyboxChoices(dto, mapOf("sky" to meta)).single()
        assertEquals(6, choice.faces)
        assertEquals(listOf("jpg", "png"), choice.formats)
        assertEquals("6 faces · jpg, png", choice.detail)
    }

    fun testMissingFacesAndMetaDoNotFail() {
        val dto = ProjectDto("P", emptyList(), listOf(AssetInfo("partial", "1", "SKYBOX"), AssetInfo("bare", "2", "SKYBOX")))
        val choices = skyboxChoices(dto, mapOf("partial" to SceneJson.parse(faces("a.png", "", "c.png")), "bare" to null))
        val partial = choices.first { it.name == "partial" }
        assertEquals(2, partial.faces)
        assertEquals("2 faces · png", partial.detail)
        val bare = choices.first { it.name == "bare" }
        assertEquals(0, bare.faces)
        assertEquals("0 faces", bare.detail)
    }

    fun testSceneCountAndUnusedFlag() {
        val dto = ProjectDto(
            "P",
            listOf(scene("nebula"), scene("nebula"), scene(null)),
            listOf(AssetInfo("nebula", "1", "SKYBOX"), AssetInfo("dusk", "2", "SKYBOX", unused = true)),
        )
        val byName = skyboxChoices(dto, emptyMap()).associateBy { it.name }
        assertEquals(2, byName.getValue("nebula").sceneCount)
        assertFalse(byName.getValue("nebula").unused)
        assertEquals(0, byName.getValue("dusk").sceneCount)
        assertTrue(byName.getValue("dusk").unused)
    }

    fun testChooserOnlyOnAProjectScenesOwnSkyboxRow() {
        val abss = myFixture.addFileToProject("p/P.abss", """{"name":"P"}""").virtualFile
        val inProject = myFixture.addFileToProject("p/scenes/A.scene", "{}").virtualFile
        val standalone = myFixture.addFileToProject("loose/B.scene", "{}").virtualFile
        fun entry(name: String, source: com.intellij.openapi.vfs.VirtualFile, keys: List<String> = emptyList()) =
            DtoEntry("x/$name", name, null, true, "skyboxEnabled", source, keys)
        assertEquals(abss, skyboxProjectOf(entry("skyboxName", inProject)))
        assertNull(skyboxProjectOf(entry("skyboxName", standalone)))
        assertNull(skyboxProjectOf(entry("fog", inProject)))
        assertNull("a nested skyboxName is not the scene's", skyboxProjectOf(entry("skyboxName", inProject, listOf("ecs"))))
    }

    fun testUntitledFixture() {
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        myFixture.copyFileToProject("Untitled/assets/skybox_default/meta.json", "Untitled/assets/skybox_default/meta.json")
        val abss = myFixture.findFileInTempDir("Untitled/Untitled.abss")
        val choice = loadSkyboxChoices(project, abss)!!.single()
        assertEquals("skybox_default", choice.name)
        assertEquals("6 faces · png", choice.detail)
        assertFalse(choice.unused)
        assertEquals(1, choice.sceneCount)
    }
}
