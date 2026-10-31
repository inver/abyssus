/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.physics

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.physics.largestScaleAxis
import net.nevinsky.abyssus.lib.physics.scaledBoxHalfExtents
import net.nevinsky.abyssus.lib.physics.scaledHalfHeight
import net.nevinsky.abyssus.lib.physics.scaledRadius
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.components.ComponentReader
import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.physics.*
import org.slf4j.helpers.NOPLogger
import kotlin.math.*

/** A segment of the physics overlay; [selected] segments belong to the selected entity and are drawn on top. */
data class OverlaySegment(val from: Vector3, val to: Vector3, val color: Color, val selected: Boolean)

/** Colors by motion type, and of constraints. The selected entity's are brighter. */
internal object PhysicsColors {
    val DYNAMIC = Color(0.2f, 0.85f, 0.25f, 1f)
    val KINEMATIC = Color(0.3f, 0.5f, 1f, 1f)
    val STATIC = Color(0.6f, 0.6f, 0.6f, 1f)
    val CONSTRAINT = Color(1f, 0.8f, 0.2f, 1f)

    fun brighter(c: Color) =
        Color(minOf(1f, c.r * 0.5f + 0.5f), minOf(1f, c.g * 0.5f + 0.5f), minOf(1f, c.b * 0.5f + 0.5f), 1f)
}

private const val CIRCLE_SEGMENTS = 24
private const val ANCHOR_SIZE = 0.15f
private const val DASH = 0.25f

/**
 * The physics overlay as line segments, without GL: each collider as a wireframe of its shape at its entity's pose and
 * offset, each constraint as a line between its anchors with a cross at each one. Poses come from [SceneContent] (so
 * drags and simulated poses show), the physics components from the scene's typed entities (raw ones decoded with their typed defaults).
 * A height field is drawn as the outline of its terrain, whose size [terrainSize] gives by asset name. A convex hull
 * is drawn as a diamond at its offset, as the overlay has no model geometry.
 */
class PhysicsOverlayGeometry(private val terrainSize: (String) -> Float? = { null }) {

    private val reader = ComponentReader(JsonProcessor(NOPLogger.NOP_LOGGER))

    /**
     * The entity's component of [type]: the bound instance, or, when the scene's registry does not know the physics
     * components, the raw entry of that name decoded with the type's defaults.
     */
    private fun <C : com.badlogic.ashley.core.Component> component(entity: Entity?, type: Class<C>): C? {
        entity ?: return null
        entity.getComponent(type)?.let { return it }
        val raw = entity.getComponent(RawComponentsWrapper::class.java)?.entries
            ?.firstOrNull { it.name.substringAfterLast('.') == type.simpleName } ?: return null
        return runCatchingKeepingCancellation { reader.readText(type, raw.json) }.getOrNull()
    }

    fun segments(content: SceneContent, entities: Map<Long, Entity>, selectedId: String?): List<OverlaySegment> {
        val out = ArrayList<OverlaySegment>()
        for ((key, entity) in entities) {
            val id = key.toString()
            val selected = id == selectedId
            component(entity, ColliderComponent::class.java)?.let { collider ->
                val motion = component(entity, RigidBodyComponent::class.java)?.motionType ?: MotionType.STATIC
                val base = when (motion) {
                    MotionType.DYNAMIC -> PhysicsColors.DYNAMIC
                    MotionType.KINEMATIC -> PhysicsColors.KINEMATIC
                    else -> PhysicsColors.STATIC
                }
                val color = if (selected) PhysicsColors.brighter(base) else base
                collider(id, entity, collider, content, Sink(out, color, selected))
            }
            component(entity, ConstraintComponent::class.java)?.let { c ->
                val other = c.other.takeIf { it != -1 }?.toString()
                val selectedToo = selected || other == selectedId
                val color =
                    if (selectedToo) PhysicsColors.brighter(PhysicsColors.CONSTRAINT) else PhysicsColors.CONSTRAINT
                val a = Vector3(c.anchor).mul(transformOf(id, entity, content))
                val b = if (other != null) {
                    Vector3(c.otherAnchor).mul(transformOf(other, entities[other.toLong()], content))
                } else Vector3(c.otherAnchor)
                val sink = Sink(out, color, selectedToo)
                val slackOrTaut = c.kind != ConstraintKind.DISTANCE || a.dst(b) <= c.maxDistance + 1e-4f
                if (slackOrTaut) sink.line(a, b) else sink.dashed(a, b)
                sink.cross(a, ANCHOR_SIZE)
                sink.cross(b, ANCHOR_SIZE)
            }
        }
        return out
    }


