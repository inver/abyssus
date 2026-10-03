/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.sceneview.SceneTransformWriter
import net.nevinsky.abyssus.sceneview.TransformEdit
import net.nevinsky.abyssus.sceneview.Vec3
import java.io.File

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
        assertEquals(SceneJson.parse(original).get("ecs").get("entities").size(), SceneJson.parse(after).get("ecs").get("entities").size())
    }

    fun testNothingIsWrittenWhenTheEditChangesNothing() {
        val file = myFixture.addFileToProject("p/Same.scene", original).virtualFile
        assertFalse(editSceneJson(project, file, "Move Entity") { root ->
            SceneTransformWriter.apply(root, "0", TransformEdit(position = Vec3(-3.035308f, 0.9123962f, -3.2570944f)))
        })
        assertEquals(original, String(file.contentsToByteArray()))
    }
}
