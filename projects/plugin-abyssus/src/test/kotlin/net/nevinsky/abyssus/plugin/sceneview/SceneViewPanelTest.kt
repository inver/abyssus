/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.testMetaFiles
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import com.badlogic.gdx.math.Vector3

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.plugin.projectView.AddAssetGroup
import net.nevinsky.abyssus.plugin.projectView.AddLightGroup
import net.nevinsky.abyssus.plugin.projectView.canAddAsset
import net.nevinsky.abyssus.plugin.projectView.canAddLight
import net.nevinsky.abyssus.plugin.projectView.hasRenderAssets
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import net.nevinsky.abyssus.plugin.AbyssusBundle

class SceneViewPanelTest : BasePlatformTestCase() {
    private fun named(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    fun testToolbarPlacesLightAtOrbitTargetAndDisablesForUnreadableScene() {
        val file = myFixture.addFileToProject("Lights.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""").virtualFile
        val params = SceneRenderParams.DEFAULT.copy(camera = CameraParams.DEFAULT.copy(position = Vector3(10f, 0f, 6f), direction = Vector3(0f, 0f, -1f)))
        var selected: String? = null
        val panel = SceneViewPanel(params, testRenderer(),
            lightMenu = PlacementMenu({ canAddLight(file, net.nevinsky.abyssus.plugin.dto.SceneDocumentCache.of(project)) }) { position ->
                AddLightGroup(project, file, position, { selected = it })
            })
        try {
            val button = named(panel, "add-light") as JButton
            assertTrue(button.isEnabled)
            val choices = panel.lightChoices()!!.getChildren(null)
            assertEquals(listOf("Directional", "Sun", "Spot"), choices.map { it.templatePresentation.text })
            choices[1].actionPerformed(TestActionEvent.createTestEvent(choices[1]))
            assertEquals("0", selected)
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            val light = sceneContentOf(net.nevinsky.abyssus.lib.core.editor.parseScene(document.text)).lights.single()
            assertEquals(Vector3(10f, 0f, -4f), light.position)
            WriteCommandAction.runWriteCommandAction(project) { document.setText("not json") }
            panel.setParams(params)
            assertFalse(button.isEnabled)
            assertNull(panel.lightChoices())
        } finally { panel.dispose() }
    }

    fun testToolbarPlacesAssetsAtOrbitTargetAndDisablesForUnreadableScene() {
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        myFixture.addFileToProject("p/assets/tree/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"6a1b8d52-9f3e-4c71-8b0d-2e5f7a9c1d34","type":"MODEL","additional":{"file":"model.glb"}}""")
        myFixture.addFileToProject("p/assets/hills/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"0c9e7f21-3b4a-4d85-a6f1-7e2d9b8c5a10","type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":100}}""")
        myFixture.addFileToProject("p/assets/sky/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"9d2c4b6e-1f8a-4e37-b5c0-3a7f6e1d2b98","type":"SKYBOX","additional":{}}""")
        val file = myFixture.addFileToProject("p/scenes/S.scene", """{"format":"abyssus","formatVersion":1,"ecs":{}}""").virtualFile
        val params = SceneRenderParams.DEFAULT.copy(camera = CameraParams.DEFAULT.copy(position = Vector3(10f, 0f, 6f), direction = Vector3(0f, 0f, -1f)))
        var selected: String? = null
        val documents = net.nevinsky.abyssus.plugin.dto.SceneDocumentCache.of(project)
        val panel = SceneViewPanel(params, testRenderer(),
            assetMenu = PlacementMenu({ canAddAsset(file, documents) && hasRenderAssets(file, testMetaFiles()) }) { position ->
                AddAssetGroup(project, file, testMetaFiles(), position, { selected = it })
            })
        try {
            val button = named(panel, "add-asset") as JButton
            assertTrue(button.isEnabled)
            val choices = panel.assetChoices()!!.getChildren(null).filter { it !is Separator }
            assertEquals(listOf("tree", "hills"), choices.map { it.templatePresentation.text })
            choices[0].actionPerformed(TestActionEvent.createTestEvent(choices[0]))
            assertEquals("0", selected)
            choices[1].actionPerformed(TestActionEvent.createTestEvent(choices[1]))
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            val ecs = SceneJson().parse(document.text)["ecs"]
            fun at(id: String) = ecs[id]["components"]["PositionComponent"]["localPosition"].let {
                Vector3(it.path("x").floatValue(), it.path("y").floatValue(), it.path("z").floatValue())
            }
            assertEquals(Vector3(10f, 0f, -4f), at("0"))
            assertEquals(Vector3(-40f, 0f, -54f), at("1")) // the 100-unit terrain is centred on the orbit target
            WriteCommandAction.runWriteCommandAction(project) { document.setText("not json") }
            panel.setParams(params)
            assertFalse(button.isEnabled)
            assertNull(panel.assetChoices())
        } finally { panel.dispose() }
    }

    /** A copy of the Untitled project on disk, whose Main Scene the arrangement tests write to. */
    private fun untitled(): com.intellij.openapi.vfs.VirtualFile {
        val dir = com.intellij.openapi.util.io.FileUtil.createTempDirectory("abyssus-panel-path", null)
        com.intellij.openapi.util.io.FileUtil.copyDir(java.io.File("src/test/testData/project/Untitled"), dir)
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(java.io.File(dir, "scenes/Main Scene.scene"))!!
    }

    fun testToolbarOffersTheProjectsModelsAndStartsDrawing() {
        val file = untitled()
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer(),
            pathMenu = PlacementMenu({ net.nevinsky.abyssus.plugin.projectView.canAddPathArrangement(file, net.nevinsky.abyssus.plugin.dto.SceneDocumentCache.of(project)) }) { start ->
                net.nevinsky.abyssus.plugin.projectView.AddPathArrangementGroup(project, file, start)
            })
        try {
            val button = named(panel, "add-path-arrangement") as JButton
            assertTrue(button.isEnabled)
            val hint = named(panel, "path-drawing-hint") as javax.swing.JLabel
            assertFalse(hint.isVisible)
            val tree = panel.pathChoices()!!.getChildren(null).first { it.templatePresentation.text == "tree" }
            tree.actionPerformed(TestActionEvent.createTestEvent(tree))
            assertTrue("drawing mode shows its hint", hint.isVisible)
        } finally { panel.dispose() }
    }

