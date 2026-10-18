/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.plugin.testMetaFiles
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import java.util.concurrent.TimeUnit

class AddChildGroupTest : BasePlatformTestCase() {
    fun testEveryGroupKeepsVisibleChildrenAndDisablesThemForAnUnsupportedScene() {
        myFixture.addFileToProject("P/P.abss", """{"format":"abyssus","formatVersion":1}""")
        myFixture.addFileToProject("P/assets/model_with_underscores/meta.json",
            """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{"file":"model.gltf"}}""")
        val file = myFixture.addFileToProject("P/scenes/Main.scene",
            """{"format":"abyssus","formatVersion":1,"ecs":{}}""").virtualFile
        val position = { Vec3(0f, 0f, 0f) }
        val groups = listOf(AddLightGroup(project, file, position), AddAssetGroup(project, file, testMetaFiles(), position),
            AddPathArrangementGroup(project, file) {}, AddWaterGroup(project, file, position))
        val context = SimpleDataContext.getProjectContext(project)
        fun check(expected: Boolean) {
            for (group in groups) {
                val children = group.getChildren(null).filterNot { it is Separator }
                assertTrue(group.javaClass.simpleName, children.isNotEmpty())
                for (action in children) {
                    val presentation = AppExecutorUtil.getAppExecutorService().submit<com.intellij.openapi.actionSystem.Presentation> {
                        ReadAction.compute<com.intellij.openapi.actionSystem.Presentation, RuntimeException> {
                            val event = AnActionEvent.createFromDataContext("test", null, context)
                            action.update(event)
                            event.presentation
                        }
                    }.get(10, TimeUnit.SECONDS)
                    assertTrue(group.javaClass.simpleName, presentation.isVisible)
                    assertEquals(group.javaClass.simpleName, expected, presentation.isEnabled)
                }
            }
        }
        check(true)
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("""{"format":"abyssus","formatVersion":2,"ecs":{}}""")
        }
        check(false)
    }
}
