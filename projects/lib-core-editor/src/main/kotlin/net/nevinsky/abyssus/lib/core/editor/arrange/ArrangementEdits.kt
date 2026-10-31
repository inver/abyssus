/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.PlacementWrites
import net.nevinsky.abyssus.lib.core.editor.scene.newEntity

/** What an arrangement edit did to a scene. The scene is only touched for [Changed]. */
sealed interface ArrangementResult {
    /** The scene changed; [entityId] is the new arrangement for a create. */
    data class Changed(val entityId: Long? = null) : ArrangementResult

    data object Unchanged : ArrangementResult

    data class Rejected(val reason: String) : ArrangementResult
}

/**
 * Creates, regenerates and detaches the copies of a path arrangement by editing a [SceneDto]. The arrangement's
 * [PathArrangementComponent] holds the line and the settings; its copies are ordinary entities that point back with a
 * `ParentComponent`, and `slots` lists the entity of each place along the line. Callers write the scene through
 * `editSceneJson` as one undoable command. [detach] needs no [messages], so the default suits callers that only detach.
 */
class ArrangementEdits(private val messages: EditorMessages = EditorMessages { key, _ -> key }) {
    private val layout = PathLayout()
    private val placements = PlacementWrites()

    /**
     * Adds an arrangement of the model [asset] along [points] (and back to the first when [closed]), and a copy for each
     * slot, standing on [heights]. Ids count up from one more than the highest entity id.
     */
    fun create(
        scene: SceneDto,
        asset: BaseMeta,
        points: List<Vector3>,
        closed: Boolean,
        heights: GroundHeight,
    ): ArrangementResult {
        val component = PathArrangementComponent()
        component.points = points.map { Vector3(it.x, it.y, it.z) }.toMutableList()
        component.closed = closed
        component.asset = BaseMeta(MetaType.MODEL, asset.name)
        if (asset.type != MetaType.MODEL || !component.isUsable()) {
            return ArrangementResult.Rejected(messages.message("arrangementInvalid"))
        }
        val slots = layout.slots(component) ?: return tooMany()

        val arrangementId = scene.nextId()
        scene.ecs[arrangementId] = newEntity(
            arrangementId, EntityType.GROUP, NameComponent(messages.message("arrangementName", arrangementId.toString())), component,
        )
        for ((k, slot) in slots.withIndex()) {
            val copyId = scene.nextId()
            scene.ecs[copyId] = copyOf(copyId, arrangementId, k, slot, component.asset, heights)
            component.slots.add(copyId.toInt())
        }
        return ArrangementResult.Changed(arrangementId)
    }

    /**
     * Re-places the copies of the arrangement [arrangementId] after its component changed (or not, for Regenerate).
     * A filled slot gets its position, rotation and render asset rewritten and keeps its id, name and other components;
     * slots past the old list get new copies; filled slots past the new layout lose their entity. An empty slot (`-1`,
     * a missing entity, or an entity that no longer has a `ParentComponent` to the arrangement) stays empty and is
     * written as `-1`, unless [before], the component as it was, shows that `spacing`, `sides`, `closed` or the first
     * point changed, which fills every slot of the new layout. Rejected, leaving [scene] as it was, for an unusable
     * line or more than [MAX_ARRANGEMENT_SLOTS] copies; Unchanged when no value differs.
     */
    fun regenerate(
        scene: SceneDto,
        arrangementId: Long,
        before: PathArrangementComponent?,
        heights: GroundHeight,
    ): ArrangementResult {
        val component = scene.entity(arrangementId)?.components?.filterIsInstance<PathArrangementComponent>()?.firstOrNull()
            ?: return ArrangementResult.Rejected(messages.message("arrangementMissing", arrangementId))
        if (!component.isUsable()) {
            return ArrangementResult.Rejected(messages.message("arrangementInvalid"))
        }
        val slots = layout.slots(component) ?: return tooMany()

        val refill = before != null && (
                before.spacing != component.spacing || before.sides != component.sides || before.closed != component.closed ||
                        before.points.firstOrNull() != component.points.firstOrNull()
                )
        val oldSlots = component.slots.toList()
        val written = ArrayList<Int>(slots.size)
        var changed = false
        for ((k, slot) in slots.withIndex()) {
            val oldId = oldSlots.getOrNull(k)
            val filled = oldId != null && isCopy(scene, oldId, arrangementId)
            written += when {
                filled -> oldId.also { changed = place(scene, it.toLong(), slot, component.asset, heights) or changed }
                k < oldSlots.size && !refill -> NO_ENTITY
                else -> {
                    val copyId = scene.nextId()
                    scene.ecs[copyId] = copyOf(copyId, arrangementId, k, slot, component.asset, heights)
                    changed = true
                    copyId.toInt()
                }
            }
        }
        for (k in slots.size until oldSlots.size) {
            val id = oldSlots[k]
            if (isCopy(scene, id, arrangementId)) {
                scene.ecs.remove(id.toLong())
                changed = true
            }
        }
        if (component.slots != written) {
            component.slots = written
            changed = true
        }
        return if (changed) ArrangementResult.Changed() else ArrangementResult.Unchanged
    }

