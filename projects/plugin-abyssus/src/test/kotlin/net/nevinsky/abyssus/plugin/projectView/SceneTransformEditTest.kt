/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.badlogic.gdx.math.Vector3
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import net.nevinsky.abyssus.lib.core.editor.pick.SceneTransformWriter
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.plugin.filetype.editSceneDocument

class SceneTransformEditTest : BasePlatformTestCase() {
    private val original
        get() =
            net.nevinsky.abyssus.plugin.prettyScene(
                File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()
            )

    fun testMoveKeepsIndentationAndEveryOtherLine() {
        val file = myFixture.addFileToProject("p/Main Scene.scene", original).virtualFile
        val moved =
            editSceneDocument(project, file, "Move Entity") { root ->
                SceneTransformWriter()
                    .apply(root, 0L, TransformEdit(position = Vector3(-1.035308f, 0.9123962f, -3.2570944f)))
            }
        assertTrue(moved)
        val after = String(file.contentsToByteArray())
        val before = original.lines()
        val now = after.lines()
        assertEquals(before.size, now.size)
        val changed = before.indices.filter { before[it] != now[it] }
        assertEquals(changed.toString(), 1, changed.size)
        assertTrue(
            now[changed.single()],
            now[changed.single()].startsWith("            \"x\": -1.035308") ||
                now[changed.single()].contains("\"x\": -1.035"),
        )
        assertEquals(
            net.nevinsky.abyssus.plugin.parsedScene(original).ecs.size,
            net.nevinsky.abyssus.plugin.parsedScene(after).ecs.size,
        )
    }

    fun testNothingIsWrittenWhenTheEditChangesNothing() {
        val file = myFixture.addFileToProject("p/Same.scene", original).virtualFile
        assertFalse(
            editSceneDocument(project, file, "Move Entity") { root ->
                SceneTransformWriter()
                    .apply(root, 0L, TransformEdit(position = Vector3(-3.035308f, 0.9123962f, -3.2570944f)))
            }
        )
        assertEquals(original, String(file.contentsToByteArray()))
    }

    private fun eventsDuring(action: () -> Unit): List<com.intellij.openapi.vfs.VirtualFile> {
        val events = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(
            net.nevinsky.abyssus.plugin.filetype.AbyssusSceneEdited.TOPIC,
            net.nevinsky.abyssus.plugin.filetype.AbyssusSceneEdited { events += it },
        )
        action()
        connection.disconnect()
        return events
    }

    fun testAWritePublishesExactlyOneEventForTheFile() {
        val file = myFixture.addFileToProject("p/Event.scene", original).virtualFile
        val events = eventsDuring {
            assertTrue(
                editSceneDocument(project, file, "Move Entity") { root ->
                    SceneTransformWriter()
                        .apply(root, 0L, TransformEdit(position = Vector3(1f, 0.9123962f, -3.2570944f)))
                }
            )
        }
        assertEquals(listOf(file), events)
    }

    fun testNoEventWhenNothingIsWritten() {
        val file = myFixture.addFileToProject("p/NoEvent.scene", original).virtualFile
        val events = eventsDuring { assertFalse(editSceneDocument(project, file, "Nothing") { false }) }
        assertTrue(events.isEmpty())
    }
}
