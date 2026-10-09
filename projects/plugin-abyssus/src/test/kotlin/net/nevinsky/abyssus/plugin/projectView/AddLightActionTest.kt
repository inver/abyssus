/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3
import java.util.concurrent.TimeUnit

class AddLightActionTest : BasePlatformTestCase() {
    fun testPresetUpdatesRunOnABackgroundThread() {
        val file = myFixture.addFileToProject("Main.scene", """{"format":"abyssus","formatVersion":1,"ecs":{}}""").virtualFile
        val context = SimpleDataContext.getProjectContext(project)
        val group = AddLightGroup(project, file, { Vec3(0f, 0f, 0f) })
        val actions = group.getChildren(null)
        assertEquals(3, actions.size)
        for (action in actions) {
            assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
            val enabled = AppExecutorUtil.getAppExecutorService().submit<Boolean> {
                ReadAction.compute<Boolean, RuntimeException> {
                    val event = AnActionEvent.createFromDataContext("test", null, context)
                    action.update(event)
                    event.presentation.isEnabled
                }
            }.get(10, TimeUnit.SECONDS)
            assertTrue(enabled)
        }
    }
}
