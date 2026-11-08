/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.hierarchy

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs

/** An entity's stored pose: its `PositionComponent` position, rotation and scale. Each value is its own copy. */
class LocalPose(
    val position: Vector3 = Vector3(),
    val rotation: Quaternion = Quaternion(),
    val scale: Vector3 = Vector3(1f, 1f, 1f),
) {
    /** `T x R x S` of this pose, written into [out]. */
    fun matrix(out: Matrix4 = Matrix4()): Matrix4 = out.set(position, rotation, scale)

    override fun toString(): String = "LocalPose(position=$position, rotation=$rotation, scale=$scale)"
}

/** A child's link: the parent entity's id and, optionally, the name of a node in the parent's model. */
data class ParentLink(val parentId: Long, val node: String? = null)

/** What a node lookup found. [NotLoaded] (the model is not read yet) places the child on the parent, silently. */
sealed interface NodeTransform {
    /** The rest transform of the node within its model. */
    class Found(val transform: Matrix4) : NodeTransform

    /** The parent has no model, or its model has no node of that name. */
    data object Missing : NodeTransform

    /** The parent's model is not available yet; the answer may change on a later resolve. */
    data object NotLoaded : NodeTransform
}

/** Rest transforms of nodes of a parent's model, by parent entity id and node name. */
fun interface NodeTransforms {
    fun of(parentId: Long, node: String): NodeTransform
}

/** A [NodeTransforms] that knows no model: every node is [NodeTransform.NotLoaded]. */
fun noNodeTransforms(): NodeTransforms = NodeTransforms { _, _ -> NodeTransform.NotLoaded }

/** Why a parent link was not followed. */
enum class HierarchyProblem {
    MISSING_PARENT,
    SELF_LINK,
    CYCLE,
    UNKNOWN_NODE,
}

/** One hierarchy problem of entity [entityId]; [node] is the node name for [HierarchyProblem.UNKNOWN_NODE]. */
data class HierarchyWarning(
    val entityId: Long,
    val problem: HierarchyProblem,
    val parentId: Long,
    val node: String? = null,
) {
    /** The warning as one log line naming the entity. */
    fun message(): String =
        when (problem) {
            HierarchyProblem.MISSING_PARENT ->
                "entity $entityId: parent $parentId is not in the scene; the entity is treated as a root"
            HierarchyProblem.SELF_LINK -> "entity $entityId: is its own parent; the entity is treated as a root"
            HierarchyProblem.CYCLE ->
                "entity $entityId: its parents form a cycle through $parentId; the entity is treated as a root"
            HierarchyProblem.UNKNOWN_NODE ->
                "entity $entityId: parent $parentId has no model node '$node'; placed on the parent itself"
        }
}

/**
 * The result of one [WorldPoses.resolve]: each entity's [world] matrix, the [roots] (no link, or a broken one), the
 * followed links ([parents], child to parent), the entities disabled by themselves or an ancestor ([disabled]) and the
 * [warnings]. Matrices are new instances the caller owns.
 */
class ResolvedPoses(
    val world: Map<Long, Matrix4>,
    val roots: Set<Long>,
    val parents: Map<Long, Long>,
    val disabled: Set<Long>,
    val warnings: List<HierarchyWarning>,
) {
    /** The world matrix of [id], or identity when the entity is unknown. */
    fun worldOf(id: Long): Matrix4 = world[id] ?: IDENTITY_TEMPLATE.cpy()

    /** The world matrix of [id]'s parent frame (the parent, times the node's rest transform), identity for a root. */
    fun parentWorldOf(id: Long): Matrix4 = parentFrames[id]?.cpy() ?: Matrix4()

    /** The children of [id] whose link was followed, in id order. */
    fun childrenOf(id: Long): List<Long> = children[id].orEmpty()

    /** Every descendant of [id], depth first, children in id order. */
    fun descendantsOf(id: Long): List<Long> =
        buildList {
            val stack = ArrayDeque(childrenOf(id).asReversed())
            while (stack.isNotEmpty()) {
                val next = stack.removeLast()
                add(next)
                childrenOf(next).asReversed().forEach(stack::addLast)
            }
        }

    /** The ancestors of [id], nearest first. */
    fun ancestorsOf(id: Long): List<Long> =
        buildList {
            var current = parents[id]
            while (current != null) {
                add(current)
                current = parents[current]
            }
        }

    internal val parentFrames = HashMap<Long, Matrix4>()

    private val children: Map<Long, List<Long>> by lazy {
        parents.entries.groupBy({ it.value }, { it.key }).mapValues { (_, ids) -> ids.sorted() }
    }
}

