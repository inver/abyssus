/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.*
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree
import net.nevinsky.abyssus.lib.core.editor.ecs.EcsWriter
import net.nevinsky.abyssus.lib.core.editor.ecs.floatNode
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.slf4j.helpers.NOPLogger


/** What an arrangement edit did to a scene's JSON tree. The tree is only touched for [Changed]. */
sealed interface ArrangementResult {
    /** The tree changed; [entityId] is the new arrangement for a create. */
    data class Changed(val entityId: String? = null) : ArrangementResult

    data object Unchanged : ArrangementResult

    data class Rejected(val reason: String) : ArrangementResult
}

/**
 * Creates, regenerates and detaches the copies of a path arrangement by editing a scene's JSON tree. The arrangement's
 * [PathArrangementComponent] holds the line and the settings; its copies are ordinary entities that point back with a
 * `ParentComponent`, and `slots` lists the entity of each place along the line. Callers write the tree through
 * `editSceneJson` as one undoable command. [detach] needs no [messages], so the default suits callers that only detach.
 */
class ArrangementEdits(private val messages: EditorMessages = EditorMessages { key, _ -> key }) {
    private val nodes = JsonNodeFactory.instance
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val writer = EcsWriter(json)
    private val layout = PathLayout()

    /**
     * Adds an arrangement of the model [asset] along [points] (and back to the first when [closed]), and a copy for each
     * slot, standing on [heights]. Ids count up from one more than the highest entity id.
     */
    fun create(
        root: JsonNode,
        asset: BaseMeta,
        points: List<Vector3>,
        closed: Boolean,
        heights: GroundHeight,
    ): ArrangementResult {
        val tree = SceneEntityTree(root)
        if (!tree.canAdd()) return unreadable()
        if (asset.type != "MODEL" || points.size < 2 || points.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) {
            return ArrangementResult.Rejected(messages.message("arrangementInvalid"))
        }
        val component = PathArrangementComponent()
        component.points = points.map { Vector3(it.x, it.y, it.z) }.toMutableList()
        component.closed = closed
        component.asset = BaseMeta(MetaType.MODEL, asset.name)
        val slots = layout.slots(component) ?: return tooMany()

        val arrangementId = tree.insert { id ->
            val components = nodes.objectNode()
            components.set<JsonNode>(
                "NameComponent",
                writer.writeComponent(NameComponent(messages.message("arrangementName", id)))
            )
            components.set<JsonNode>("TypeComponent", writer.writeComponent(TypeComponent(TypeComponent.Type.GROUP)))
            components.set<JsonNode>(PathArrangementComponent::class.simpleName, writer.writeComponent(component))
            components
        } ?: return unreadable()
        for ((k, slot) in slots.withIndex()) {
            val copy = tree.insert { id -> copyComponents(arrangementId, k, slot, component.asset, heights) }
                ?: return unreadable()
            component.slots.add(copy.toInt())
        }
        tree.components(arrangementId)
            ?.set<JsonNode>(PathArrangementComponent::class.simpleName, writer.writeComponent(component))
        return ArrangementResult.Changed(arrangementId)
    }

