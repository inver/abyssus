/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Vec3

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.editor.document.SceneJson
import com.intellij.openapi.actionSystem.Separator
import net.nevinsky.abyssus.projectView.AddAssetGroup
import net.nevinsky.abyssus.projectView.AddLightGroup
import net.nevinsky.abyssus.projectView.canAddAsset
import net.nevinsky.abyssus.projectView.canAddLight
import net.nevinsky.abyssus.projectView.hasRenderAssets
import java.awt.Component
import java.awt.Container
import javax.swing.JButton

class SceneViewPanelTest : BasePlatformTestCase() {
    private fun named(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    fun testToolbarPlacesLightAtOrbitTargetAndDisablesForUnreadableScene() {
        val file = myFixture.addFileToProject("Lights.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""").virtualFile
        val params = SceneRenderParams.DEFAULT.copy(camera = CameraParams.DEFAULT.copy(position = Vec3(10f, 0f, 6f), direction = Vec3(0f, 0f, -1f)))
        var selected: String? = null
        val panel = SceneViewPanel(params, testRenderer(),
            lightActions = { position -> AddLightGroup(project, file, position, { selected = it }) },
            canAddLight = { canAddLight(file, net.nevinsky.abyssus.dto.SceneDocumentCache.of(project)) })
        try {
            val button = named(panel, "add-light") as JButton
            assertTrue(button.isEnabled)
            val choices = panel.lightChoices()!!.getChildren(null)
            assertEquals(listOf("Directional", "Sun", "Spot"), choices.map { it.templatePresentation.text })
            choices[1].actionPerformed(TestActionEvent.createTestEvent(choices[1]))
            assertEquals("0", selected)
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            val light = SceneContent.of(net.nevinsky.abyssus.parseScene(document.text)).lights.single()
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
        val documents = net.nevinsky.abyssus.dto.SceneDocumentCache.of(project)
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
}