private val IDENTITY_TEMPLATE = Matrix4()

/**
 * Resolves world poses through `ParentComponent` links: world = parent world x node rest transform x local TRS. Only a
 * parent's stored pose is used, never an orientation a consumer derives from its `lookAtId`, so a light aimed at its
 * own
 * child handle makes no cycle. A missing parent, a self-link or a cycle makes the entity a root, with one warning. The
 * resolver is pure: warnings are values, and [HierarchyWarningLog] keeps them to once per scene.
 */
class WorldPoses {

    /**
     * Resolves [ids] (every entity of the scene) from their [locals] (an entity without one has the identity pose) and
     * [parents]. Entities in [disabledSelf] are disabled, and so is every entity below them.
     */
    fun resolve(
        ids: Collection<Long>,
        locals: Map<Long, LocalPose>,
        parents: Map<Long, ParentLink>,
        nodes: NodeTransforms = noNodeTransforms(),
        disabledSelf: Set<Long> = emptySet(),
    ): ResolvedPoses {
        val known = ids.toHashSet()
        val warnings = ArrayList<HierarchyWarning>()
        val followed = HashMap<Long, Long>()
        val roots = HashSet<Long>()

        // links: drop missing parents and self-links first, then cycles (every entity on a cycle becomes a root)
        val candidate = HashMap<Long, ParentLink>()
        for (id in ids.sorted()) {
            val link = parents[id] ?: continue
            when {
                link.parentId == id -> warnings += HierarchyWarning(id, HierarchyProblem.SELF_LINK, id)
                link.parentId !in known ->
                    warnings += HierarchyWarning(id, HierarchyProblem.MISSING_PARENT, link.parentId)
                else -> candidate[id] = link
            }
        }
        val onCycle = cycleMembers(candidate)
        for (id in onCycle.sorted()) {
            warnings += HierarchyWarning(id, HierarchyProblem.CYCLE, candidate.getValue(id).parentId)
            candidate.remove(id)
        }
        for ((id, link) in candidate) followed[id] = link.parentId
        for (id in ids) {
            if (id !in followed) {
                roots += id
            }
        }

        val world = HashMap<Long, Matrix4>()
        val result = ResolvedPoses(world, roots, followed, disabledBelow(ids, followed, disabledSelf), warnings)
        val local = Matrix4()
        fun resolveOne(id: Long): Matrix4 {
            world[id]?.let { return it }
            val own = (locals[id] ?: LocalPose()).matrix(local)
            val parentId = followed[id]
            val out = if (parentId == null) {
                Matrix4(own)
            } else {
                val frame = Matrix4(resolveOne(parentId))
                val node = candidate.getValue(id).node
                if (node != null) {
                    when (val rest = nodes.of(parentId, node)) {
                        is NodeTransform.Found -> frame.mul(rest.transform)
                        NodeTransform.Missing ->
                            warnings += HierarchyWarning(id, HierarchyProblem.UNKNOWN_NODE, parentId, node)
                        NodeTransform.NotLoaded -> Unit
                    }
                }
                result.parentFrames[id] = frame
                // the local matrix is shared scratch; rebuild it after the recursion above
                Matrix4(frame).mul((locals[id] ?: LocalPose()).matrix(local))
            }
            world[id] = out
            return out
        }
        for (id in ids) resolveOne(id)
        return result
    }

    private fun cycleMembers(links: Map<Long, ParentLink>): Set<Long> {
        val state = HashMap<Long, Int>() // 1 = on the current walk, 2 = done
        val members = HashSet<Long>()
        for (start in links.keys.sorted()) {
            if (state[start] == 2) {
                continue
            }
            val path = ArrayList<Long>()
            var current: Long? = start
            while (current != null && state[current] == null) {
                state[current] = 1
                path += current
                current = links[current]?.parentId
            }
            if (current != null && state[current] == 1) {
                members += path.subList(path.indexOf(current), path.size)
            }
            path.forEach { state[it] = 2 }
        }
        return members
    }

    private fun disabledBelow(ids: Collection<Long>, parents: Map<Long, Long>, self: Set<Long>): Set<Long> {
        if (self.isEmpty()) {
            return emptySet()
        }
        return ids.filterTo(HashSet()) { id ->
            var current: Long? = id
            var hit = false
            while (current != null && !hit) {
                hit = current in self
                current = parents[current]
            }
            hit
        }
    }
}

