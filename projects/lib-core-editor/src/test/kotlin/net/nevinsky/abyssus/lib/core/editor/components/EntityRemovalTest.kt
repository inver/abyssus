/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEdit
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EntityRemovalTest {
    private val messages = ResourceEditorMessages()
    private val removal = EntityRemoval(messages)
    private val json = SceneJson()
    private val untitled = File(testProject("Untitled"), "scenes/Main Scene.scene").readText()

    private fun scene(vararg entities: String, wrapped: Boolean = true): JsonNode {
        val body = entities.joinToString(",")
        val ecs = if (wrapped) """{"entities":{$body},"archetypes":{"1":{"a":1}},"metadata":{"m":2}}""" else "{$body}"
        return json.parse("""{"format":"abyssus","formatVersion":1,"ecs":$ecs}""")
    }

    private fun entity(id: Int, components: String) = """"$id":{"archetype":1,"components":{$components}}"""

    private fun entities(root: JsonNode) = (root["ecs"]["entities"] ?: root["ecs"])

    private fun rejected(result: EditResult): String {
        assertTrue("expected Rejected, was $result", result is EditResult.Rejected)
        return (result as EditResult.Rejected).reason
    }

    @Test
    fun removesAnUnreferencedEntityFromADirectEcs() {
        val root = scene(entity(0, """"A":{"x":1.000}"""), entity(1, """"B":{}"""), wrapped = false)
        assertEquals(EditResult.Changed, removal.remove(root, "0"))
        assertFalse(root["ecs"].has("0"))
        assertTrue(root["ecs"].has("1"))
    }

    @Test
    fun wrappedSceneKeepsArchetypesAndMetadata() {
        val root = scene(entity(0, """"A":{}"""), entity(1, """"B":{}"""))
        assertEquals(EditResult.Changed, removal.remove(root, "1"))
        assertFalse(entities(root).has("1"))
        assertEquals("""{"1":{"a":1}}""", json.compact(root["ecs"]["archetypes"]))
        assertEquals("""{"m":2}""", json.compact(root["ecs"]["metadata"]))
    }

    @Test
    fun missingTargetIsRejectedAndLeavesTheTree() {
        val root = scene(entity(0, """"A":{}"""))
        val before = json.compact(root)
        assertTrue(rejected(removal.remove(root, "7")).contains("7"))
        assertEquals(before, json.compact(root))
    }

    @Test
    fun shortAndQualifiedComponentKeysBothBlock() {
        for (key in listOf("PositionComponent", "net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent")) {
            val root = scene(entity(0, """"A":{}"""), entity(1, """"$key":{"lookAtId":0}"""))
            val before = json.compact(root)
            val reason = rejected(removal.remove(root, "0"))
            assertTrue(reason, reason.contains("entity 1") && reason.contains("PositionComponent.lookAtId"))
            assertEquals(before, json.compact(root))
        }
    }

    @Test
    fun unknownPayloadsAreNotInterpretedAsReferences() {
        val root = scene(entity(0, """"A":{}"""), entity(1, """"Mystery":{"target":0,"lookAtId":0}"""))
        assertEquals(EditResult.Changed, removal.remove(root, "0"))
        assertEquals(1, entities(root).size())
    }

    @Test
    fun parentAndBothPointEndpointsBlock() {
        for ((component, member) in listOf(
            "ParentComponent" to "parentEntityId", "Point2PointPositionComponent" to "entity1Id",
            "Point2PointPositionComponent" to "entity2Id",
        )) {
            val root = scene(entity(0, """"A":{}"""), entity(1, """"$component":{"$member":0}"""))
            val before = json.compact(root)
            assertTrue(rejected(removal.remove(root, "0")).contains("$component.$member"))
            assertEquals(before, json.compact(root))
        }
    }

    @Test
    fun registeredEntityReferenceFieldBlocks() {
        class Link : Component {
            var other = -1
        }
        val kind = ComponentKind(
            "LinkComponent",
            object : ComponentCodec<Link> {
                override val name = "LinkComponent"
                override val type = Link::class.java
                override fun read(node: JsonNode) = Link().also { it.other = node["other"]?.asInt(-1) ?: -1 }
                override fun write(component: Link): JsonNode = json.parse("""{"other":${component.other}}""")
            },
            listOf(ComponentField<Link>("other", FieldKind.ENTITY_REF, { it.other.toString() }, { c, t -> c.other = t.toInt() })),
            { Link() },
        )
        val root = scene(entity(0, """"A":{}"""), entity(1, """"LinkComponent":{"other":0}"""))
        val reason = rejected(EntityRemoval(messages, listOf(kind)).remove(root, "0"))
        assertTrue(reason, reason.contains("entity 1") && reason.contains("LinkComponent.other"))
        assertEquals(EditResult.Changed, removal.remove(root, "0"))
    }

    @Test
    fun arrangementSlotAndOwnedCopiesBlock() {
        val root = scene(
            entity(0, """"PathArrangementComponent":{"slots":[1,-1,2]}"""),
            entity(1, """"B":{}"""), entity(2, """"B":{}"""),
        )
        val before = json.compact(root)
        assertTrue(rejected(removal.remove(root, "1")).contains("slot"))
        assertTrue(rejected(removal.remove(root, "0")).contains("1, 2"))
        assertEquals(before, json.compact(root))
        // an arrangement whose slots are all empty or dangling owns nothing
        val empty = scene(entity(0, """"PathArrangementComponent":{"slots":[-1,9]}"""))
        assertEquals(EditResult.Changed, removal.remove(empty, "0"))
    }

    @Test
    fun clearingTheReferenceThenRemovingWorks() {
        val root = scene(entity(0, """"A":{}"""), entity(1, """"PositionComponent":{"lookAtId":0}"""))
        rejected(removal.remove(root, "0"))
        (root["ecs"]["entities"]["1"]["components"]["PositionComponent"] as com.fasterxml.jackson.databind.node.ObjectNode).remove("lookAtId")
        assertEquals(EditResult.Changed, removal.remove(root, "0"))
    }

    @Test
    fun headlessRemovalOfUntitledModelUndoesByText() {
        val edit = HeadlessEditing().removeEntity(untitled, "0") as HeadlessEdit.Edited
        val tree = json.parse(edit.text)["ecs"]
        assertFalse(tree.has("0"))
        assertEquals(10, tree.size())
        // unrelated text is untouched: every remaining entity line is still in the original
        assertTrue(edit.text.lines().all { it in untitled.lines() || it.isBlank() })
    }

    @Test
    fun headlessRefusesUntitledReferencedEntities() {
        val editing = HeadlessEditing()
        for ((id, relation) in listOf("3" to "lookAtId", "4" to "parentEntityId")) {
            val refused = editing.removeEntity(untitled, id)
            assertTrue("$id: $refused", refused is HeadlessEdit.Refused)
        }
        val camera = editing.removeEntity(untitled, "3") as HeadlessEdit.Refused
        assertTrue(camera.refusal.message, camera.refusal.message.contains("entity 4") && camera.refusal.message.contains("lookAtId"))
        val missing = editing.removeEntity(untitled, "99")
        assertTrue(missing is HeadlessEdit.Refused)
    }
}