    /**
     * Re-places the copies of the arrangement [arrangementId] after its component changed (or not, for Regenerate).
     * A filled slot gets its position, rotation and render asset rewritten and keeps its id, name and other components;
     * slots past the old list get new copies; filled slots past the new layout lose their entity. An empty slot (`-1`,
     * a missing entity, or an entity that no longer has a `ParentComponent` to the arrangement) stays empty and is
     * written as `-1`, unless [before], the component as it was, shows that `spacing`, `sides`, `closed` or the first
     * point changed, which fills every slot of the new layout. Rejected, leaving [root] as it was, for an unusable
     * line or more than [MAX_ARRANGEMENT_SLOTS] copies; Unchanged when no value differs.
     */
    fun regenerate(
        root: JsonNode,
        arrangementId: String,
        before: PathArrangementComponent?,
        heights: GroundHeight,
    ): ArrangementResult {
        val tree = SceneEntityTree(root)
        val components = tree.components(arrangementId)
        val node = components?.get(PathArrangementComponent::class.simpleName) as? ObjectNode
            ?: return ArrangementResult.Rejected(messages.message("arrangementMissing", arrangementId))
        val component =
            runCatchingKeepingCancellation { json.bindComponent(node, PathArrangementComponent::class.java) }
                .getOrElse { return ArrangementResult.Rejected(messages.message("arrangementMissing", arrangementId)) }
        if (component.points.size < 2 || !(component.spacing > 0f) || !(component.offset >= 0f)) {
            return ArrangementResult.Rejected(messages.message("arrangementInvalid"))
        }
        val slots = layout.slots(component) ?: return tooMany()
        val snapshot = root.deepCopy<JsonNode>()

        val refill = before != null && (
                before.spacing != component.spacing || before.sides != component.sides || before.closed != component.closed ||
                        before.points.firstOrNull() != component.points.firstOrNull()
                )
        val oldSlots = component.slots
        val written = ArrayList<Int>(slots.size)
        for ((k, slot) in slots.withIndex()) {
            val oldId = oldSlots.getOrNull(k)
            val filled = oldId != null && isCopy(tree, oldId, arrangementId)
            written += when {
                filled -> oldId.also { place(tree, it.toString(), slot, component.asset, heights) }
                k < oldSlots.size && !refill -> NO_ENTITY
                else -> tree.insert { id -> copyComponents(arrangementId, k, slot, component.asset, heights) }?.toInt()
                    ?: return unreadable()
            }
        }
        for (k in slots.size until oldSlots.size) {
            val id = oldSlots[k]
            if (isCopy(tree, id, arrangementId)) (tree.entities() as? ObjectNode)?.remove(id.toString())
        }
        setSlots(node, written)
        return if (root == snapshot) {
            ArrangementResult.Unchanged
        } else ArrangementResult.Changed()
    }

    /**
     * Replaces the points of the arrangement [arrangementId] by [points] and regenerates its copies, as dragging, inserting
     * or removing a path point does. Rejected, leaving [root] as it was, for fewer than two points or a line that would
     * make too many copies.
     */
    fun setPoints(
        root: JsonNode,
        arrangementId: String,
        points: List<Vector3>,
        heights: GroundHeight
    ): ArrangementResult {
        val node = SceneEntityTree(root).components(arrangementId)
            ?.get(PathArrangementComponent::class.simpleName) as? ObjectNode
            ?: return ArrangementResult.Rejected(messages.message("arrangementMissing", arrangementId))
        if (points.size < 2 || points.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) {
            return ArrangementResult.Rejected(messages.message("arrangementInvalid"))
        }
        val before = runCatchingKeepingCancellation { json.bindComponent(node, PathArrangementComponent::class.java) }
            .getOrElse { return ArrangementResult.Rejected(messages.message("arrangementMissing", arrangementId)) }
        val after = arrangementAlong(points, before)
        if (layout.slots(after) == null) return tooMany()
        val previous = node.get("points")
        node.set<JsonNode>("points", nodes.arrayNode().also { array ->
            for ((i, p) in points.withIndex()) {
                val old = (previous as? ArrayNode)?.get(i)
                array.add(pointNode(p, old))
            }
        })
        return regenerate(root, arrangementId, before, heights)
    }

    /** A point as the file writes it; the node [old] keeps its text when the point is unchanged. */
    private fun pointNode(p: Vector3, old: JsonNode?): ObjectNode {
        if (old is ObjectNode && listOf("x", "y", "z").all { old.get(it)?.isNumber == true } &&
            old.get("x").floatValue() == p.x && old.get("y").floatValue() == p.y && old.get("z").floatValue() == p.z
        ) return old
        return nodes.objectNode().also {
            it.set<JsonNode>("x", floatNode(p.x))
            it.set<JsonNode>("y", floatNode(p.y))
            it.set<JsonNode>("z", floatNode(p.z))
        }
    }

    /**
     * Makes the copy [copyId] an ordinary entity: removes its `ParentComponent` and writes `-1` for it in the slots of
     * its arrangement. False, leaving [root] as it was, when the entity is not a linked copy.
     */
    fun detach(root: JsonNode, copyId: String): Boolean {
        val tree = SceneEntityTree(root)
        val id = copyId.toIntOrNull() ?: return false
        val components = tree.components(copyId) ?: return false
        val parent =
            components.get("ParentComponent")?.get("parentEntityId")?.takeIf { it.isInt }?.asInt() ?: return false
        val slots =
            tree.components(parent.toString())?.get(PathArrangementComponent::class.simpleName)
                ?.get("slots") as? ArrayNode ?: return false
        val at = slots.indexOfFirst { it.isInt && it.asInt() == id }
        if (at < 0) return false
        slots.set(at, IntNode.valueOf(NO_ENTITY))
        components.remove("ParentComponent")
        tree.matchArchetype(copyId)
        return true
    }

