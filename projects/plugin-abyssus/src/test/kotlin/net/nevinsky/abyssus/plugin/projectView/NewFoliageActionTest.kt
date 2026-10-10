/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FOLIAGE_DATA_FILE
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_MASK_RESOLUTION_DIALOG_DEFAULT
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_MASK_RESOLUTION_MAX
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_MASK_RESOLUTION_MIN
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.dto.ProjectReader
import org.slf4j.helpers.NOPLogger
import java.io.File

/** New Foliage... on copies of the Untitled and Foliage fixtures: Create, Undo and what makes the action available. */
class NewFoliageActionTest : BasePlatformTestCase() {
    private lateinit var projectDir: File
    private lateinit var abss: VirtualFile
    private val processor = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val terrain = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"

    override fun setUp() {
        super.setUp()
        projectDir = FileUtil.createTempDirectory("abyssus-new-foliage", null)
    }

    /** A writable copy of a committed fixture; the fixture itself is never touched. */
    private fun useFixture(name: String) {
        FileUtil.copyDir(File("src/test/testData/project/$name"), projectDir)
        abss = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectDir, "$name.abss"))!!
        abss.parent.refresh(false, true)
    }

    private fun create(
        terrainFolder: String,
        name: String,
        resolution: Int = FOLIAGE_MASK_RESOLUTION_DIALOG_DEFAULT,
        select: (String) -> Unit = {},
        report: (String) -> Unit = { fail(it) },
    ) = createFoliage(project, abss, terrainFolder, name, resolution, report, select)

    private fun metaOf(name: String) = processor.readObject(File(projectDir, "assets/$name/meta.json").readText())

    private fun bytes(path: String) = File(projectDir, path).readBytes().toList()

    /** Whether the action offers itself on a project whose Assets node is [abss]. */
    private fun offered(): Boolean {
        val action = object : NewFoliageAction() {
            override fun owningProject(e: AnActionEvent): VirtualFile? = abss
        }
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    // Create

    fun testCreateForTheFixtureTerrainWritesANativeAssetAndTouchesNoOtherFile() {
        useFixture("Untitled")
        val sceneBefore = bytes("scenes/Main Scene.scene")
        val projectBefore = bytes("Untitled.abss")
        val terrainUuid = metaOf(terrain)["uuid"].asText()
        var selected: String? = null
        assertEquals(AssetCommandResult.Done, create(terrain, "foliage_meadow", select = { selected = it }))
        assertEquals("the asset is selected in the view", "foliage_meadow", selected)

        val meta = metaOf("foliage_meadow")
        assertEquals("abyssus", meta["format"].asText())
        assertEquals(1, meta["formatVersion"].asInt())
        assertEquals(MetaType.FOLIAGE.name, meta["type"].asText())
        val uuid = meta["uuid"].asText()
        assertTrue("a fresh uuid", uuid.isNotEmpty() && uuid != terrainUuid)
        assertEquals(terrain, meta["additional"]["terrain"].asText())
        assertEquals(512, meta["additional"]["maskResolution"].asInt())
        assertFalse("a new foliage has no layers", meta["additional"].has("layers"))

        assertEquals(sceneBefore, bytes("scenes/Main Scene.scene"))
        assertEquals(projectBefore, bytes("Untitled.abss"))
        assertEquals("no masks for no layers", emptyList<String>(), File(projectDir, "assets/foliage_meadow")
            .listFiles { f -> f.name.endsWith(".mask") }.orEmpty().map { it.name })

        val files = FileLoader(projectDir)
        val metas = AssetMetaLoader(processor, files)
        val prepared = checkNotNull(FoliageLoader(files, metas, TerrainLoader(files, metas)).prepare("foliage_meadow"))
            .staged
        assertEquals("the empty bake holds no copies", 0, prepared.copyCount)
        assertFalse("a fresh bake is not stale", prepared.stale)
        assertEquals(setOf(terrain), prepared.dependencies)
        assertTrue(File(projectDir, "assets/foliage_meadow/$FOLIAGE_DATA_FILE").isFile)

        abss.parent.refresh(false, true)
        val project = project.service<ProjectReader>().read(abss).obj!!
        val listed = project.assets.single { it.name == "foliage_meadow" }
        assertEquals(MetaType.FOLIAGE, listed.type)
        assertTrue("nothing places it yet", listed.unused)
    }

    fun testCreateAndUndoOnTheFoliageFixture() {
        useFixture("Foliage")
        assertEquals(AssetCommandResult.Done, create(terrain, "foliage_lake"))
        val folder = File(projectDir, "assets/foliage_lake")
        assertTrue(folder.isDirectory)
        val meta = bytes("assets/foliage_lake/meta.json")
        val data = bytes("assets/foliage_lake/$FOLIAGE_DATA_FILE")

        UndoManager.getInstance(project).undo(null)
        assertFalse("Undo removes the new asset", folder.exists())
        assertTrue("the existing foliage stays", File(projectDir, "assets/foliage_meadow").isDirectory)

        UndoManager.getInstance(project).redo(null)
        assertEquals(meta, bytes("assets/foliage_lake/meta.json"))
        assertEquals(data, bytes("assets/foliage_lake/$FOLIAGE_DATA_FILE"))
    }

    fun testANameTakenOrAnOutOfRangeResolutionWritesNothing() {
        useFixture("Foliage")
        val treeBefore = bytes("assets/tree/meta.json")
        val reported = mutableListOf<String>()
        assertNull(create(terrain, "tree", report = reported::add))
        assertEquals(
            AbyssusBundle.message("newFoliageFailed", AbyssusBundle.message("newTerrainNameError.EXISTS")),
            reported.last(),
        )
        assertEquals(treeBefore, bytes("assets/tree/meta.json"))

        assertNull(create(terrain, "foliage_lake", resolution = 8, report = reported::add))
        assertEquals(
            AbyssusBundle.message(
                "newFoliageFailed",
                AbyssusBundle.message("newFoliageResolutionRange", FOLIAGE_MASK_RESOLUTION_MIN, FOLIAGE_MASK_RESOLUTION_MAX),
            ),
            reported.last(),
        )
        assertNull(create(terrain, "foliage_lake", resolution = 4096, report = reported::add))
        assertEquals(3, reported.size)
        assertEquals("no folder was made", setOf("foliage_meadow", "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", "tree", terrain),
            File(projectDir, "assets").listFiles { f -> f.isDirectory }!!.map { it.name }.toSet())
    }

    fun testAnUnreadableTerrainIsNotPlannedAgainst() {
        useFixture("Foliage")
        File(projectDir, "assets/$terrain/terrain.data").delete()
        val reported = mutableListOf<String>()
        assertNull(create(terrain, "foliage_lake", report = reported::add))
        assertEquals(
            AbyssusBundle.message("newFoliageFailed", AbyssusBundle.message("newFoliageUnreadable", terrain)),
            reported.single(),
        )
        assertFalse(File(projectDir, "assets/foliage_lake").exists())
    }

    // availability

    fun testTheActionIsOfferedForAProjectWithAReadableTerrain() {
        useFixture("Foliage")
        assertEquals(listOf(terrain), foliageTerrains(projectDir, processor))
        assertTrue(offered())
    }

    fun testAProjectWithoutATerrainDoesNotOfferTheAction() {
        File(projectDir, "P.abss").writeText("""{"format":"abyssus","formatVersion":1,"name":"P"}""")
        File(projectDir, "scenes").mkdirs()
        File(projectDir, "scenes/Main.scene").writeText("""{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{}}}""")
        File(projectDir, "assets/tree").mkdirs()
        File(projectDir, "assets/tree/meta.json")
            .writeText("""{"format":"abyssus","formatVersion":1,"version":1,"uuid":"u-tree","type":"MODEL","additional":{}}""")
        abss = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectDir, "P.abss"))!!
        abss.parent.refresh(false, true)

        assertEquals(emptyList<String>(), foliageTerrains(projectDir, processor))
        assertFalse(offered())
    }

    fun testAMissingHeightsFileMakesTheTerrainUnreadable() {
        useFixture("Foliage")
        assertTrue(offered())
        File(projectDir, "assets/$terrain/terrain.data").delete()
        assertEquals(emptyList<String>(), foliageTerrains(projectDir, processor))
        assertFalse(offered())
    }

    // dialog

    fun testTheDialogSuggestsTheTerrainNameAndValidatesEveryChoice() {
        useFixture("Foliage")
        val dialog = NewFoliageDialog(project, projectDir, foliageTerrains(projectDir, processor))
        try {
            assertEquals("foliage_$terrain", dialog.nameField.text)
            assertEquals(FOLIAGE_MASK_RESOLUTION_DIALOG_DEFAULT, dialog.resolutionField.text.toInt())
            assertEquals(terrain, dialog.terrain)
            assertTrue(dialog.isOKActionEnabled)

            dialog.resolutionField.text = "8"
            assertFalse(dialog.isOKActionEnabled)
            dialog.resolutionField.text = "4096"
            assertFalse(dialog.isOKActionEnabled)
            dialog.resolutionField.text = "512"
            assertTrue(dialog.isOKActionEnabled)

            dialog.nameField.text = "foliage_meadow"
            assertFalse(dialog.isOKActionEnabled)
            dialog.nameField.text = "../escape"
            assertFalse(dialog.isOKActionEnabled)
            dialog.nameField.text = "foliage_lake"
            assertTrue(dialog.isOKActionEnabled)
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }
}
