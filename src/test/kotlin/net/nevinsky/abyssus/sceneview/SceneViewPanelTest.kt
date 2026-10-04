/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.projectView.AddLightGroup
import net.nevinsky.abyssus.projectView.canAddLight
import java.awt.Component
import java.awt.Container
import javax.swing.JButton

class SceneViewPanelTest : BasePlatformTestCase() {
    private fun named(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    fun testToolbarPlacesLightAtOrbitTargetAndDisablesForUnreadableScene() {
        val file = myFixture.addFileToProject("Lights.scene", """{"ecs":{"entities":{}}}""").virtualFile
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
}
