/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.editor.content.Vec3

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import com.intellij.openapi.actionSystem.Separator
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
        val params = SceneRenderParams.DEFAULT.copy(camera = CameraParams.DEFAULT.copy(position = Vec3(10f, 0f, 6f), direction = Vec3(0f, 0f, -1f)))
        var selected: String? = null
        val panel = SceneViewPanel(params, testRenderer(),
            lightActions = { position -> AddLightGroup(project, file, position, { selected = it }) },
            canAddLight = { canAddLight(file, net.nevinsky.abyssus.plugin.dto.SceneDocumentCache.of(project)) })
        try {
            val button = named(panel, "add-light") as JButton
            assertTrue(button.isEnabled)
            val choices = panel.lightChoices()!!.getChildren(null)
            assertEquals(listOf("Directional", "Sun", "Spot"), choices.map { it.templatePresentation.text })
            choices[1].actionPerformed(TestActionEvent.createTestEvent(choices[1]))
            assertEquals("0", selected)
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            val light = sceneContentOf(net.nevinsky.abyssus.lib.core.editor.parseScene(document.text)).lights.single()
            assertEquals(Vec3(10f, 0f, -4f), light.position)
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
        val params = SceneRenderParams.DEFAULT.copy(camera = CameraParams.DEFAULT.copy(position = Vec3(10f, 0f, 6f), direction = Vec3(0f, 0f, -1f)))
        var selected: String? = null
        val documents = net.nevinsky.abyssus.plugin.dto.SceneDocumentCache.of(project)
        val panel = SceneViewPanel(params, testRenderer(),
            assetActions = { position -> AddAssetGroup(project, file, position, { selected = it }) },
            canAddAsset = { canAddAsset(file, documents) && hasRenderAssets(file) })
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
                Vec3(it.path("x").floatValue(), it.path("y").floatValue(), it.path("z").floatValue())
            }
            assertEquals(Vec3(10f, 0f, -4f), at("0"))
            assertEquals(Vec3(-40f, 0f, -54f), at("1")) // the 100-unit terrain is centred on the orbit target
            WriteCommandAction.runWriteCommandAction(project) { document.setText("not json") }
            panel.setParams(params)
            assertFalse(button.isEnabled)
            assertNull(panel.assetChoices())
        } finally { panel.dispose() }
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
            assertEquals(net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique.LAYERED, firstRenderer.state.cloudTechnique)
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
            assertEquals(net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique.SHELLS, renderer.state.cloudTechnique)

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
}
