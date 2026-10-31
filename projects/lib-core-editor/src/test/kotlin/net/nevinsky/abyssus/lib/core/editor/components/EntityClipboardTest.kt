/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EntityClipboardTest {
    private val messages = ResourceEditorMessages()
    private val clipboard = EntityClipboard(messages)

    private fun scene() = SceneJson().parse(File("src/test/testData/project/Tree/scenes/Main Scene.scene").readText())

    private fun sceneWithTree(): Pair<JsonNode, String> {
        val root = scene()
        val id = AssetEntities(messages).add(root, BaseMeta(MetaType.MODEL, "tree"), Vector3(1f, 2f, 3f)).entityId!!
        return root to id
    }

    private fun position(root: JsonNode, id: String) =
        root["ecs"][id]["components"]["PositionComponent"]["localPosition"].let {
            Vector3(it.path("x").floatValue(), it.path("y").floatValue(), it.path("z").floatValue())
        }

    @Test
    fun aPasteIsANewEntityAtThePointInAnotherScene() {
        val (source, id) = sceneWithTree()
        val text = (clipboard.copy(source, id) as CopyResult.Copied).text
        val target = scene()
        val before = target["ecs"].size()
        val pasted = clipboard.paste(target, text, Vector3(10f, 0f, -4f))
        assertEquals(EditResult.Changed, pasted.result)
        assertEquals(before + 1, target["ecs"].size())
        assertEquals(Vector3(10f, 0f, -4f), position(target, pasted.entityId!!))
        assertEquals("tree", target["ecs"][pasted.entityId]["components"]["RenderComponent"]["assetName"].asText())
        assertEquals(Vector3(1f, 2f, 3f), position(source, id))
    }

    @Test
    fun eachPasteAddsAnotherEntity() {
        val (source, id) = sceneWithTree()
        val text = (clipboard.copy(source, id) as CopyResult.Copied).text
        val first = clipboard.paste(source, text, Vector3(0f, 0f, 0f)).entityId
        val second = clipboard.paste(source, text, Vector3(5f, 0f, 0f)).entityId
        assertTrue(first != second && first != id && second != id)
    }

    @Test
    fun anEntityWithoutARenderComponentIsRefused() {
        val root = scene()
        val light = root["ecs"].properties().first { !it.value["components"].has("RenderComponent") }.key
        assertTrue(clipboard.copy(root, light) is CopyResult.Refused)
        assertTrue(clipboard.copy(root, "missing") is CopyResult.Refused)
    }

    @Test
    fun foreignClipboardTextIsNotPasted() {
        val root = scene()
        val before = root.toString()
        assertFalse(clipboard.holdsEntity("hello"))
        assertFalse(clipboard.holdsEntity(null))
        assertTrue(
            clipboard.paste(
                root,
                "{\"abyssusEntity\":1,\"components\":{}}",
                Vector3()
            ).result is EditResult.Rejected
        )
        assertEquals(before, root.toString())
    }
}