    private fun collider(id: String, entity: Entity, c: ColliderComponent, content: SceneContent, sink: Sink) {
        val world = transformOf(id, entity, content)
        val scale = world.getScale(Vector3())
        val position = world.getTranslation(Vector3())
        val rotation = world.getRotation(Quaternion(), true)
        val offset = Vector3(c.offset).scl(scale)
        // the shape's frame: the entity's position and rotation, moved by the scaled offset; sizes are scaled below
        val frame = Matrix4().set(position, rotation).translate(offset)
        val largest = largestScaleAxis(scale)
        when (c.shape) {
            ColliderShape.BOX -> {
                val h = scaledBoxHalfExtents(c.halfExtents, scale)
                val corners = listOf(-1f, 1f).flatMap { x ->
                    listOf(-1f, 1f).flatMap { y ->
                        listOf(
                            -1f,
                            1f
                        ).map { z -> Vector3(x * h.x, y * h.y, z * h.z).mul(frame) }
                    }
                }
                // corners are indexed x * 4 + y * 2 + z; an edge joins corners that differ in one bit
                for (i in 0 until 8) for (bit in listOf(1, 2, 4)) if (i and bit == 0) sink.line(
                    corners[i],
                    corners[i or bit]
                )
            }

            ColliderShape.SPHERE -> sphere(frame, scaledRadius(c.radius, scale), Vector3(), sink)
            ColliderShape.CAPSULE -> {
                val r = scaledRadius(c.radius, scale)
                val half = scaledHalfHeight(c.halfHeight, scale)
                circle(frame, Vector3(0f, half, 0f), r, Vector3.X, Vector3.Z, sink)
                circle(frame, Vector3(0f, -half, 0f), r, Vector3.X, Vector3.Z, sink)
                for ((x, z) in listOf(r to 0f, -r to 0f, 0f to r, 0f to -r)) {
                    sink.line(Vector3(x, half, z).mul(frame), Vector3(x, -half, z).mul(frame))
                }
                for (axis in listOf(Vector3.X, Vector3.Z)) {
                    arc(frame, Vector3(0f, half, 0f), r, axis, Vector3.Y, sink)
                    arc(frame, Vector3(0f, -half, 0f), r, axis, Vector3(0f, -1f, 0f), sink)
                }
            }

            ColliderShape.HEIGHT_FIELD -> {
                val name = content.terrains.firstOrNull { it.entityId == id }?.assetName
                val size = name?.let(terrainSize) ?: return
                val corners =
                    listOf(0f to 0f, size * scale.x to 0f, size * scale.x to size * scale.z, 0f to size * scale.z)
                        .map { (x, z) -> Vector3(x, 0f, z).mul(frame) }
                for (i in corners.indices) sink.line(corners[i], corners[(i + 1) % corners.size])
            }

            else -> {
                val d = 0.25f * largest
                val points = listOf(
                    Vector3(d, 0f, 0f),
                    Vector3(-d, 0f, 0f),
                    Vector3(0f, d, 0f),
                    Vector3(0f, -d, 0f),
                    Vector3(0f, 0f, d),
                    Vector3(0f, 0f, -d)
                )
                    .map { it.mul(frame) }
                for (i in 0 until 2) for (j in 2 until 6) sink.line(points[i], points[j])
                sink.line(points[2], points[4]); sink.line(points[4], points[3]); sink.line(
                    points[3],
                    points[5]
                ); sink.line(points[5], points[2])
            }
        }
    }