    /** Whether [copyId] is linked to the arrangement: it exists and its `ParentComponent` points at [arrangementId]. */
    private fun isCopy(tree: SceneEntityTree, copyId: Int, arrangementId: String): Boolean {
        if (copyId < 0) return false
        val parent = tree.components(copyId.toString())?.get("ParentComponent")?.get("parentEntityId")
        return parent != null && parent.isInt && parent.asInt().toString() == arrangementId
    }

    private fun copyComponents(
        arrangementId: String,
        slotIndex: Int,
        slot: PathSlot,
        asset: BaseMeta,
        heights: GroundHeight
    ): ObjectNode {
        val components = nodes.objectNode()
        components.set<JsonNode>(
            "NameComponent",
            writer.writeComponent(
                NameComponent(
                    messages.message(
                        "arrangementCopyName",
                        arrangementId,
                        slotIndex.toString()
                    )
                )
            ),
        )
        components.set<JsonNode>("TypeComponent", writer.writeComponent(TypeComponent(TypeComponent.Type.OBJECT)))
        val at = slot.position(heights)
        val position = PositionComponent(at.x, at.y, at.z)
        position.localRotation.set(slot.rotation())
        components.set<JsonNode>("PositionComponent", writer.writeComponent(position))
        components.set<JsonNode>(
            "RenderComponent",
            writer.writeComponent(RenderComponent("defaultShader", asset.type, asset.name)),
        )
        components.set<JsonNode>("ParentComponent", writer.writeComponent(ParentComponent(arrangementId.toInt())))
        return components
    }

    /** Writes the slot's position and rotation and the arrangement's model into the copy, touching only what differs. */
    private fun place(tree: SceneEntityTree, copyId: String, slot: PathSlot, asset: BaseMeta, heights: GroundHeight) {
        val components = tree.components(copyId) ?: return
        val placement = components.get("PositionComponent").let {
            if (it == null || it.isNull) components.putObject("PositionComponent") else it as? ObjectNode ?: return
        }
        val at = slot.position(heights)
        setFields(placement, "localPosition", listOf(Field("x", at.x, 0f), Field("y", at.y, 0f), Field("z", at.z, 0f)))
        val q = slot.rotation()
        setFields(
            placement,
            "localRotation",
            listOf(Field("x", q.x, 0f), Field("y", q.y, 0f), Field("z", q.z, 0f), Field("w", q.w, 1f))
        )
        val render = components.get("RenderComponent") as? ObjectNode ?: return
        val target = if (render.has("assetName")) {
            render
        } else (render.get("renderable")?.get("asset") as? ObjectNode) ?: return
        if (target.get("assetName")?.asText() != asset.name) target.put("assetName", asset.name)
        if (target.get("type")?.asText() != asset.type.name) target.put("type", asset.type.name)
    }

    private class Field(val key: String, val value: Float, val default: Float)

    /** Writes [fields] into the object [name] of [parent]: a missing object gets every field, an existing one those that differ. */
    private fun setFields(parent: ObjectNode, name: String, fields: List<Field>) {
        val existing = parent.get(name)
        if (existing != null && !existing.isNull && existing !is ObjectNode) return
        val target = existing as? ObjectNode
        val differing =
            fields.filter { f -> (target?.get(f.key)?.takeIf { it.isNumber }?.floatValue() ?: f.default) != f.value }
        if (differing.isEmpty()) return
        val written = target ?: parent.putObject(name)
        for (f in if (target == null) fields.filter { it.value != it.default } else differing) {
            written.set<JsonNode>(f.key, FloatNode.valueOf(f.value))
        }
    }

    private fun setSlots(node: ObjectNode, slots: List<Int>) {
        val existing = node.get("slots")
        val same =
            existing is ArrayNode && existing.size() == slots.size && slots.indices.all { existing[it].isInt && existing[it].asInt() == slots[it] }
        if (same) return
        node.set<JsonNode>("slots", nodes.arrayNode().also { a -> slots.forEach { a.add(it) } })
    }

    private fun unreadable() = ArrangementResult.Rejected(messages.message("componentSceneUnreadable"))

    private fun tooMany() =
        ArrangementResult.Rejected(messages.message("arrangementTooMany", MAX_ARRANGEMENT_SLOTS.toString()))
}
