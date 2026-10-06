/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.sceneview.SceneTransformWriter
import net.nevinsky.abyssus.sceneview.TransformEdit
import net.nevinsky.abyssus.editor.content.Vec3
import java.io.File
import net.nevinsky.abyssus.filetype.editSceneJson

class SceneTransformEditTest : BasePlatformTestCase() {
    private val original = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    fun testMoveKeepsIndentationAndEveryOtherLine() {
        val file = myFixture.addFileToProject("p/Main Scene.scene", original).virtualFile
        val moved = editSceneJson(project, file, "Move Entity") { root ->
            SceneTransformWriter.apply(root, "0", TransformEdit(position = Vec3(-1.035308f, 0.9123962f, -3.2570944f)))
        }
        assertTrue(moved)
        val after = String(file.contentsToByteArray())
        val before = original.lines()
        val now = after.lines()
        assertEquals(before.size, now.size)
        val changed = before.indices.filter { before[it] != now[it] }
        assertEquals(changed.toString(), 1, changed.size)
        assertTrue(now[changed.single()], now[changed.single()].startsWith("            \"x\": -1.035308") || now[changed.single()].contains("\"x\": -1.035"))
        val paths = net.nevinsky.abyssus.SceneEcsPaths()
        assertEquals(paths.entities(SceneJson.parse(original))!!.size(), paths.entities(SceneJson.parse(after))!!.size())
    }

    fun testNothingIsWrittenWhenTheEditChangesNothing() {
        val file = myFixture.addFileToProject("p/Same.scene", original).virtualFile
        assertFalse(editSceneJson(project, file, "Move Entity") { root ->
            SceneTransformWriter.apply(root, "0", TransformEdit(position = Vec3(-3.035308f, 0.9123962f, -3.2570944f)))
        })
        assertEquals(original, String(file.contentsToByteArray()))
    }

    private fun eventsDuring(action: () -> Unit): List<com.intellij.openapi.vfs.VirtualFile> {
        val events = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(net.nevinsky.abyssus.filetype.AbyssusSceneEdited.TOPIC, net.nevinsky.abyssus.filetype.AbyssusSceneEdited { events += it })
        action()
        connection.disconnect()
        return events
    }

    fun testAWritePublishesExactlyOneEventForTheFile() {
        val file = myFixture.addFileToProject("p/Event.scene", original).virtualFile
        val events = eventsDuring {
            assertTrue(editSceneJson(project, file, "Move Entity") { root ->
                SceneTransformWriter.apply(root, "0", TransformEdit(position = Vec3(1f, 0.9123962f, -3.2570944f)))
            })
        }
        assertEquals(listOf(file), events)
    }

    fun testNoEventWhenNothingIsWritten() {
        val file = myFixture.addFileToProject("p/NoEvent.scene", original).virtualFile
        val events = eventsDuring { assertFalse(editSceneJson(project, file, "Nothing") { false }) }
        assertTrue(events.isEmpty())
    }
}
