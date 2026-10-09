/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.plugin.ui.thumbnail

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.gdx.editor.testAsset
import net.nevinsky.abyssus.plugin.dto.ProjectDto
import net.nevinsky.abyssus.plugin.dto.SceneEntry
import net.nevinsky.abyssus.lib.gdx.editor.parseScene
import net.nevinsky.abyssus.lib.gdx.editor.document.SceneJson
import net.nevinsky.abyssus.plugin.testMetaFiles
import net.nevinsky.abyssus.plugin.testCore

class SkyboxChoicesTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun faces(vararg files: String): String {
        val keys = listOf("top", "bottom", "left", "right", "front", "back")
        return """{"format":"abyssus","formatVersion":1,"type":"SKYBOX","additional":{${files.mapIndexed { i, f -> "\"${keys[i]}\":\"$f\"" }.joinToString(",")}}}"""
    }

    private fun scene(sky: String?) = parseScene("""{"format":"abyssus","formatVersion":1,"skyboxName":${sky?.let { "\"$it\"" } ?: "null"}}""")

    fun testOnlySkyboxAssetsInNameOrder() {
        val dto = ProjectDto("P", emptyList(), listOf(
            testAsset("nebula", "1", "SKYBOX"),
            testAsset("hdr", "2", "SKYBOX_HDR"),
            testAsset("reef", "3", "MODEL"),
            testAsset("dusk", "4", "SKYBOX"),
            testAsset("caustics", "5", "SHADER"),
        ))
        assertEquals(listOf("dusk", "hdr", "nebula"), skyboxChoices(dto, emptyMap()).map { it.name })
    }

    fun testListsAllThreeSkyboxKinds() {
        val dto = ProjectDto("P", emptyList(), listOf(
            testAsset("skybox_physical", "1", "SKYBOX_PROCEDURAL"),
            testAsset("skybox_default", "2", "SKYBOX"),
            testAsset("hdr", "3", "SKYBOX_HDR"),
        ))
        assertEquals(listOf("hdr", "skybox_default", "skybox_physical"), skyboxChoices(dto, emptyMap()).map { it.name })
    }

    fun testProceduralDetailLine() {
        val dto = ProjectDto("P", emptyList(), listOf(testAsset("skybox_physical", "1", "SKYBOX_PROCEDURAL")))
        val choice = skyboxChoices(dto, emptyMap()).single()
        assertTrue(choice.procedural)
        assertEquals("procedural sky", choice.detail)
    }

    fun testProceduralDetailLineWithClouds() {
        val dto = ProjectDto("P", emptyList(), listOf(testAsset("skybox_physical", "1", "SKYBOX_PROCEDURAL")))
        fun detail(clouds: String?): String {
            val meta = """{"format":"abyssus","formatVersion":1,"type":"SKYBOX_PROCEDURAL","additional":{"vertex":"sky.vert"${clouds?.let { ",\"clouds\":$it" } ?: ""}}}"""
            return skyboxChoices(dto, mapOf("skybox_physical" to SceneJson().parse(meta))).single().detail
        }
        assertEquals("procedural sky \u00b7 clouds", detail("\"3f2a9c1e-7b4d-4e8a-9c6f-1d2e3b4a5c6d\""))
        assertEquals("procedural sky", detail("\"\""))
        assertEquals("procedural sky", detail("""{"enabled":true}"""))
        assertEquals("procedural sky", detail(null))
    }

    fun testFixtureProjectOffersAllThreeSkies() {
        val abss = myFixture.copyFileToProject("Tree/Untitled.abss", "Untitled/Untitled.abss")
        myFixture.copyFileToProject("Tree/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        java.io.File("$testDataPath/Tree/assets").listFiles { f -> f.isDirectory }!!.forEach {
            myFixture.copyFileToProject("Tree/assets/${it.name}/meta.json", "Untitled/assets/${it.name}/meta.json")
        }
        assertEquals(listOf("skybox_default", "skybox_hdr", "skybox_physical"), loadSkyboxChoices(project, abss, testMetaFiles(), testCore.hdrPreviews)!!.map { it.name })
    }

    fun testListsTheHdrFixture() {
        val abss = diskHdrFixture()
        val choice = loadSkyboxChoices(project, abss, testMetaFiles(), testCore.hdrPreviews)!!.single { it.name == "skybox_hdr" }
        assertEquals(MetaType.SKYBOX_HDR, choice.type)
        assertEquals(HdrSkyInfo("sky.exr", 1024, 512), choice.hdr)
        assertEquals(listOf("sky.exr"), choice.faceFiles)
        assertTrue(choice.unused)
    }

    fun testHeaderDimensionsDoNotRequireImagePixels() {
        val abss = diskHdrFixture(truncated = true)
        val choice = loadSkyboxChoices(project, abss, testMetaFiles(), testCore.hdrPreviews)!!.single()
        assertEquals(HdrSkyInfo("sky.exr", 1024, 512), choice.hdr)
    }

    private fun diskHdrFixture(truncated: Boolean = false): com.intellij.openapi.vfs.VirtualFile {
        val dir = com.intellij.openapi.util.io.FileUtil.createTempDirectory("abyssus-hdr-choices", null)
        val sky = java.io.File(dir, "assets/skybox_hdr").apply { mkdirs() }
        java.io.File(dir, "scenes").mkdirs()
        java.io.File("$testDataPath/Tree/Untitled.abss").copyTo(java.io.File(dir, "Untitled.abss"))
        java.io.File("$testDataPath/Tree/scenes/Main Scene.scene").copyTo(java.io.File(dir, "scenes/Main Scene.scene"))
        java.io.File("$testDataPath/Tree/assets/skybox_hdr/meta.json").copyTo(java.io.File(sky, "meta.json"))
        val bytes = java.io.File("$testDataPath/Untitled/assets/skybox_hdr/sky.exr").readBytes()
        java.io.File(sky, "sky.exr").writeBytes(if (truncated) bytes.copyOf(bytes.size / 2) else bytes)
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(java.io.File(dir, "Untitled.abss"))!!
    }

    fun testHdrDetailLine() {
        val dto = ProjectDto("P", emptyList(), listOf(testAsset("sky", "1", "SKYBOX_HDR")))
        val choice = skyboxChoices(dto, emptyMap(), mapOf("sky" to HdrSkyInfo("sky.hdr", 4096, 2048))).single()
        // sizes are not grouped ("4,096") whatever the locale
        assertEquals("HDR · 4096 × 2048", choice.detail)
        assertFalse(choice.procedural)
    }

    fun testUnreadableHdrDetailIsHdr() {
        val abss = myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""").virtualFile
        myFixture.addFileToProject("p/assets/broken/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{"file":"sky.hdr"}}""")
        myFixture.addFileToProject("p/assets/broken/sky.hdr", "this is not an image")
        val choice = loadSkyboxChoices(project, abss, testMetaFiles(), testCore.hdrPreviews)!!.single()
        assertEquals(HdrSkyInfo("sky.hdr", 0, 0), choice.hdr)
        assertEquals("HDR", choice.detail)
        // an HDR sky with no image at all is still listed, with no thumbnail cell to fill
        assertEquals("HDR", skyboxChoices(ProjectDto("P", emptyList(), listOf(testAsset("bare", "1", "SKYBOX_HDR"))), emptyMap()).single().detail)
    }

    fun testDetailLineCountsFacesAndSortsFormats() {
        val dto = ProjectDto("P", emptyList(), listOf(testAsset("sky", "1", "SKYBOX")))
        val meta = SceneJson().parse(faces("a.png", "b.PNG", "c.jpg", "d.png", "e.jpg", "f.png"))
        val choice = skyboxChoices(dto, mapOf("sky" to meta)).single()
        assertEquals(6, choice.faces)
        assertEquals(listOf("jpg", "png"), choice.formats)
        assertEquals("6 faces · jpg, png", choice.detail)
    }

    fun testMissingFacesAndMetaDoNotFail() {
        val dto = ProjectDto("P", emptyList(), listOf(testAsset("partial", "1", "SKYBOX"), testAsset("bare", "2", "SKYBOX")))
        val choices = skyboxChoices(dto, mapOf("partial" to SceneJson().parse(faces("a.png", "", "c.png")), "bare" to null))
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
            listOf(scene("nebula"), scene("nebula"), scene(null)).mapIndexed { i, scene ->
                SceneEntry(myFixture.addFileToProject("scenes/$i.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile, scene)
            },
            listOf(testAsset("nebula", "1", "SKYBOX"), testAsset("dusk", "2", "SKYBOX", unused = true)),
        )
        val byName = skyboxChoices(dto, emptyMap()).associateBy { it.name }
        assertEquals(2, byName.getValue("nebula").sceneCount)
        assertFalse(byName.getValue("nebula").unused)
        assertEquals(0, byName.getValue("dusk").sceneCount)
        assertTrue(byName.getValue("dusk").unused)
    }

    fun testChooserOnlyOnAProjectScenesOwnSkyboxRow() {
        val abss = myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""").virtualFile
        val inProject = myFixture.addFileToProject("p/scenes/A.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile
        val standalone = myFixture.addFileToProject("loose/B.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile
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
        myFixture.copyFileToProject("Untitled/assets/skybox_physical/meta.json", "Untitled/assets/skybox_physical/meta.json")
        val abss = myFixture.findFileInTempDir("Untitled/Untitled.abss")
        val choices = loadSkyboxChoices(project, abss, testMetaFiles(), testCore.hdrPreviews)!!.associateBy { it.name }
        // Main Scene names skybox_physical; skybox_default is in the project but used by no scene
        val physical = choices.getValue("skybox_physical")
        assertEquals("procedural sky", physical.detail)
        assertFalse(physical.unused)
        assertEquals(1, physical.sceneCount)
        val default = choices.getValue("skybox_default")
        assertEquals("6 faces · png", default.detail)
        assertTrue(default.unused)
        assertEquals(0, default.sceneCount)
    }
}
