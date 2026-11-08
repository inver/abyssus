/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.lib.core.hierarchy.*

/**
 * Resolves every entity's [WorldTransformComponent] from its `PositionComponent` and its `ParentComponent` chain, so
 * that systems after it (look-at, camera sync, a game's renderer) see this update's world poses. Runs first: give it
 * the lowest priority of the engine. The link structure is resolved by [WorldPoses] only when it changes (an entity
 * added or removed, a parent or node edited); each update then composes the matrices in parent-first order without
 * allocating. Hierarchy warnings go to [warn], once per entity and problem for this engine.
 *
 * [nodes] answers the rest transform of a node of a parent's model; a game sets it once its models are built
 * (until then a node-bound child sits on its parent).
 */
class HierarchySystem(
    private val warn: (String) -> Unit,
    var nodes: NodeTransforms = noNodeTransforms(),
    priority: Int = PRIORITY,
) : EntitySystem(priority) {
    private val poses = WorldPoses()
    private val warnings = HierarchyWarningLog()
    private lateinit var entities: ImmutableArray<Entity>

    // the structure last resolved: per entity, its id, parent id and node, to notice a change cheaply
    private var signatureIds = LongArray(0)
    private var signatureParents = IntArray(0)
    private var signatureNodes = arrayOfNulls<String>(0)

    // parent-first order of the entities, with each one's followed parent (null for a root) and node
    private var order = arrayOfNulls<Entity>(0)
    private var orderParents = arrayOfNulls<Entity>(0)
    private var orderNodes = arrayOfNulls<String>(0)
    private var orderIds = LongArray(0)

    private val frame = Matrix4()

    override fun addedToEngine(engine: Engine) {
        entities = engine.entities
    }

    override fun update(deltaTime: Float) {
        if (structureChanged()) {
            rebuild()
        }
        for (i in order.indices) {
            val entity = order[i]!!
            val world = entity.getComponent(WorldTransformComponent::class.java) ?: continue
            val local = entity.getComponent(PositionComponent::class.java)?.getTransform()
            val parent = orderParents[i]
            if (parent == null) {
                world.parentFrame.idt()
                if (local == null) {
                    world.transform.idt()
                } else {
                    world.transform.set(local)
                }
                world.child = false
                world.disabled = !entity.isActive()
            } else {
                val parentWorld = parent.getComponent(WorldTransformComponent::class.java)
                frame.set(parentWorld.transform)
                orderNodes[i]?.let { node -> applyNode(frame, orderIds[i], parent, node) }
                world.parentFrame.set(frame)
                world.transform.set(frame)
                if (local != null) {
                    world.transform.mul(local)
                }
                world.child = true
                world.disabled = !entity.isActive() || parentWorld.disabled
            }
            world.resolved = true
        }
    }

    private fun applyNode(frame: Matrix4, childId: Long, parent: Entity, node: String) {
        val parentId = parent.getComponent(HeaderComponent::class.java)?.id ?: return
        when (val rest = nodes.of(parentId, node)) {
            is NodeTransform.Found -> frame.mul(rest.transform)
            NodeTransform.Missing -> report(
                listOf(
                    HierarchyWarning(
                        childId,
                        HierarchyProblem.UNKNOWN_NODE,
                        parentId,
                        node
                    )
                )
            )

            NodeTransform.NotLoaded -> Unit
        }
    }

    private fun structureChanged(): Boolean {
        val n = entities.size()
        if (n != signatureIds.size) {
            return true
        }
        for (i in 0 until n) {
            val entity = entities[i]
            val link = entity.getComponent(ParentComponent::class.java)
            if (signatureIds[i] != idOf(entity, i) ||
                signatureParents[i] != (link?.parentEntityId ?: NO_LINK) ||
                signatureNodes[i] != link?.node
            ) {
                return true
            }
        }
        return false
    }

    private fun rebuild() {
        val n = entities.size()
        signatureIds = LongArray(n)
        signatureParents = IntArray(n)
        signatureNodes = arrayOfNulls(n)
        val byId = HashMap<Long, Entity>(n * 2)
        val parents = HashMap<Long, ParentLink>()
        for (i in 0 until n) {
            val entity = entities[i]
            if (entity.getComponent(WorldTransformComponent::class.java) == null) {
                entity.add(WorldTransformComponent())
            }
            val id = idOf(entity, i)
            val link = entity.getComponent(ParentComponent::class.java)
            signatureIds[i] = id
            signatureParents[i] = link?.parentEntityId ?: NO_LINK
            signatureNodes[i] = link?.node
            byId[id] = entity
            link?.let(::parentLinkOf)?.let { parents[id] = it }
        }
        // the structure only: poses and nodes are composed per update
        val resolved = poses.resolve(byId.keys, emptyMap(), parents)
        report(resolved.warnings)

        val sorted = ArrayList<Long>(n)
        val visited = HashSet<Long>(n * 2)
        fun visit(id: Long) {
            if (!visited.add(id)) {
                return
            }
            resolved.parents[id]?.let(::visit)
            sorted += id
        }
        byId.keys.sorted().forEach(::visit)
        order = arrayOfNulls(n)
        orderParents = arrayOfNulls(n)
        orderNodes = arrayOfNulls(n)
        orderIds = LongArray(n)
        sorted.forEachIndexed { i, id ->
            order[i] = byId.getValue(id)
            orderParents[i] = resolved.parents[id]?.let(byId::get)
            orderNodes[i] = if (resolved.parents[id] != null) {
                parents[id]?.node
            } else {
                null
            }
            orderIds[i] = id
        }
    }

    private fun report(found: List<HierarchyWarning>) {
        if (found.isEmpty()) {
            return
        }
        warnings.fresh(found).forEach { warn(it.message()) }
    }

    // an entity without a header (built by code, not loaded) gets a negative id that no link can name
    private fun idOf(entity: Entity, index: Int): Long =
        entity.getComponent(HeaderComponent::class.java)?.id?.takeIf { it >= 0 } ?: (-2L - index)
}

/** The priority [HierarchySystem] runs at by default: before every system of the runtime. */
const val HIERARCHY_PRIORITY = -100
private const val PRIORITY = HIERARCHY_PRIORITY
private const val NO_LINK = Int.MIN_VALUE
