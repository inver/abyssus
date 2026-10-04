/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.filetype.SceneJson
import java.io.File
import java.time.ZoneOffset
import net.nevinsky.abyssus.testMetaFiles

private fun load(folder: com.intellij.openapi.vfs.VirtualFile) = loadAssetMeta(folder, testMetaFiles())

class MetaRowsTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun sample(folder: String) = SceneJson.parseObject(File("$testDataPath/Untitled/assets/$folder/meta.json").readText())

    private fun terrainFolder() = File("$testDataPath/Untitled/assets").listFiles { f -> f.name.startsWith("terrain_") }!!.single().name

    private fun rows(json: String) = metaRowsOf(SceneJson.parseObject(json), ZoneOffset.UTC)

    fun testSkyboxListsTopLevelFieldsThenAdditionalFacesInFileOrder() {
        val rows = metaRowsOf(sample("skybox_default"), ZoneOffset.UTC)
        assertEquals(listOf("version", "lastModified", "type", "additional", "top", "bottom", "left", "right", "front", "back"), rows.map { it.name })
        assertEquals("SKYBOX", rows.single { it.name == "type" }.value)
        assertEquals(RowKind.HEADING, rows.single { it.name == "additional" }.kind)
        val faces = rows.dropWhile { it.kind != RowKind.HEADING }.drop(1)
        assertTrue(faces.all { it.kind == RowKind.ADDITIONAL && it.value == "skybox_default.png" })
        assertEquals(6, faces.size)
    }

    fun testTerrainShowsNumbersAsWrittenAndNullAsNull() {
        val rows = metaRowsOf(sample(terrainFolder()), ZoneOffset.UTC).associateBy { it.name }
        assertEquals("1600", rows.getValue("size").value)
        assertEquals("60.0", rows.getValue("uv").value)
        assertEquals("null", rows.getValue("splatMap").value)
        assertEquals(RowKind.ADDITIONAL, rows.getValue("size").kind)
    }

    fun testModelListSummarisesAnEmptyListAndShowsBooleans() {
        val rows = metaRowsOf(sample("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"), ZoneOffset.UTC).associateBy { it.name }
        assertEquals("0 items", rows.getValue("materials").value)
        assertEquals("true", rows.getValue("binary").value)
        assertEquals("29e9be61-6594-4f82-a6cf-44ccf09f71fb", rows.getValue("uuid").value)
    }

    fun testSingularListAndObjectSummaries() {
        val rows = rows("""{"additional":{"a":[1],"b":{"x":1,"y":2},"c":{"x":1}}}""").associateBy { it.name }
        assertEquals("1 item", rows.getValue("a").value)
        assertEquals("2 properties", rows.getValue("b").value)
        assertEquals("1 property", rows.getValue("c").value)
    }

    fun testMissingUuidAndMissingAdditionalAreNotListed() {
        val rows = rows("""{"version":1,"type":"MODEL"}""")
        assertEquals(listOf("version", "type"), rows.map { it.name })
        assertTrue(rows.none { it.kind != RowKind.FIELD })
    }

    fun testLastModifiedIsADateTimeAndOtherNumbersAreUntouched() {
        val rows = rows("""{"lastModified":1663444124794,"version":1}""").associateBy { it.name }
        assertEquals("2022-09-17 19:48:44", rows.getValue("lastModified").value)
        assertEquals("1", rows.getValue("version").value)
    }

    fun testUnknownTypeAndFieldsAreStillListed() {
        val rows = rows("""{"type":"WIDGET","additional":{"shiny":true}}""")
        assertEquals(listOf("type", "additional", "shiny"), rows.map { it.name })
    }

    // 1.2 loading

    private fun asset(meta: String?): com.intellij.openapi.vfs.VirtualFile {
        myFixture.addFileToProject("p/assets/a/other.txt", "x")
        meta?.let { myFixture.addFileToProject("p/assets/a/meta.json", it) }
        return myFixture.findFileInTempDir("p/assets/a")
    }

    fun testLoadsAValidMeta() {
        val loaded = load(asset("""{"type":"SKYBOX","additional":{"top":"t.png"}}""")) as AssetMeta.Loaded
        assertEquals(net.nevinsky.abyssus.assets.files.MetaType.SKYBOX, loaded.type)
        assertEquals(listOf("type", "additional", "top"), loaded.rows.map { it.name })
    }

    fun testMissingMetaFailsWithTheFolderName() {
        val failed = load(asset(null)) as AssetMeta.Failed
        assertTrue(failed.message, failed.message.contains("a") && failed.message.contains("meta.json"))
    }

    fun testMalformedMetaFailsWithTheReason() {
        val failed = load(asset("{not json")) as AssetMeta.Failed
        assertTrue(failed.message, failed.message.startsWith("Cannot read the asset's meta: "))
        assertTrue(failed.message.length > "Cannot read the asset's meta: ".length)
    }

    fun testNonObjectMetaFails() {
        assertTrue(load(asset("[1]")) is AssetMeta.Failed)
    }

    fun testUnsavedEditorTextWinsOverTheFile() {
        val folder = asset("""{"type":"MODEL"}""")
        val file = folder.findChild("meta.json")!!
        val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { doc.setText("""{"type":"TERRAIN"}""") }
        assertEquals(net.nevinsky.abyssus.assets.files.MetaType.TERRAIN, (load(folder) as AssetMeta.Loaded).type)
    }
}
