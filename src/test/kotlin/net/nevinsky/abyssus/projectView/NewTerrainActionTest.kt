/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.tree.TreeVisitor
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.assetfiles.AssetFileStore
import net.nevinsky.abyssus.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.terrainData
import net.nevinsky.abyssus.terrain.generation.TerrainGenerationDraft
import net.nevinsky.abyssus.terrain.generation.TerrainGenerationSettings
import net.nevinsky.abyssus.terrain.generation.TerrainPreview
import net.nevinsky.abyssus.terrain.generation.SourceSnapshot
import net.nevinsky.abyssus.dto.ProjectReader
import net.nevinsky.abyssus.terrain.FolderNameError
import net.nevinsky.abyssus.terrain.GeometryError
import net.nevinsky.abyssus.terrain.NewTerrainFactory
import net.nevinsky.abyssus.terrain.NewTerrainRequest
import net.nevinsky.abyssus.terrain.checkFolderName
import net.nevinsky.abyssus.terrain.checkGeometry
import java.io.File
import java.nio.file.Files

class NewTerrainActionTest : BasePlatformTestCase() {
    private lateinit var projectDir: File
    private lateinit var abss: VirtualFile
    private val sceneText = """{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{}}}"""

    override fun setUp() {
        super.setUp()
        projectDir = FileUtil.createTempDirectory("abyssus-newterrain", null)
        File(projectDir, "P.abss").writeText("""{"format":"abyssus","formatVersion":1,"name":"P"}""")
        File(projectDir, "scenes").mkdirs()
        File(projectDir, "scenes/Main.scene").writeText(sceneText)
        File(projectDir, "assets/existing").mkdirs()
        File(projectDir, "assets/existing/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"${java.util.UUID.nameUUIDFromBytes("fixed-uuid".toByteArray())}","type":"MODEL","additional":{}}""")
        abss = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectDir, "P.abss"))!!
        abss.parent.refresh(false, true)
    }

    private fun preview(size: Int = 400, resolution: Int = 17, settings: TerrainGenerationSettings = TerrainGenerationSettings()): TerrainPreview {
        val core = service<AbyssusCore>()
        return TerrainPreview(settings, size, resolution, core.terrainGenerator.generate(resolution, size, settings))
    }

    private fun create(name: String = "hills", preview: TerrainPreview = preview(), store: AssetFileStore = LocalAssetFileStore(projectDir)): AssetCommandResult {
        val staged = service<AbyssusCore>().newTerrains.stage(projectDir, name, preview)!!
        return AssetFileCommand(project, store).execute(staged.transaction)
    }

    private fun bytes(path: String) = File(projectDir, path).readBytes()

    // ownership and selection

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun projectNode(): AbstractTreeNode<*> {
        // the project tree reads through the VFS, so the project must be one the fixture sees: an in-memory copy
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        myFixture.addFileToProject("p/scenes/Main.scene", sceneText)
        myFixture.addFileToProject("p/assets/tree/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"u","type":"MODEL","additional":{}}""")
        return children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
    }

    fun testOnlyTheAssetsNodeOfARecognizedProjectOwnsTheAction() {
        val abssNode = projectNode()
        val assets = children(abssNode).single { label(it) == "assets" }
        val owner = assetsNodeProjectFile(assets)
        assertEquals("P.abss", owner!!.name)
        assertNull(assetsNodeProjectFile(children(abssNode).single { label(it) == "scenes" }))
        assertNull("an asset row is not the Assets node", assetsNodeProjectFile(children(assets).single()))
        assertNull("the project node", assetsNodeProjectFile(abssNode))
        assertNull(assetsNodeProjectFile(null))
        assertNull(assetsNodeProjectFile("not a node"))
    }


    fun testUnsupportedUnsavedProjectCannotOwnOrCreateTerrain() {
        val node = projectNode()
        val assets = children(node).single { label(it) == "assets" }
        val file = assetsNodeProjectFile(assets)!!
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.setText("""{"format":"abyssus","formatVersion":2}""")
        }
        assertNull(assetsNodeProjectFile(assets))
        val errors = mutableListOf<String>()
        assertNull(createTerrain(project, file, net.nevinsky.abyssus.terrain.NewTerrainRequest("refused", preview()), errors::add))
        assertEquals(1, errors.size)
        assertNull(file.parent.findChild("assets")!!.findChild("refused"))
    }

    fun testAStandaloneSceneSelectionHasNoAssetsOwner() {
        val scene = myFixture.addFileToProject("standalone/Alone.scene", sceneText).virtualFile
        val nodes = children(AbyssusRootNode(project, ViewSettings.DEFAULT))
        assertTrue(nodes.any { label(it) == scene.name })
        nodes.forEach { assertNull(assetsNodeProjectFile(it)) }
    }

    fun testTheTreeVisitorWalksToTheNewAssetUnderItsOwnProject() {
        val abssNode = projectNode() as AbyssusAssetNode
        val file = abssNode.virtualFile
        val assets = children(abssNode).single { label(it) == "assets" }
        val tree = children(assets).single()
        val root = AbyssusRootNode(project, ViewSettings.DEFAULT)
        assertEquals(TreeVisitor.Action.CONTINUE, assetVisitAction(file, "tree", listOf(root)))
        assertEquals(TreeVisitor.Action.CONTINUE, assetVisitAction(file, "tree", listOf(root, abssNode)))
        assertEquals(TreeVisitor.Action.CONTINUE, assetVisitAction(file, "tree", listOf(root, abssNode, assets)))
        assertEquals(TreeVisitor.Action.INTERRUPT, assetVisitAction(file, "tree", listOf(root, abssNode, assets, tree)))
        assertEquals(TreeVisitor.Action.SKIP_CHILDREN, assetVisitAction(file, "other", listOf(root, abssNode, assets, tree)))
        val scenes = children(abssNode).single { label(it) == "scenes" }
        assertEquals(TreeVisitor.Action.SKIP_CHILDREN, assetVisitAction(file, "tree", listOf(root, abssNode, scenes)))
    }

    // names and geometry

    private val assetsDir get() = File(projectDir, "assets")

    fun testNamesAreValidated() {
        assertNull(checkFolderName(assetsDir, "hills"))
        assertNull(checkFolderName(assetsDir, "  hills 2  "))
        assertEquals(FolderNameError.BLANK, checkFolderName(assetsDir, ""))
        assertEquals(FolderNameError.BLANK, checkFolderName(assetsDir, "   "))
        assertEquals(FolderNameError.DOT, checkFolderName(assetsDir, ".."))
        assertEquals(FolderNameError.DOT, checkFolderName(assetsDir, "."))
        assertEquals(FolderNameError.SEPARATOR, checkFolderName(assetsDir, "../x"))
        assertEquals(FolderNameError.SEPARATOR, checkFolderName(assetsDir, "a/b"))
        assertEquals(FolderNameError.SEPARATOR, checkFolderName(assetsDir, "a\\b"))
        assertEquals(FolderNameError.SEPARATOR, checkFolderName(assetsDir, "/etc/passwd"))
        assertEquals(FolderNameError.INVALID_CHARACTER, checkFolderName(assetsDir, "C:x"))
        assertEquals(FolderNameError.INVALID_CHARACTER, checkFolderName(assetsDir, "a*b"))
        assertEquals(FolderNameError.INVALID_CHARACTER, checkFolderName(assetsDir, "a\u0001b"))
        assertEquals(FolderNameError.TRAILING, checkFolderName(assetsDir, "hills."))
        assertEquals(FolderNameError.RESERVED, checkFolderName(assetsDir, "CON"))
        assertEquals(FolderNameError.RESERVED, checkFolderName(assetsDir, "nul.txt"))
    }

    fun testExistingNamesCollideInAnyCaseAndAsFilesOrLinks() {
        assertEquals(FolderNameError.EXISTS, checkFolderName(assetsDir, "existing"))
        assertEquals(FolderNameError.EXISTS, checkFolderName(assetsDir, "EXISTING"))
        File(assetsDir, "notes.txt").writeText("x")
        assertEquals("a file blocks the name too", FolderNameError.EXISTS, checkFolderName(assetsDir, "Notes.TXT"))
        val link = File(assetsDir, "linked").toPath()
        if (runCatching { Files.createSymbolicLink(link, File(projectDir, "scenes").toPath()) }.isSuccess) {
            assertEquals(FolderNameError.EXISTS, checkFolderName(assetsDir, "linked"))
        }
        val dangling = File(assetsDir, "dangling").toPath()
        if (runCatching { Files.createSymbolicLink(dangling, File(projectDir, "nowhere").toPath()) }.isSuccess) {
            assertEquals("a dangling link still occupies the name", FolderNameError.EXISTS, checkFolderName(assetsDir, "dangling"))
        }
    }

    fun testAMissingAssetsFolderAcceptsAnyValidName() {
        assertNull(checkFolderName(File(projectDir, "nothing"), "hills"))
        assertEquals(FolderNameError.SEPARATOR, checkFolderName(File(projectDir, "nothing"), "../x"))
    }

    fun testResolutionAndSizeLimits() {
        assertNull(checkGeometry(1600, 180))
        assertNull(checkGeometry(1, 2))
        assertNull(checkGeometry(1600, 255))
        assertEquals(GeometryError.RESOLUTION, checkGeometry(1600, 1))
        assertEquals(GeometryError.RESOLUTION, checkGeometry(1600, 256))
        assertEquals(GeometryError.RESOLUTION, checkGeometry(1600, null))
        assertEquals(GeometryError.SIZE, checkGeometry(0, 180))
        assertEquals(GeometryError.SIZE, checkGeometry(-5, 180))
        assertEquals(GeometryError.SIZE, checkGeometry(null, 180))
    }

    // creation

    fun testCreatingWritesTheAssetAndLeavesTheSceneAndProjectFilesAlone() {
        val abssBefore = bytes("P.abss").toList()
        val sceneBefore = bytes("scenes/Main.scene").toList()
        assertEquals(AssetCommandResult.Done, create())
        assertEquals(abssBefore, bytes("P.abss").toList())
        assertEquals(sceneBefore, bytes("scenes/Main.scene").toList())
        val meta = File(projectDir, "assets/hills/meta.json").readText()
        assertTrue(meta, meta.startsWith("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":"""))
        assertTrue(meta.contains(""""type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":400,"uv":1.0,"splatMap":null"""))
        assertEquals(17 * 17 * 4, bytes("assets/hills/terrain.data").size)
        assertTrue(File(projectDir, "assets/hills/abyssus-terrain.recipe.json").isFile)
    }

    fun testTheNewTerrainLoadsAndWithoutItsRecipeToo() {
        create()
        File(projectDir, "assets/hills/abyssus-terrain.recipe.json").delete()
        val data = terrainData(projectDir, "hills")
        assertEquals(17, data.resolution)
        assertEquals(400, data.size)
    }

    fun testTheNewAssetIsListedAsUnusedAndNothingIsPlaced() {
        create()
        abss.parent.refresh(false, true)
        val project = project.service<ProjectReader>().read(abss).obj!!
        val hills = project.assets.single { it.name == "hills" }
        assertTrue("no scene references it", hills.unused)
        assertEquals(net.nevinsky.abyssus.core.assets.MetaType.TERRAIN, hills.type)
        assertEquals(2, project.assets.size)
        assertFalse(File(projectDir, "scenes/Main.scene").readText().contains("hills"))
    }

    fun testMissingAssetsFolderIsCreatedAndRemovedAgainOnUndo() {
        File(projectDir, "assets").deleteRecursively()
        assertEquals(AssetCommandResult.Done, create())
        assertTrue(File(projectDir, "assets/hills/meta.json").isFile)
        UndoManager.getInstance(project).undo(null)
        assertFalse("the folder this operation made is removed with it", File(projectDir, "assets").exists())
    }

    fun testUndoAndRedoKeepTheUuidAndBytes() {
        assertEquals(AssetCommandResult.Done, create())
        val meta = bytes("assets/hills/meta.json").toList()
        val heights = bytes("assets/hills/terrain.data").toList()
        val recipe = bytes("assets/hills/abyssus-terrain.recipe.json").toList()
        UndoManager.getInstance(project).undo(null)
        assertFalse(File(projectDir, "assets/hills").exists())
        assertTrue(File(projectDir, "assets/existing/meta.json").isFile)
        UndoManager.getInstance(project).redo(null)
        assertEquals(meta, bytes("assets/hills/meta.json").toList())
        assertEquals(heights, bytes("assets/hills/terrain.data").toList())
        assertEquals(recipe, bytes("assets/hills/abyssus-terrain.recipe.json").toList())
    }

    fun testACollidingFolderIsRejectedWithoutChangingAnything() {
        assertNull("staging re-checks the name", service<AbyssusCore>().newTerrains.stage(projectDir, "existing", preview()))
        assertNull(service<AbyssusCore>().newTerrains.stage(projectDir, "../escape", preview()))
        assertEquals(java.util.UUID.nameUUIDFromBytes("fixed-uuid".toByteArray()).toString(), com.fasterxml.jackson.databind.ObjectMapper().readTree(File(projectDir, "assets/existing/meta.json")).get("uuid").asText())
        File(projectDir, "assets/hills").mkdirs()
        File(projectDir, "assets/hills/mine.txt").writeText("keep")
        val staged = NewTerrainFactoryFor("hills-staged")
        assertNotNull(staged)
        File(projectDir, "assets/hills-staged").mkdirs() // appears after staging
        File(projectDir, "assets/hills-staged/mine.txt").writeText("keep")
        assertEquals(AssetCommandResult.Collision("assets/hills-staged"), AssetFileCommand(project, LocalAssetFileStore(projectDir)).execute(staged!!.transaction))
        assertEquals("keep", File(projectDir, "assets/hills-staged/mine.txt").readText())
    }

    private fun NewTerrainFactoryFor(name: String) = service<AbyssusCore>().newTerrains.stage(projectDir, name, preview())

    fun testAFailedWriteLeavesNothingBehind() {
        val real = LocalAssetFileStore(projectDir)
        for (n in 0..2) for (after in listOf(false, true)) {
            var writes = 0
            val failing = object : AssetFileStore by real {
                override fun write(path: String, bytes: ByteArray) {
                    val i = writes++
                    if (i == n && !after) throw java.io.IOException("injected")
                    real.write(path, bytes)
                    if (i == n && after) throw java.io.IOException("injected")
                }
            }
            val result = create(store = failing)
            assertTrue("$n $after $result", result is AssetCommandResult.Failed && result.rolledBack)
            assertFalse(File(projectDir, "assets/hills").exists())
            assertEquals(listOf("existing"), File(projectDir, "assets").list()!!.toList())
        }
    }

    fun testFreshUuidsAreUniqueInTheProject() {
        val core = service<AbyssusCore>()
        val fixed = java.util.UUID.nameUUIDFromBytes("fixed-uuid".toByteArray())
        val brandNew = java.util.UUID.nameUUIDFromBytes("brand-new".toByteArray())
        val sequence = ArrayDeque(listOf(fixed, fixed, brandNew))
        val factory = NewTerrainFactory(core.json, core.terrainWriter, core.heightEncoder, core.terrainRecipes, randomUuid = { sequence.removeFirst() })
        val staged = factory.stage(projectDir, "hills", preview())!!
        assertEquals(brandNew.toString(), staged.uuid)
        assertTrue(String(staged.transaction.changes.first { it.path.endsWith("meta.json") }.after.let { (it as net.nevinsky.abyssus.assetfiles.FileSnapshot.Bytes).toByteArray() }).contains("\"uuid\":\"$brandNew\""))
        // the real generator never repeats within a run
        val real = (1..50).map { core.newTerrains.stage(projectDir, "t$it", preview())!!.uuid }
        assertEquals(50, real.toSet().size)
    }

    fun testNoRequestWithoutAMatchingPreviewIsOffered() {
        val draft = TerrainGenerationDraft(TerrainGenerationSettings(), 400, 17, SourceSnapshot(null, null, null, false))
        assertNull(draft.applicable(SourceSnapshot(null, null, null, false)))
        val request = draft.begin()!!
        draft.complete(request, FloatArray(17 * 17))
        assertNotNull(draft.applicable(SourceSnapshot(null, null, null, false)))
        assertNotNull(NewTerrainRequest("x", draft.preview!!))
    }
}