    fun testWithoutPathActionsThereIsNoPathButton() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        try {
            assertNull(named(panel, "add-path-arrangement"))
        } finally { panel.dispose() }
    }

    fun testAFinishedDrawingWritesOneUndoableCommandAndSelectsTheArrangement() {
        val file = untitled()
        myFixture.openFileInEditor(file)
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val renderer = testRenderer()
        lateinit var panel: SceneViewPanel
        val editor = newSceneEditor(project, file) { params -> SceneViewPanel(params, renderer).also { panel = it } }
        try {
            val before = document.text
            val points = listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f))
            assertTrue(panel.onArrangementCreate!!(net.nevinsky.abyssus.lib.core.editor.content.RenderAsset("MODEL", "tree"), points, false))
            val ecs = SceneJson().parse(document.text)["ecs"]
            assertEquals("Path 11", ecs["11"]["components"]["NameComponent"]["name"].asText())
            assertEquals(5, ecs["11"]["components"]["PathArrangementComponent"]["slots"].size())
            assertEquals("11", renderer.state.selectedId)
            com.intellij.openapi.command.undo.UndoManager.getInstance(project)
                .undo(com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
            assertEquals(before, document.text)
        } finally { editor.dispose() }
    }

    fun testADraggedPointIsWrittenWithTheRegeneratedCopies() {
        val file = untitled()
        val renderer = testRenderer()
        lateinit var panel: SceneViewPanel
        val editor = newSceneEditor(project, file) { params -> SceneViewPanel(params, renderer).also { panel = it } }
        try {
            panel.onArrangementCreate!!(net.nevinsky.abyssus.lib.core.editor.content.RenderAsset("MODEL", "tree"), listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f)), false)
            assertTrue(panel.onArrangementEdit!!("11", listOf(Vector3(0f, 0f, 0f), Vector3(60f, 0f, 0f))))
            val slots = SceneJson().parse(FileDocumentManager.getInstance().getDocument(file)!!.text)["ecs"]["11"]["components"]["PathArrangementComponent"]["slots"]
            assertEquals(7, slots.size())
        } finally { editor.dispose() }
    }

    fun testWithoutAssetActionsThereIsNoAddAssetButton() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        try {
            assertNull(named(panel, "add-asset"))
        } finally { panel.dispose() }
    }

    /** A copy of the fixture's skies on disk, with `skybox_cloudy`: `skybox_physical` naming the cloud asset `clouds_fair`. */
    private fun skies(): java.io.File {
        val dir = java.nio.file.Files.createTempDirectory("clouds").toFile()
        val assets = java.io.File("src/test/testData/project/Untitled/assets")
        for (name in listOf("skybox_default", "skybox_physical")) assets.resolve(name).copyRecursively(dir.resolve("assets/$name"))
        assets.resolve("skybox_physical").copyRecursively(dir.resolve("assets/skybox_cloudy"))
        val meta = dir.resolve("assets/skybox_cloudy/meta.json")
        meta.writeText(meta.readText().replace("\"sunIntensity\": 20.0", "\"sunIntensity\": 20.0,\n    \"clouds\": \"3f2a9c1e-7b4d-4e8a-9c6f-1d2e3b4a5c6d\""))
        dir.resolve("assets/clouds_fair").mkdirs()
        dir.resolve("assets/clouds_fair/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"uuid":"3f2a9c1e-7b4d-4e8a-9c6f-1d2e3b4a5c6d","type":"CLOUDS","additional":{"low":{"type":"cumulus"}}}"""
        )
        return dir
    }

    private fun skyParams(dir: java.io.File, sky: String?) =
        SceneRenderParams.DEFAULT.copy(content = SceneRenderParams.DEFAULT.content.copy(skybox = sky), projectDir = dir)

    private fun clouds(panel: SceneViewPanel) = named(panel, "clouds") as javax.swing.JComboBox<*>

    private fun choose(panel: SceneViewPanel, choice: net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice) {
        val combo = clouds(panel)
        combo.selectedItem = (0 until combo.itemCount).map(combo::getItemAt).first { (it as CloudChoiceItem).choice == choice }
    }

    private fun snapshot(dir: java.io.File) = dir.walkTopDown().filter { it.isFile }.associate { it.path to it.readBytes().toList() }

    fun testCloudsChoiceDefaultsToAssetAndIsPerView() {
        val dir = skies()
        val before = snapshot(dir)
        val firstRenderer = testRenderer()
        val secondRenderer = testRenderer()
        val first = SceneViewPanel(skyParams(dir, "skybox_cloudy"), firstRenderer)
        val second = SceneViewPanel(skyParams(dir, "skybox_cloudy"), secondRenderer)
        val renderers = mapOf(first to firstRenderer, second to secondRenderer)
        try {
            for (panel in listOf(first, second)) {
                assertTrue(clouds(panel).isEnabled)
                assertEquals(net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice.ASSET, (clouds(panel).selectedItem as CloudChoiceItem).choice)
                assertNull(renderers.getValue(panel).state.cloudTechnique)
            }
            choose(first, net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice.LAYERED)
            assertEquals(CloudTechnique.LAYERED, firstRenderer.state.cloudTechnique)
            assertNull("the other view keeps its own choice", secondRenderer.state.cloudTechnique)
            assertEquals(net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice.ASSET, second.cloudState.choice)
        } finally {
            first.dispose()
            second.dispose()
        }
        val reopened = SceneViewPanel(skyParams(dir, "skybox_cloudy"), testRenderer())
        try {
            assertEquals("a reopened view starts at Asset", net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice.ASSET, reopened.cloudState.choice)
        } finally {
            reopened.dispose()
        }
        assertEquals("no file changes", before, snapshot(dir))
        dir.deleteRecursively()
    }

    fun testCloudsChoiceIsDisabledWithoutEnabledClouds() {
        val dir = skies()
        for (sky in listOf("skybox_default", "skybox_physical", null)) {
            val panel = SceneViewPanel(skyParams(dir, sky), testRenderer())
            try {
                assertFalse("$sky", clouds(panel).isEnabled)
            } finally {
                panel.dispose()
            }
        }
        val panel = SceneViewPanel(skyParams(dir, "skybox_default"), testRenderer())
        try {
            panel.setParams(skyParams(dir, "skybox_cloudy"))
            assertTrue("switching to a cloudy sky enables the choice", clouds(panel).isEnabled)
            panel.setParams(skyParams(dir, "skybox_physical"))
            assertFalse(clouds(panel).isEnabled)
        } finally {
            panel.dispose()
        }
        dir.deleteRecursively()
    }

    fun testSlowVolumetricCloudsFallBackToShells() {
        val dir = skies()
        val before = snapshot(dir)
        val renderer = testRenderer()
        val panel = SceneViewPanel(skyParams(dir, "skybox_cloudy"), renderer)
        try {
            val note = named(panel, "clouds-note") as javax.swing.JLabel
            assertFalse(note.isVisible)
            choose(panel, net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice.VOLUMETRIC)
            repeat(41) { panel.cloudFrameRendered(0.05f, volumetric = true) } // 20 frames per second for two seconds
            assertTrue(note.isVisible)
            assertEquals(AbyssusBundle.message("sceneViewCloudsFallback"), note.text)
            assertEquals(net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice.SHELLS, (clouds(panel).selectedItem as CloudChoiceItem).choice)
            assertEquals(CloudTechnique.SHELLS, renderer.state.cloudTechnique)

            choose(panel, net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice.VOLUMETRIC)
            assertFalse("choosing again clears the note", note.isVisible)
            repeat(41) { panel.cloudFrameRendered(0.05f, volumetric = true) }
            assertTrue(panel.cloudState.sticky)
            choose(panel, net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice.VOLUMETRIC)
            assertEquals("volumetric is refused after the second fallback",
                net.nevinsky.abyssus.plugin.sceneview.skybox.CloudChoice.SHELLS, (clouds(panel).selectedItem as CloudChoiceItem).choice)
            assertEquals(AbyssusBundle.message("sceneViewCloudsStickyTooltip"), clouds(panel).toolTipText)
        } finally {
            panel.dispose()
        }
        assertEquals("no file changes", before, snapshot(dir))
        dir.deleteRecursively()
    }

    private fun toolbarOf(panel: SceneViewPanel) = named(panel, "scene-toolbar") as javax.swing.JPanel

    private fun actionsOf(panel: SceneViewPanel, place: String) =
        (named(panel, place) as com.intellij.openapi.actionSystem.ActionToolbar).actions

    fun testToolbarGroupsAppearInTheMockupsOrderWithDividers() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        try {
            val names = toolbarOf(panel).components.map {
                when {
                    it.name != null -> it.name
                    it is javax.swing.JComboBox<*> -> "camera"
                    else -> it.javaClass.simpleName
                }
            }
            val firstRow = names.take(9)
            assertEquals(
                listOf("AbyssusSceneTools", SceneToolsToolbar.DIVIDER_NAME, "AbyssusSceneSpace", SceneToolsToolbar.DIVIDER_NAME,
                    "AbyssusSceneDisplay", SceneToolsToolbar.DIVIDER_NAME, "camera", "AbyssusSceneFrame", SceneToolsToolbar.DIVIDER_NAME),
                firstRow,
            )
            assertTrue("existing actions follow", names.drop(9).containsAll(listOf("add-light")))
        } finally { panel.dispose() }
    }

    fun testToolTooltipsNameTheirKeysAndMoveIsPressedFirst() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        try {
            val tools = actionsOf(panel, "AbyssusSceneTools")
            assertEquals(listOf("Select (Q)", "Move (W)", "Rotate (E)"), tools.map { it.templatePresentation.text })
            val toggles = tools.map { it as com.intellij.openapi.actionSystem.ToggleAction }
            fun pressed() = toggles.map { it.isSelected(TestActionEvent.createTestEvent(it)) }
            assertEquals(listOf(false, true, false), pressed())
            toggles[2].setSelected(TestActionEvent.createTestEvent(toggles[2]), true)
            assertEquals(listOf(false, false, true), pressed())
            toggles[0].setSelected(TestActionEvent.createTestEvent(toggles[0]), true)
            assertEquals(listOf(true, false, false), pressed())
        } finally { panel.dispose() }
    }

    fun testDisplayTogglesWriteTheModelAndNotAnything_Else() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        try {
            val display = actionsOf(panel, "AbyssusSceneDisplay")
            val grid = display.last() as com.intellij.openapi.actionSystem.ToggleAction
            assertFalse(panel.tools.display.grid)
            grid.setSelected(TestActionEvent.createTestEvent(grid), true)
            assertTrue(panel.tools.display.grid)
            assertTrue(panel.tools.display.shadows && panel.tools.display.fog)
        } finally { panel.dispose() }
    }

    fun testToolbarWrapsInsteadOfOverlappingAtNineHundredPixels() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer(),
            lightMenu = PlacementMenu({ true }) { _ -> DefaultActionGroup() },
            assetMenu = PlacementMenu({ true }) { _ -> DefaultActionGroup() })
        try {
            panel.setSize(900, 600)
            fun layoutAll(c: Component) { (c as? Container)?.let { it.doLayout(); it.components.forEach(::layoutAll) } }
            layoutAll(panel)
            val toolbar = toolbarOf(panel)
            layoutAll(toolbar)
            val boxes = toolbar.components.filter { it.isVisible }.map { it.name to it.bounds }
            assertTrue("all controls fit inside the width", boxes.all { (_, b) -> b.x >= 0 && b.x + b.width <= 900 })
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                assertFalse("${boxes[i].first} overlaps ${boxes[j].first}", boxes[i].second.intersects(boxes[j].second))
            }
            val frame = toolbar.components.first { it.name == "AbyssusSceneFrame" }
            assertTrue("the first row ends with Frame selection or wraps before it", frame.width > 0)
        } finally { panel.dispose() }
    }

    fun testToolStatusFollowsToolSpaceAndSnap() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        try {
            assertEquals("Move \u00b7 World", panel.toolStatusText)
            panel.tools.space = net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace.LOCAL
            panel.tools.snapOn = true
            assertEquals("Move \u00b7 Local \u00b7 Snap 0.5", panel.toolStatusText)
            val rotate = actionsOf(panel, "AbyssusSceneTools")[2] as com.intellij.openapi.actionSystem.ToggleAction
            rotate.setSelected(TestActionEvent.createTestEvent(rotate), true)
            panel.tools.snapOn = false
            panel.tools.space = net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace.WORLD
            assertEquals("Rotate \u00b7 World", panel.toolStatusText)
        } finally { panel.dispose() }
    }

    fun testToolsReachTheViewStateThatDrivesTheGizmo() {
        val renderer = testRenderer()
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer)
        try {
            panel.tools.space = net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace.LOCAL
            panel.tools.snapOn = true
            panel.tools.snapStep = 0.25f
            assertEquals(net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace.LOCAL, renderer.state.gizmoSpace)
            assertEquals(net.nevinsky.abyssus.lib.core.editor.pick.GizmoSnap(0.25f, SNAP_ANGLE_DEGREES), renderer.state.snap)
            panel.tools.snapOn = false
            assertNull(renderer.state.snap)
            panel.tools.setGrid(false)
            panel.tools.setShading(Shading.UNLIT)
            assertEquals(ViewDisplay(grid = false, shading = Shading.UNLIT), renderer.display)
            panel.tools.setShading(Shading.WIREFRAME)
            assertEquals(ViewDisplay(grid = false, shading = Shading.WIREFRAME), renderer.display)
        } finally { panel.dispose() }
    }

    private val mainParams = net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf(
        net.nevinsky.abyssus.lib.core.editor.parseScene(java.io.File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()),
        CameraParams.DEFAULT,
    )

    fun testFrameSelectionLooksAtTheSelectedMarker() {
        val renderer = testRenderer()
        val panel = SceneViewPanel(mainParams, renderer)
        try {
            val camera = mainParams.content.cameras.single()
            renderer.state.selectedId = camera.entityId
            panel.frameSelection()
            assertEquals(camera.position.x, panel.orbit.target.x, 1.5f)
            assertEquals(camera.position.y, panel.orbit.target.y, 1.5f)
            assertEquals(camera.position.z, panel.orbit.target.z, 1.5f)
        } finally { panel.dispose() }
    }

    fun testFrameSelectionDoesNothingWhileLookingThroughASceneCamera() {
        val renderer = testRenderer()
        val panel = SceneViewPanel(mainParams, renderer)
        try {
            renderer.state.selectedId = mainParams.content.cameras.single().entityId
            renderer.state.viewCamera = mainParams.content.cameras.single().entityId
            val before = panel.orbit.target
            panel.frameSelection()
            assertEquals(before, panel.orbit.target)
        } finally { panel.dispose() }
    }

    fun testFrameSelectionWithNothingDrawnAndNothingSelectedDoesNothing() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        try {
            val before = panel.orbit.target
            panel.frameSelection()
            assertEquals(before, panel.orbit.target)
        } finally { panel.dispose() }
    }

    private fun laidOut(panel: SceneViewPanel) {
        panel.setSize(800, 600)
        fun layoutAll(c: Component) { (c as? Container)?.let { it.doLayout(); it.components.forEach(::layoutAll) } }
        layoutAll(panel)
    }

    fun testAPressOnTheOrientationWidgetsXMarkLooksAlongMinusXFromItsSide() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        try {
            laidOut(panel)
            val x = (panel.width - CORNER_MARGIN - OrientationWidget.SIZE / 2 + OrientationWidget.ARM).toInt()
            val y = CORNER_MARGIN + OrientationWidget.SIZE / 2
            assertTrue(panel.pressOnOrientationWidget(x, y))
            assertEquals(Math.PI.toFloat() / 2, panel.orbit.yaw, 1e-4f)
        } finally { panel.dispose() }
    }

    fun testAPressOutsideTheWidgetsDiscGoesToTheScene() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        try {
            laidOut(panel)
            assertFalse(panel.pressOnOrientationWidget(5, 5))
            // inside the widget's rectangle but outside its circle
            assertFalse(panel.pressOnOrientationWidget(panel.width - CORNER_MARGIN - 1, CORNER_MARGIN + 1))
        } finally { panel.dispose() }
    }

    fun testAnUnavailableViewKeepsItsToolbarDisabledAboveTheMessage() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer(),
            lightMenu = PlacementMenu({ true }) { _ -> DefaultActionGroup() })
        try {
            assertTrue(panel.showUnavailable("OpenGL scene is unavailable: no GL"))
            val message = named(panel, "scene-unavailable") as javax.swing.JLabel
            assertEquals("OpenGL scene is unavailable: no GL", message.text)
            assertNotNull("the toolbar stays", named(panel, "scene-toolbar"))
            assertFalse((named(panel, "add-light") as JButton).isEnabled)
            val tools = actionsOf(panel, "AbyssusSceneTools")
            for (action in tools) {
                val event = TestActionEvent.createTestEvent(action)
                action.update(event)
                assertFalse(event.presentation.isEnabled)
            }
            assertEquals("Move \u00b7 World", panel.toolStatusText)
        } finally { panel.dispose() }
    }
}