    /**
     * Replaces the points of the arrangement [arrangementId] by [points] and regenerates its copies, as dragging, inserting
     * or removing a path point does. Rejected, leaving [scene] as it was, for fewer than two points or a line that would
     * make too many copies.
     */
    fun setPoints(
        scene: SceneDto,
        arrangementId: Long,
        points: List<Vector3>,
        heights: GroundHeight
    ): ArrangementResult {
        val component = scene.entity(arrangementId)?.components?.filterIsInstance<PathArrangementComponent>()?.firstOrNull()
            ?: return ArrangementResult.Rejected(messages.message("arrangementMissing", arrangementId))
        if (points.size < 2 || points.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) {
            return ArrangementResult.Rejected(messages.message("arrangementInvalid"))
        }
        val before = arrangementAlong(component.points, component)
        val after = arrangementAlong(points, component)
        if (!after.isUsable()) return ArrangementResult.Rejected(messages.message("arrangementInvalid"))
        if (layout.slots(after) == null) return tooMany()
        component.points = points.map { Vector3(it) }.toMutableList()
        return regenerate(scene, arrangementId, before, heights)
    }

    /**
     * Makes the copy [copyId] an ordinary entity: removes its `ParentComponent` and writes `-1` for it in the slots of
     * its arrangement. False, leaving [scene] as it was, when the entity is not a linked copy.
     */
    fun detach(scene: SceneDto, copyId: Long): Boolean {
        val id = copyId.toInt()
        val copy = scene.entity(copyId) ?: return false
        val parent = copy.components.filterIsInstance<ParentComponent>().firstOrNull()?.parentEntityId ?: return false
        val arrangement = scene.entity(parent.toLong())?.components?.filterIsInstance<PathArrangementComponent>()?.firstOrNull()
            ?: return false
        val at = arrangement.slots.indexOf(id)
        if (at < 0) return false
        arrangement.slots[at] = NO_ENTITY
        copy.components.removeAll { it is ParentComponent }
        return true
    }

    /** Whether [copyId] is linked to the arrangement: it exists and its `ParentComponent` points at [arrangementId]. */
    private fun isCopy(scene: SceneDto, copyId: Int, arrangementId: Long): Boolean {
        if (copyId < 0) return false
        val parent = scene.entity(copyId.toLong())?.components?.filterIsInstance<ParentComponent>()?.firstOrNull()
        return parent != null && parent.parentEntityId.toLong() == arrangementId
    }

    private fun copyOf(
        id: Long,
        arrangementId: Long,
        slotIndex: Int,
        slot: PathSlot,
        asset: BaseMeta,
        heights: GroundHeight,
    ): EntityDto {
        val at = slot.position(heights)
        val position = PositionComponent(at.x, at.y, at.z)
        position.localRotation.set(slot.rotation())
        return newEntity(
            id, EntityType.OBJECT,
            NameComponent(messages.message("arrangementCopyName", arrangementId, slotIndex.toString())),
            position,
            RenderComponent("defaultShader", asset.type, asset.name),
            ParentComponent(arrangementId.toInt()),
        )
    }

    /** Writes the slot's position and rotation and the arrangement's model into the copy; whether anything differed. */
    private fun place(scene: SceneDto, copyId: Long, slot: PathSlot, asset: BaseMeta, heights: GroundHeight): Boolean {
        val copy = scene.entity(copyId) ?: return false
        val placement = placements.placementOf(copy)
        var changed = placements.setPosition(placement, slot.position(heights))
        changed = placements.setRotation(placement, slot.rotation()) or changed
        val render = copy.components.filterIsInstance<RenderComponent>().firstOrNull() ?: return changed
        if (render.assetName != asset.name) { render.assetName = asset.name; changed = true }
        if (render.type != asset.type) { render.type = asset.type; changed = true }
        return changed
    }

    private fun tooMany() =
        ArrangementResult.Rejected(messages.message("arrangementTooMany", MAX_ARRANGEMENT_SLOTS.toString()))
}
