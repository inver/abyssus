/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.LengthUnit
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ModelImportException
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ModelSource
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ModelSourceOpener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The Import Model dialog's state, without Swing, over the model import fixtures of `lib-core`. */
class ModelImportFormTest {
    private val fixtures = Files.createTempDirectory("abyssus_form").toFile().also {
        File("../lib-core-editor/src/test/resources/modelimport").copyRecursively(it)
    }
    private val untitled = File("src/test/testData/project/Untitled/assets").list()!!.toSet()
    private val opened = ArrayList<ModelSource>()

    @After
    fun closeSources() = opened.forEach(ModelSource::close)

    private fun form(name: String, placement: PlacementOption = PlacementOption.Disabled(PlacementBlock.NO_SCENE_VIEW)) =
        ModelImportForm(File(fixtures, name), untitled, placement).also { form ->
            form.accept(runCatchingKeepingCancellation { ModelSourceOpener().open(form.sourceFile).also(opened::add) })
        }

    @Test
    fun aTakenFolderNameDisablesCreate() {
        val form = form("crate.obj")
        assertEquals("model_crate", form.folderName)
        assertTrue(form.createEnabled)
        form.folderName = "tree"
        assertEquals(listOf(FormProblem.FOLDER_TAKEN), form.problems())
        assertFalse(form.createEnabled)
    }

    @Test
    fun aSizeOfZeroDisablesCreate() {
        val form = form("crate.obj")
        form.fitMode = FitMode.HEIGHT
        form.sizeText = "0"
        assertEquals(listOf(FormProblem.SIZE_NOT_POSITIVE), form.problems())
        form.sizeText = "1.8"
        assertTrue(form.createEnabled)
    }

    @Test
    fun fbxValuesAreMarkedAsReadFromTheFile() {
        val form = form("rig.fbx")
        assertEquals(LengthUnit.CM, form.unit)
        assertEquals(UpAxis.Z, form.upAxis)
        assertEquals(ValueSource.FILE, form.unitSource)
        assertEquals(ValueSource.FILE, form.upSource)
        assertEquals("Idle", form.animation)
        assertEquals(listOf("Idle", "Run"), form.animations.map { it.name })
        form.unit = LengthUnit.M
        assertEquals(ValueSource.CHOSEN, form.unitSource)
    }

    @Test
    fun objValuesAreDefaultsAndGlbValuesComeFromTheFormat() {
        val obj = form("crate.obj")
        assertEquals(ValueSource.DEFAULT, obj.unitSource)
        assertEquals(ValueSource.DEFAULT, obj.upSource)
        val glb = form("crate.glb")
        assertEquals(ValueSource.FORMAT, glb.unitSource)
        assertEquals(ValueSource.FORMAT, glb.upSource)
    }

    @Test
    fun aDaeStatingXUpShowsYWithANote() {
        val form = form("crate_xup.dae")
        assertEquals(UpAxis.Y, form.upAxis)
        assertTrue(form.statesXUp)
        assertEquals(ValueSource.FILE, form.unitSource)
    }

    @Test
    fun anUnreadableSourceDisablesCreateAndShowsItsReason() {
        val form = ModelImportForm(File(fixtures, "broken.fbx"), untitled, PlacementOption.Disabled(PlacementBlock.NO_SCENE_VIEW))
        assertEquals(listOf(FormProblem.SOURCE_LOADING), form.problems())
        form.accept(Result.failure(ModelImportException("Error loading model: truncated file")))
        assertEquals(listOf(FormProblem.SOURCE_UNREADABLE), form.problems())
        assertEquals("Error loading model: truncated file", form.sourceError)
        assertFalse(form.createEnabled)
        assertEquals(null, form.preview())
    }

    @Test
    fun placementIsOnByDefaultWhenAvailable() {
        val form = form("crate.obj", PlacementOption.Available("Main Scene.scene"))
        assertTrue(form.addToScene)
        form.addToScene = false
        assertFalse(form.addToScene)
    }

    @Test
    fun eachDisabledPlacementKeepsAddToSceneOff() {
        for (reason in PlacementBlock.entries) {
            val form = form("crate.obj", PlacementOption.Disabled(reason))
            assertFalse(reason.name, form.addToScene)
            form.addToScene = true
            assertFalse(reason.name, form.addToScene)
            assertTrue("Create still writes the folder", form.createEnabled)
        }
    }

    @Test
    fun settingsChangeReusesSource() {
        var opens = 0
        val form = ModelImportForm(File(fixtures, "crate.obj"), untitled, PlacementOption.Disabled(PlacementBlock.NO_SCENE_VIEW))
        form.accept(runCatchingKeepingCancellation { opens++; ModelSourceOpener().open(form.sourceFile).also(opened::add) })
        val heights = ArrayList<Float>()
        for (unit in listOf(LengthUnit.CM, LengthUnit.MM, LengthUnit.M)) {
            form.unit = unit
            heights += form.preview()!!.size.y
        }
        assertEquals(1, opens)
        assertEquals(listOf(1f, 0.1f, 100f).map { "%.4f".format(it) }, heights.map { "%.4f".format(it) })
    }
}
