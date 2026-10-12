/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.editor.modelimport.ImportSettings
import net.nevinsky.abyssus.lib.core.editor.modelimport.LengthUnit
import net.nevinsky.abyssus.lib.core.editor.modelimport.ModelSource
import net.nevinsky.abyssus.lib.core.editor.modelimport.ModelSourceOpener
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.dto.ProjectReader
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import java.io.File

/** Import Model into a copy of the Untitled fixture, with and without placement in `Main Scene.scene`. */
class ImportModelTest : BasePlatformTestCase() {
    private lateinit var projectDir: File
    private lateinit var abss: VirtualFile
    private lateinit var scene: VirtualFile
    private lateinit var fixtures: File
    private val sources = ArrayList<ModelSource>()

    override fun setUp() {
        super.setUp()
        projectDir = FileUtil.createTempDirectory("abyssus-model-import", null)
        FileUtil.copyDir(File("src/test/testData/project/Untitled"), projectDir)
        abss = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectDir, "Untitled.abss"))!!
        abss.parent.refresh(false, true)
        scene = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectDir, "scenes/Main Scene.scene"))!!
        fixtures = FileUtil.createTempDirectory("abyssus-model-import-sources", null)
        File("../lib-core-editor/src/test/resources/modelimport").copyRecursively(fixtures)
    }

    override fun tearDown() {
        try {
            sources.forEach(ModelSource::close)
        } finally {
            super.tearDown()
        }
    }

    private fun source(name: String) = ModelSourceOpener().open(File(fixtures, name)).also(sources::add)

    private val crate = ImportSettings("model_crate", LengthUnit.CM, UpAxis.Y)

    private fun tree(dir: File): Map<String, List<Byte>> = dir.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(dir).path to it.readBytes().toList() }

    private fun placement(point: Vec3 = Vec3(10f, 0f, -4f), playing: Boolean = false) = ScenePlacement(scene, { point }, { playing })

    private fun sceneRoot() = SceneJson().parse(FileDocumentManager.getInstance().getDocument(scene)!!.text)

    private fun nextEntityId(): String = (SceneEntityTree(sceneRoot()).entities()!!.properties().maxOf { it.key.toInt() } + 1).toString()

    /** Undo of the import is refused: the platform reports the reason (through the log in tests), naming [mentions]. */
    private fun assertUndoRefused(mentions: String) {
        try {
            UndoManager.getInstance(project).undo(null)
            fail("expected a refused undo")
        } catch (e: Throwable) {
            if (e is junit.framework.AssertionFailedError && e.message == "expected a refused undo") throw e
            val messages = generateSequence(e) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue(messages.toString(), messages.any { it.contains(mentions) })
        }
    }

    fun testAnObjBecomesAnUnusedModelAndTouchesNoSceneOrProject() {
        val before = tree(projectDir)
        val sourcesBefore = tree(fixtures)
        val errors = mutableListOf<String>()
        val outcome = importModel(project, abss, source("crate.obj"), crate, null, errors::add)
        assertEquals(emptyList<String>(), errors)
        assertEquals(AssetCommandResult.Done, outcome!!.result)
        assertNull(outcome.entityId)
        val folder = File(projectDir, "assets/model_crate")
        assertEquals(setOf("meta.json", "model.glb", "source.json", "textures/wood.png"), tree(folder).keys.map { it.replace('\\', '/') }.toSet())
        assertEquals("only the new folder appears", before, tree(projectDir).filterKeys { !it.startsWith("assets/model_crate") })
        abss.parent.refresh(false, true)
        val asset = project.service<ProjectReader>().read(abss).obj!!.assets.single { it.name == "model_crate" }
        assertEquals(MetaType.MODEL, asset.type)
        assertTrue(asset.unused)
        assertEquals("nothing is written beside the source", sourcesBefore, tree(fixtures))
    }

    fun testUndoRemovesTheFolderAndRedoRestoresTheSameBytesAndUuid() {
        importModel(project, abss, source("crate.obj"), crate, null) { fail(it) }
        val folder = File(projectDir, "assets/model_crate")
        val made = tree(folder)
        val uuid = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER).readObject(File(folder, "meta.json").readText())["uuid"].asText()
        UndoManager.getInstance(project).undo(null)
        assertFalse(folder.exists())
        UndoManager.getInstance(project).redo(null)
        assertEquals(made, tree(folder))
        assertEquals(uuid, JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER).readObject(File(folder, "meta.json").readText())["uuid"].asText())
    }

    fun testUndoIsRefusedOnceASceneNamesTheAsset() {
        importModel(project, abss, source("crate.obj"), crate, null) { fail(it) }
        // a reference saved outside the undo stack, as another tool or a teammate would write it
        val sceneFile = File(projectDir, "scenes/Main Scene.scene")
        sceneFile.writeText(sceneFile.readText().replaceFirst(Regex("(\"assetName\"\\s*:\\s*)\"[^\"]*\""), "$1\"model_crate\""))
        assertTrue(sceneFile.readText().contains("model_crate"))
        scene.refresh(false, false)
        assertUndoRefused("model_crate")
        assertTrue(File(projectDir, "assets/model_crate/meta.json").isFile)
    }

    fun testANonNativeProjectIsRefusedBeforeWriting() {
        File(projectDir, "Untitled.abss").writeText("""{"name":"legacy"}""")
        abss.refresh(false, false)
        val errors = mutableListOf<String>()
        assertNull(importModel(project, abss, source("crate.obj"), crate, null, errors::add))
        assertEquals(1, errors.size)
        assertFalse(File(projectDir, "assets/model_crate").exists())
    }

    fun testABlenderFileIsRefusedWithTheExportHint() {
        val reason = sourceRefusal("ship.blend")!!
        assertTrue(reason, reason.contains("glTF"))
        assertNull(sourceRefusal("crate.obj"))
        assertNotNull(sourceRefusal("notes.txt"))
        assertFalse(File(projectDir, "assets/model_ship").exists())
    }

    fun testPlacementAddsTheModelAtTheOrbitTarget() {
        val id = nextEntityId()
        val outcome = importModel(project, abss, source("crate.obj"), crate, placement()) { fail(it) }!!
        assertEquals(AssetCommandResult.Done, outcome.result)
        assertEquals(id, outcome.entityId)
        val components = SceneEntityTree(sceneRoot()).components(id)!!
        assertEquals("Model $id", components["NameComponent"]["name"].asText())
        assertEquals("OBJECT", components["TypeComponent"]["type"].asText())
        val position = components["PositionComponent"]["localPosition"]
        assertEquals(listOf(10.0, 0.0, -4.0), listOf("x", "y", "z").map { position[it]?.asDouble() ?: 0.0 })
        val asset = components["RenderComponent"]
        assertEquals("MODEL", asset["type"].asText())
        assertEquals("model_crate", asset["assetName"].asText())
        assertTrue(File(projectDir, "assets/model_crate/model.glb").isFile)
    }

    fun testOneUndoRemovesTheEntityAndTheFolderAndRedoRestoresBoth() {
        val sceneBefore = sceneRoot().toString()
        val id = importModel(project, abss, source("crate.obj"), crate, placement()) { fail(it) }!!.entityId!!
        val folder = File(projectDir, "assets/model_crate")
        val made = tree(folder)
        val sceneAfter = sceneRoot().toString()

        UndoManager.getInstance(project).undo(null)
        assertFalse(folder.exists())
        assertNull(SceneEntityTree(sceneRoot()).components(id))
        assertEquals(sceneBefore, sceneRoot().toString())

        UndoManager.getInstance(project).redo(null)
        assertEquals(made, tree(folder))
        assertEquals(sceneAfter, sceneRoot().toString())
    }

    fun testASceneMadeUnreadableBeforeCreateWritesNothing() {
        val document = FileDocumentManager.getInstance().getDocument(scene)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { document.setText("""{"name":"legacy"}""") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val undoBefore = UndoManager.getInstance(project).isUndoAvailable(null)
        val errors = mutableListOf<String>()
        assertNull(importModel(project, abss, source("crate.obj"), crate, placement(), errors::add))
        assertEquals(1, errors.size)
        assertFalse(File(projectDir, "assets/model_crate").exists())
        assertEquals("""{"name":"legacy"}""", document.text)
        assertEquals(undoBefore, UndoManager.getInstance(project).isUndoAvailable(null))
        assertEquals(PlacementOption.Disabled(PlacementBlock.UNREADABLE), placementOption(project, placement()))
    }

    /** In the same scene the platform refuses first: the scene changed since the import's own edit of it. */
    fun testUndoStaysRefusedWhenTheModelWasAlsoPlacedByAddAsset() {
        importModel(project, abss, source("crate.obj"), crate, placement()) { fail(it) }
        val other = SceneComponentEdits.addAsset(project, scene, RenderAsset("MODEL", "model_crate"), Vec3(1f, 0f, 1f),
            SceneDocumentCache.of(project), service<AbyssusCore>().assets.metaFiles)
        assertNotNull(other.entityId)
        assertUndoRefused("Main Scene.scene")
        assertTrue(File(projectDir, "assets/model_crate/meta.json").isFile)
    }

    /** Placed by Add Asset in another scene: the reference guard refuses, naming that scene. */
    fun testUndoIsRefusedWhenAnotherScenePlacesTheModel() {
        val second = File(projectDir, "scenes/Second.scene")
        File(projectDir, "scenes/Main Scene.scene").copyTo(second)
        val secondFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(second)!!
        importModel(project, abss, source("crate.obj"), crate, placement()) { fail(it) }
        SceneComponentEdits.addAsset(project, secondFile, RenderAsset("MODEL", "model_crate"), Vec3(1f, 0f, 1f),
            SceneDocumentCache.of(project), service<AbyssusCore>().assets.metaFiles)
        FileDocumentManager.getInstance().saveAllDocuments()
        assertUndoRefused("Second.scene uses model_crate")
        assertTrue(File(projectDir, "assets/model_crate/meta.json").isFile)
    }

    fun testPlacementOptionNamesWhyItIsOff() {
        assertEquals(PlacementOption.Disabled(PlacementBlock.NO_SCENE_VIEW), placementOption(project, null))
        assertEquals(PlacementOption.Disabled(PlacementBlock.PLAYING), placementOption(project, placement(playing = true)))
        assertEquals(PlacementOption.Available("Main Scene.scene"), placementOption(project, placement()))
        val errors = mutableListOf<String>()
        assertNull(importModel(project, abss, source("crate.obj"), crate, placement(playing = true), errors::add))
        assertEquals(1, errors.size)
        assertFalse(File(projectDir, "assets/model_crate").exists())
    }
}

