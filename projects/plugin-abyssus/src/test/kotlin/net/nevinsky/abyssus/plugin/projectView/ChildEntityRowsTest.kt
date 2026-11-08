/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import net.nevinsky.abyssus.lib.core.editor.parseScene

/** OpenSpec `abyssus-project-view`, "Child entities are listed under their parent". */
class ChildEntityRowsTest : BasePlatformTestCase() {
    private val untitled
        get() = parseScene(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText())

    private fun entry(name: String, value: Any?, vararg keys: String) =
        DtoEntry("/p/$name", name, value, null, null, null, keys.toList())

    fun testTheCameraHandleIsTheFirstChildOfCameraFour() {
        val scene = untitled
        assertFalse("3" in ecsRows(scene).map { it.name })
        val camera = EntityNode(scene, 4)
        assertEquals("3", entityRows(camera).first().name)
        assertEquals(listOf("3"), childEntityRows(camera).map { it.name })
        assertEquals(RowText("Camera 4", "6 components, 1 children"), rowText(entry("4", camera, "ecs")))
        assertEquals("11 entities", rowText(entry("ecs", EcsNode(scene))).secondary)
    }

    fun testChildRowsComeBeforeComponentRows() {
        val scene = parseScene(
            """{"formatVersion":1,"ecs":{
              "0":{"type":"GROUP","components":{"NameComponent":{"name":"G"}}},
              "2":{"type":"DUMMY","components":{"NameComponent":{"name":"B"},"ParentComponent":{"parentEntityId":0}}},
              "1":{"type":"DUMMY","components":{"NameComponent":{"name":"A"},"ParentComponent":{"parentEntityId":0}}}
            }}"""
        )
        assertEquals(listOf("1", "2", "NameComponent"), entityRows(EntityNode(scene, 0)).map { it.name })
        assertEquals(listOf("0"), ecsRows(scene).map { it.name })
    }

    fun testABrokenLinkStaysAtTheRoot() {
        val scene = parseScene(
            """{"formatVersion":1,"ecs":{
              "0":{"components":{}},
              "1":{"type":"DUMMY","components":{"ParentComponent":{"parentEntityId":99}}},
              "2":{"type":"DUMMY","components":{"ParentComponent":{"parentEntityId":2}}}
            }}"""
        )
        assertEquals(listOf("0", "1", "2"), ecsRows(scene).map { it.name })
    }

    fun testADummyUnderAModelIsListedUnderIt() {
        val scene = untitled
        scene.ecs[20L] = net.nevinsky.abyssus.lib.core.editor.entityOf(
            20,
            net.nevinsky.abyssus.lib.core.format.EntityType.DUMMY,
            net.nevinsky.abyssus.lib.core.ecs.component.NameComponent("Dummy"),
            net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent(9),
        )
        assertFalse("20" in ecsRows(scene).map { it.name })
        assertEquals(listOf("20"), childEntityRows(EntityNode(scene, 9)).map { it.name })
    }
}
