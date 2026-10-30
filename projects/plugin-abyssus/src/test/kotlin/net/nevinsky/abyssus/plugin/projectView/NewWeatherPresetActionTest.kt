/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.AssetMetaBinder
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.assetfiles.*
import net.nevinsky.abyssus.plugin.dto.ProjectAssetListing
import org.junit.Ignore
import org.slf4j.helpers.NOPLogger
import java.io.File

@Ignore
class NewWeatherPresetActionTest : BasePlatformTestCase() {
    private lateinit var dir: File
    private lateinit var abss: VirtualFile
    private lateinit var sky: VirtualFile
    private val sourceId = "00000000-0000-0000-0000-000000000001"
    private val processor = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val json = SceneJson()
    private fun native(type: String, additional: String, id: String = sourceId) =
        """{"format":"abyssus","formatVersion":1,"uuid":"$id","type":"$type","additional":$additional}"""

    override fun setUp() {
        super.setUp()
        dir = FileUtil.createTempDirectory("abyssus-weather", null)
        File(dir, "P.abss").writeText("""{"format":"abyssus","formatVersion":1,"name":"P"}""")
        File(dir, "scenes").mkdirs()
        File(dir, "scenes/Main.scene").writeText("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""")
        File(dir, "assets/sky").mkdirs()
        File(dir, "assets/clouds").mkdirs()
        File(dir, "assets/sky/meta.json").writeText(
            native(
                "SKYBOX_PROCEDURAL",
                """{"clouds":"$sourceId"}""",
                "00000000-0000-0000-0000-000000000003"
            )
        )
        File(dir, "assets/clouds/meta.json").writeText(
            native(
                "CLOUDS",
                """{"technique":"volumetric","low":{"type":"cumulus"}}"""
            )
        )
        abss = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(dir, "P.abss"))!!
        abss.parent.refresh(false, true)
        sky = abss.parent.findFileByRelativePath("assets/sky/meta.json")!!
    }

    private fun factory() = NewWeatherPresetFactory(processor)
    private fun stage(name: String = "weather_sky") = factory().stage(abss, sky, name)
    private fun cloudFile() = abss.parent.findFileByRelativePath("assets/clouds/meta.json")!!
    private fun unsaved(file: VirtualFile, text: String) {
        ApplicationManager.getApplication().runWriteAction {
            FileDocumentManager.getInstance().getDocument(file)!!.setText(text)
        }
    }

    fun testCreationListsAnUnusedCloudAssetAndKeepsAllSourcesUnchanged() {
        val paths = listOf("P.abss", "scenes/Main.scene", "assets/sky/meta.json", "assets/clouds/meta.json")
        val before = paths.associateWith { File(dir, it).readText() }
        var selected: String? = null
        assertEquals(
            AssetCommandResult.Done,
            createWeatherPreset(project, abss, sky, "weather_sky", select = { selected = it }, report = { fail(it) })
        )
        assertEquals("weather_sky", selected)
        for ((path, text) in before) assertEquals(path, text, File(dir, path).readText())
        val added = ProjectAssetListing(processor).list(abss).single { it.name == "weather_sky" }
        assertEquals("CLOUDS", added.type.name)
        assertFalse(added.uuid.toString() == sourceId)
        val root = json.parse(File(dir, "assets/weather_sky/meta.json").readText())
        val settings = AssetMetaBinder(processor).bind("copy", root).typedAdditional<CloudMeta>()
        assertEquals(0.4f, settings.low!!.coverage)
        assertEquals("volumetric", root["additional"]["technique"].asText())
        val reader = net.nevinsky.abyssus.plugin.dto.ProjectReader(project)
        assertTrue(reader.read(abss).obj!!.assets.single { it.name == "weather_sky" }.unused)
    }

    fun testUnsavedSourceAndSkyReferenceAreReadOnCreate() {
        val cloud = cloudFile()
        val oldBytes = File(dir, "assets/clouds/meta.json").readText()
        val replacementId = "00000000-0000-0000-0000-000000000009"
        unsaved(cloud, native("CLOUDS", """{"high":{"type":"cirrus","coverage":0.7300}}""", replacementId))
        unsaved(sky, native("SKYBOX_PROCEDURAL", """{"clouds":"$replacementId"}"""))
        val staged = stage()
        assertEquals(
            AssetCommandResult.Done,
            AssetFileCommand(project, LocalAssetFileStore(dir)).execute(staged.transaction)
        )
        val text = File(dir, "assets/weather_sky/meta.json").readText()
        assertTrue(text.contains("0.7300"))
        assertFalse(json.parse(text)["additional"].has("low"))
        assertEquals(oldBytes, File(dir, "assets/clouds/meta.json").readText())
    }

    fun testSourceAndNativeDocumentRejectionsWriteNothing() {
        val valid = File(dir, "assets/clouds/meta.json").readText()
        for (bad in listOf(
            "broken",
            native("MODEL", "{}"),
            native("CLOUDS", "{}"),
            valid.replace("\"formatVersion\":1", "\"formatVersion\":2"),
            valid.replace(sourceId, "00000000-0000-0000-0000-000000000009")
        )) {
            unsaved(cloudFile(), bad)
            assertThrows(Exception::class.java) { stage() }
            assertFalse(File(dir, "assets/weather_sky").exists())
        }
        unsaved(cloudFile(), valid)
        unsaved(abss, """{"format":"foreign","formatVersion":1}""")
        assertThrows(Exception::class.java) { stage() }
    }

    fun testNamesAndStagingCollisionsNeverOverwrite() {
        for (name in listOf("", "..", "../escape", "a:b", "CON", "SKY", "clouds")) {
            assertThrows(Exception::class.java) { stage(name) }
        }
        val staged = stage()
        File(dir, "assets/weather_sky").mkdirs()
        File(dir, "assets/weather_sky/keep").writeText("mine")
        assertEquals(
            AssetCommandResult.Collision("assets/weather_sky"),
            AssetFileCommand(project, LocalAssetFileStore(dir)).execute(staged.transaction)
        )
        assertEquals("mine", File(dir, "assets/weather_sky/keep").readText())
    }

    fun testUndoRedoRestoreTheSameIdentityTimestampAndBytes() {
        val staged = stage()
        val command = AssetFileCommand(project, LocalAssetFileStore(dir))
        assertEquals(AssetCommandResult.Done, command.execute(staged.transaction))
        val before = File(dir, "assets/weather_sky/meta.json").readText()
        UndoManager.getInstance(project).undo(null)
        assertFalse(File(dir, "assets/weather_sky").exists())
        UndoManager.getInstance(project).redo(null)
        assertEquals(before, File(dir, "assets/weather_sky/meta.json").readText())
    }

    fun testUndoGuardSeesSavedAndUnsavedReferencesAndNewFiles() {
        val staged = stage()
        val engine = AssetTransactionEngine(LocalAssetFileStore(dir))
        assertEquals(AssetCommandResult.Done, engine.apply(staged.transaction, true))
        val linked = native("SKYBOX_PROCEDURAL", """{"clouds":"${staged.uuid}"}""")
        unsaved(sky, linked)
        assertTrue(engine.verify(staged.transaction, false) is AssetCommandResult.Blocked)
        FileDocumentManager.getInstance().saveDocument(FileDocumentManager.getInstance().getDocument(sky)!!)
        assertTrue(engine.verify(staged.transaction, false) is AssetCommandResult.Blocked)
        unsaved(sky, native("SKYBOX_PROCEDURAL", "{}"))
        File(dir, "assets/weather_sky/extra").writeText("keep")
        assertTrue(engine.verify(staged.transaction, false) is AssetCommandResult.Conflict)
    }

    fun testOnlyTheSkyAssetRowWithATextReferenceShowsTheAction() {
        val asset = ProjectAssetListing(processor).list(abss).single { it.name == "sky" }
        val node = DtoEntryNode(project, "project/assets", DtoRow("sky", asset), abss, listOf("assets"))
        val action = object : NewWeatherPresetAction() {
            override fun selected(e: com.intellij.openapi.actionSystem.AnActionEvent): Any = node
        }

        fun visible(): Boolean {
            val event = com.intellij.testFramework.TestActionEvent.createTestEvent(action)
            action.update(event)
            return event.presentation.isEnabledAndVisible
        }
        assertTrue(visible())
        for (additional in listOf("{}", """{"clouds":null}""", """{"clouds":" "}""", """{"clouds":{}}""")) {
            unsaved(sky, native("SKYBOX_PROCEDURAL", additional))
            assertFalse(visible())
        }
        unsaved(sky, native("SKYBOX", """{"clouds":"$sourceId"}"""))
        assertFalse(visible())
        unsaved(
            sky,
            native("SKYBOX_PROCEDURAL", """{"clouds":"$sourceId"}""").replace(
                "\"formatVersion\":1",
                "\"formatVersion\":2"
            )
        )
        assertFalse(visible())
        assertNull(weatherPresetTarget(null, net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader(processor)))
    }

    fun testDialogSuggestsTheSkyNameAndValidatesNames() {
        val dialog = NewWeatherPresetDialog(project, File(dir, "assets"), "sky")
        try {
            assertEquals("weather_sky", dialog.nameField.text)
            assertTrue(dialog.isOKActionEnabled)
            for (name in listOf("", "SKY", "a:b", "../escape")) {
                dialog.nameField.text = name
                assertFalse(dialog.isOKActionEnabled)
            }
            dialog.nameField.text = "weather_custom"
            assertTrue(dialog.isOKActionEnabled)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(dialog.disposable)
        }
    }

    fun testFreshUuidAvoidsUnsavedAssetIdentities() {
        val unused = java.util.UUID.fromString("00000000-0000-0000-0000-000000000011")
        val fresh = java.util.UUID.fromString("00000000-0000-0000-0000-000000000012")
        unsaved(sky, native("SKYBOX_PROCEDURAL", """{"clouds":"$sourceId"}""", unused.toString()))
        val sequence = ArrayDeque(listOf(java.util.UUID.fromString(sourceId), unused, fresh))
        val factory = NewWeatherPresetFactory(processor, randomUuid = { sequence.removeFirst() }, clock = { 123 })
        assertEquals(fresh.toString(), factory.stage(abss, sky, "weather_sky").uuid)
    }

    fun testSourceIsRevalidatedAfterTheDialogAndFailuresDoNotSelect() {
        factory().validateSource(abss, sky)
        unsaved(cloudFile(), native("CLOUDS", "{}"))
        var reported = false
        var selected = false
        assertNull(
            createWeatherPreset(
                project,
                abss,
                sky,
                "weather_sky",
                report = { reported = true },
                select = { selected = true })
        )
        assertTrue(reported)
        assertFalse(selected)
        assertFalse(File(dir, "assets/weather_sky").exists())
    }

    fun testEditedSnapshotAndRedoCollisionAreRefusedWithoutWrites() {
        val staged = stage()
        val store = LocalAssetFileStore(dir)
        val engine = AssetTransactionEngine(store)
        assertEquals(AssetCommandResult.Done, engine.apply(staged.transaction, true))
        store.flush(false)
        val created = abss.parent.findFileByRelativePath("assets/weather_sky/meta.json")!!
        val original = File(dir, "assets/weather_sky/meta.json").readText()
        unsaved(created, original + " ")
        assertTrue(engine.apply(staged.transaction, false) is AssetCommandResult.Conflict)
        unsaved(created, original)
        assertEquals(AssetCommandResult.Done, engine.apply(staged.transaction, false))
        File(dir, "assets/weather_sky").mkdirs()
        File(dir, "assets/weather_sky/mine").writeText("keep")
        assertTrue(engine.apply(staged.transaction, true) is AssetCommandResult.Collision)
        assertEquals("keep", File(dir, "assets/weather_sky/mine").readText())
    }

    fun testFailedMetadataWriteRollsBackCreation() {
        val staged = stage()
        val real = LocalAssetFileStore(dir)
        val failing = object : AssetFileStore by real {
            override fun write(path: String, bytes: ByteArray) {
                real.write(path, bytes)
                throw java.io.IOException("injected write failure")
            }
        }
        val result = AssetFileCommand(project, failing).execute(staged.transaction)
        assertTrue(result is AssetCommandResult.Failed && result.rolledBack)
        assertFalse(File(dir, "assets/weather_sky").exists())
    }

}