/** A child pose decomposed from matrices; [sheared] is true when TRS could only approximate it. */
class LocalFit(val pose: LocalPose, val sheared: Boolean)

/**
 * The local pose that puts a child at [newWorld] under [parentWorld]: `inverse(parentWorld) x newWorld`, decomposed
 * into TRS. The position is exact; rotation and scale are the best fit when the result is sheared.
 */
fun localFor(parentWorld: Matrix4, newWorld: Matrix4): LocalFit {
    val local = Matrix4(parentWorld).inv().mul(newWorld)
    val position = local.getTranslation(Vector3())
    val scale = columnScale(local, Vector3())
    val rotation = columnRotation(local, scale, Quaternion())
    return LocalFit(LocalPose(position, rotation, scale), isSheared(local))
}

/**
 * The scale of a `T x R x S` matrix: the lengths of its basis columns, the last one negated when the basis is
 * mirrored. (libGDX's `getScale` measures rows, which is wrong for a non-uniform scale under a rotation.)
 */
fun columnScale(m: Matrix4, out: Vector3): Vector3 {
    val v = m.`val`
    val sx = Vector3.len(v[Matrix4.M00], v[Matrix4.M10], v[Matrix4.M20])
    val sy = Vector3.len(v[Matrix4.M01], v[Matrix4.M11], v[Matrix4.M21])
    val sz = Vector3.len(v[Matrix4.M02], v[Matrix4.M12], v[Matrix4.M22])
    val mirrored = m.det3x3() < 0f
    return if (mirrored) {
        out.set(sx, sy, -sz)
    } else {
        out.set(sx, sy, sz)
    }
}

/** The rotation of a `T x R x S` matrix whose column scale is [scale], into [out]. */
fun columnRotation(m: Matrix4, scale: Vector3, out: Quaternion): Quaternion {
    val v = m.`val`
    fun axis(i0: Int, i1: Int, i2: Int, s: Float): Vector3 =
        if (abs(s) < 1e-12f) {
            Vector3()
        } else {
            Vector3(v[i0] / s, v[i1] / s, v[i2] / s)
        }
    val x = axis(Matrix4.M00, Matrix4.M10, Matrix4.M20, scale.x)
    val y = axis(Matrix4.M01, Matrix4.M11, Matrix4.M21, scale.y)
    val z = axis(Matrix4.M02, Matrix4.M12, Matrix4.M22, scale.z)
    // re-orthonormalise (a sheared basis gives the nearest rotation that keeps x)
    x.nor()
    z.set(x).crs(y).nor()
    y.set(z).crs(x).nor()
    return out.setFromAxes(x.x, y.x, z.x, x.y, y.y, z.y, x.z, y.z, z.z).nor()
}

/** Whether a child under [parentWorld] can be sheared: the frame is rotated and its scale is not uniform. */
fun shearsChildren(parentWorld: Matrix4): Boolean {
    val scale = columnScale(parentWorld, Vector3())
    val uniform = abs(scale.x - scale.y) < EPSILON && abs(scale.y - scale.z) < EPSILON
    if (uniform) {
        return false
    }
    val rotation = columnRotation(parentWorld, scale, Quaternion())
    return abs(abs(rotation.w) - 1f) > EPSILON
}

private fun isSheared(m: Matrix4): Boolean {
    val v = m.`val`
    val x = Vector3(v[Matrix4.M00], v[Matrix4.M10], v[Matrix4.M20]).nor()
    val y = Vector3(v[Matrix4.M01], v[Matrix4.M11], v[Matrix4.M21]).nor()
    val z = Vector3(v[Matrix4.M02], v[Matrix4.M12], v[Matrix4.M22]).nor()
    return abs(x.dot(y)) > EPSILON || abs(y.dot(z)) > EPSILON || abs(x.dot(z)) > EPSILON
}

private const val EPSILON = 1e-4f

/**
 * Keeps hierarchy warnings to once per entity and problem for one loaded scene: [fresh] returns only the warnings not
 * seen before. Make one per scene load.
 */
class HierarchyWarningLog {
    private val seen = HashSet<Pair<Long, HierarchyProblem>>()

    fun fresh(warnings: List<HierarchyWarning>): List<HierarchyWarning> =
        warnings.filter { seen.add(it.entityId to it.problem) }
}