    private fun sphere(frame: Matrix4, r: Float, centre: Vector3, sink: Sink) {
        circle(frame, centre, r, Vector3.X, Vector3.Y, sink)
        circle(frame, centre, r, Vector3.Y, Vector3.Z, sink)
        circle(frame, centre, r, Vector3.X, Vector3.Z, sink)
    }

    private fun circle(frame: Matrix4, centre: Vector3, r: Float, u: Vector3, v: Vector3, sink: Sink) {
        val points = (0..CIRCLE_SEGMENTS).map { i ->
            val a = 2 * PI * i / CIRCLE_SEGMENTS
            Vector3(centre).mulAdd(u, (r * cos(a)).toFloat()).mulAdd(v, (r * sin(a)).toFloat()).mul(frame)
        }
        for (i in 0 until CIRCLE_SEGMENTS) sink.line(points[i], points[i + 1])
    }

    /** Half a circle from -[u] to +[u] through [up]. */
    private fun arc(frame: Matrix4, centre: Vector3, r: Float, u: Vector3, up: Vector3, sink: Sink) {
        val n = CIRCLE_SEGMENTS / 2
        val points = (0..n).map { i ->
            val a = PI * i / n
            Vector3(centre).mulAdd(u, (r * cos(a)).toFloat()).mulAdd(up, (r * sin(a)).toFloat()).mul(frame)
        }
        for (i in 0 until n) sink.line(points[i], points[i + 1])
    }

    /**
     * The entity's transform: where the view shows it (a model, terrain, light or camera placement, or a bare position),
     * with the rotation and scale its `PositionComponent` holds when the view does not place it.
     */
    private fun transformOf(id: String, entity: Entity?, content: SceneContent): Matrix4 {
        val authored = entity?.getComponent(PositionComponent::class.java) ?: PositionComponent()
        content.models.firstOrNull { it.entityId == id }?.transform?.let {
            return matrix(
                it.position,
                it.rotation.let { q -> Quaternion(q.x, q.y, q.z, q.w) },
                it.scale
            )
        }
        content.terrains.firstOrNull { it.entityId == id }?.transform?.let {
            return matrix(
                it.position,
                Quaternion(it.rotation.x, it.rotation.y, it.rotation.z, it.rotation.w),
                it.scale
            )
        }
        val at = content.entityPositions[id]?.let { Vector3(it.x, it.y, it.z) } ?: Vector3(authored.localPosition.x, authored.localPosition.y, authored.localPosition.z)
        val rotation = authored.localRotation.let { Quaternion(it.x, it.y, it.z, it.w).nor() }
        return Matrix4().set(at, rotation, Vector3(authored.localScale.x, authored.localScale.y, authored.localScale.z))
    }

    private fun matrix(p: Vector3, q: Quaternion, s: Vector3) =
        Matrix4().set(Vector3(p.x, p.y, p.z), q.nor(), Vector3(s.x, s.y, s.z))

    private class Sink(val out: MutableList<OverlaySegment>, val color: Color, val selected: Boolean) {
        fun line(a: Vector3, b: Vector3) {
            out += OverlaySegment(Vector3(a.x, a.y, a.z), Vector3(b.x, b.y, b.z), color, selected)
        }

        fun dashed(a: Vector3, b: Vector3) {
            val length = a.dst(b)
            val steps = max(1, (length / DASH).toInt())
            for (i in 0 until steps step 2) {
                line(Vector3(a).lerp(b, i.toFloat() / steps), Vector3(a).lerp(b, minOf(1f, (i + 1).toFloat() / steps)))
            }
        }

        fun cross(p: Vector3, size: Float) {
            for (axis in listOf(Vector3.X, Vector3.Y, Vector3.Z)) line(
                Vector3(p).mulAdd(axis, -size),
                Vector3(p).mulAdd(axis, size)
            )
        }
    }
}
