/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import java.io.File
import java.nio.file.Files
import javax.swing.JButton
import javax.swing.JTextField
import net.nevinsky.abyssus.plugin.projectView.PartTarget

/** OpenSpec `object-properties-panel`, "Part properties", over a project on the disk (the models are read by Assimp). */
class PartDetailsViewTest : BasePlatformTestCase() {
    private val dirs = mutableListOf<File>()

    override fun tearDown() {
        try {
            dirs.forEach(File::deleteRecursively)
        } finally {
            super.tearDown()
        }
    }

    private fun byName(root: Component, name: String): Component? =
        if (root.name == name) {
            root
        } else {
            (root as? Container)?.components?.firstNotNullOfOrNull { byName(it, name) }
        }

    private fun file(io: File): VirtualFile = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(io))

    /** A project folder with the scene text [scene] and the model asset folder [source] as `assets/[asset]`. */
    private fun project(source: File, asset: String, scene: String): Pair<VirtualFile, File> {
        val dir = Files.createTempDirectory("part-view").toFile().also(dirs::add)
        File(dir, "P.abss").writeText("""{"format":"abyssus","formatVersion":1,"name":"P"}""")
        File(dir, "scenes").mkdirs()
        File(dir, "scenes/S.scene").writeText(scene)
        assertTrue(source.copyRecursively(File(dir, "assets/$asset")))
        return file(File(dir, "scenes/S.scene")) to dir
    }

    private fun sceneWith(entity: Int, name: String, asset: String) =
        """{"formatVersion":1,"name":"S","ecs":{"$entity":{"type":"OBJECT","components":{
            "NameComponent":{"name":"$name"},"PositionComponent":{},
            "RenderComponent":{"shaderKey":"defaultShader","type":"MODEL","assetName":"$asset"}}}}}"""

    private fun view(scene: VirtualFile, entity: Long, asset: String, node: String): PartDetailsView {
        val state = readPartState(PartTarget(scene, entity, node, asset, null)) as PanelState.PartDetails
        return PartDetailsView(project, state)
    }

    fun testAFlapWithoutDataShowsAZeroPivotAndAddHinge() {
        val asset = "model_900f6f61-6384-434a-be81-56ce303fbb56"
        val (scene, _) = project(File("src/test/testData/project/Untitled/assets/$asset"), asset, sceneWith(6, "Model 6", asset))
        val view = view(scene, 6, asset, "flap_L01_Cube.012")
        val breadcrumb = (byName(view, "part-breadcrumb") as javax.swing.JLabel).text
        assertTrue(breadcrumb, breadcrumb.contains("S") && breadcrumb.contains("Model 6") && breadcrumb.contains("flap_L01_Cube.012"))
        assertEquals(asset, (byName(view, "part-asset") as javax.swing.JLabel).text)
        assertEquals("0", (byName(view, "part-pivot-position-x") as JTextField).text)
        assertNotNull(byName(view, "part-hinge-add"))
        assertNull(byName(view, "part-hinge-min"))
    }

    fun testAddHingeWritesThirtyDegreesAndUndoShowsAddHingeAgain() {
        val asset = "model_trainer"
        val (scene, dir) = project(
            File("../app-game-control-line/project/ControlLine/assets/model_trainer"), asset,
            sceneWith(5, "Trainer", asset),
        )
        val meta = file(File(dir, "assets/$asset/meta.json"))
        myFixture.openFileInEditor(meta)
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)

        (byName(view(scene, 5, asset, "RightFlap"), "part-hinge-add") as JButton).doClick()
        val text = FileDocumentManager.getInstance().getDocument(meta)!!.text
        assertTrue(text, Regex("\"RightFlap\"").containsMatchIn(text))
        val shown = view(scene, 5, asset, "RightFlap")
        assertEquals("-30", (byName(shown, "part-hinge-min") as JTextField).text)
        assertEquals("30", (byName(shown, "part-hinge-max") as JTextField).text)

        UndoManager.getInstance(project).undo(editor)
        assertNotNull(byName(view(scene, 5, asset, "RightFlap"), "part-hinge-add"))
    }

    fun testAFlapThatTheModelAnimatesShowsWhyNoPivotCanBeApplied() {
        val asset = "model_trainer"
        val (scene, _) = project(
            File("../app-game-control-line/project/ControlLine/assets/model_trainer"), asset,
            sceneWith(5, "Trainer", asset),
        )
        val view = view(scene, 5, asset, "LeftFlap")
        assertNotNull(byName(view, "part-pivot-refused"))
        assertNull(byName(view, "part-pivot-position-x"))
    }

    fun testHingeLimitsTheWrongWayRoundShowAnErrorAndWriteNothing() {
        val asset = "model_trainer"
        val (scene, dir) = project(
            File("../app-game-control-line/project/ControlLine/assets/model_trainer"), asset,
            sceneWith(5, "Trainer", asset),
        )
        val meta = file(File(dir, "assets/$asset/meta.json"))
        (byName(view(scene, 5, asset, "LeftFlap"), "part-hinge-add") as JButton).doClick()
        val withHinge = FileDocumentManager.getInstance().getDocument(meta)!!.text

        val view = view(scene, 5, asset, "LeftFlap")
        (byName(view, "part-hinge-min") as JTextField).apply {
            text = "10"
            postActionEvent()
        }
        assertTrue((byName(view, "part-hinge-error") as javax.swing.JLabel).isVisible)
        assertEquals(withHinge, FileDocumentManager.getInstance().getDocument(meta)!!.text)
    }
}
