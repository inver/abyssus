/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.plugin

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.physics.PhysicsComponents
import net.nevinsky.abyssus.runtime.schema.ComponentSchemaReader
import net.nevinsky.abyssus.runtime.schema.SchemaJson
import net.nevinsky.abyssus.runtime.schema.SchemaVector
import net.nevinsky.abyssus.editor.content.Rgba
import net.nevinsky.abyssus.editor.scene.SceneContent
import net.nevinsky.abyssus.editor.content.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A segment of the physics overlay; [selected] segments belong to the selected entity and are drawn on top. */
data class OverlaySegment(val from: Vec3, val to: Vec3, val color: Rgba, val selected: Boolean)

/** Colors by motion type, and of constraints. The selected entity's are brighter. */
internal object PhysicsColors {
    val DYNAMIC = Rgba(0.2f, 0.85f, 0.25f, 1f)
    val KINEMATIC = Rgba(0.3f, 0.5f, 1f, 1f)
    val STATIC = Rgba(0.6f, 0.6f, 0.6f, 1f)
    val CONSTRAINT = Rgba(1f, 0.8f, 0.2f, 1f)

    fun brighter(c: Rgba) = Rgba(minOf(1f, c.r * 0.5f + 0.5f), minOf(1f, c.g * 0.5f + 0.5f), minOf(1f, c.b * 0.5f + 0.5f), 1f)
}

private const val CIRCLE_SEGMENTS = 24
private const val ANCHOR_SIZE = 0.15f
private const val DASH = 0.25f

/**
 * The physics overlay as line segments, without GL: each collider as a wireframe of its shape at its entity's pose and
 * offset, each constraint as a line between its anchors with a cross at each one. Poses come from [SceneContent] (so
 * drags and simulated poses show), the physics components from the scene's `ecs` JSON, decoded with their schemas.
 * A height field is drawn as the outline of its terrain, whose size [terrainSize] gives by asset name. A convex hull
 * is drawn as a diamond at its offset, as the overlay has no model geometry.
 */
class PhysicsOverlayGeometry(private val terrainSize: (String) -> Float? = { null }) {
    private val schemas = ComponentSchemaReader().readAll(PhysicsComponents().components(), emptySet()).associateBy { it.name }
    private val json = SchemaJson()

    fun segments(content: SceneContent, ecs: JsonNode?, selectedId: String?): List<OverlaySegment> {
        val entities = ecs?.get("entities") ?: return emptyList()
        val out = ArrayList<OverlaySegment>()
        for ((id, entity) in entities.properties()) {
            val components = entity.get("components") ?: continue
            val selected = id == selectedId
            components.get("ColliderComponent")?.let { node ->
                val collider = decode("ColliderComponent", node)
                val motion = components.get("RigidBodyComponent")?.let { decode("RigidBodyComponent", it)["motionType"] as String } ?: "STATIC"
                val base = when (motion) {
                    "DYNAMIC" -> PhysicsColors.DYNAMIC
                    "KINEMATIC" -> PhysicsColors.KINEMATIC
                    else -> PhysicsColors.STATIC
                }
                val color = if (selected) PhysicsColors.brighter(base) else base
                collider(id, components, collider, content, Sink(out, color, selected))
            }
            components.get("ConstraintComponent")?.let { node ->
                val c = decode("ConstraintComponent", node)
                val other = (c["other"] as Int).takeIf { it != -1 }?.toString()
                val selectedToo = selected || other == selectedId
                val color = if (selectedToo) PhysicsColors.brighter(PhysicsColors.CONSTRAINT) else PhysicsColors.CONSTRAINT
                val a = Vector3(vector(c["anchor"])).mul(transformOf(id, components, content))
                val b = if (other != null) {
                    val otherComponents = entities.get(other)?.get("components")
                    Vector3(vector(c["otherAnchor"])).mul(transformOf(other, otherComponents, content))
                } else Vector3(vector(c["otherAnchor"]))
                val sink = Sink(out, color, selectedToo)
                val slackOrTaut = c["kind"] != "DISTANCE" || a.dst(b) <= (c["maxDistance"] as Float) + 1e-4f
                if (slackOrTaut) sink.line(a, b) else sink.dashed(a, b)
                sink.cross(a, ANCHOR_SIZE)
                sink.cross(b, ANCHOR_SIZE)
            }
        }
        return out
    }

    private fun decode(name: String, node: JsonNode): Map<String, Any> = json.decode(schemas.getValue(name), node)

    private fun vector(value: Any?): Vector3 = (value as SchemaVector).let { Vector3(it.x, it.y, it.z) }

    private fun collider(id: String, components: JsonNode, c: Map<String, Any>, content: SceneContent, sink: Sink) {
        val world = transformOf(id, components, content)
        val scale = world.getScale(Vector3())
        val position = world.getTranslation(Vector3())
        val rotation = world.getRotation(Quaternion(), true)
        val offset = vector(c["offset"]).scl(scale)
        // the shape's frame: the entity's position and rotation, moved by the scaled offset; sizes are scaled below
        val frame = Matrix4().set(position, rotation).translate(offset)
        val largest = max(abs(scale.x), max(abs(scale.y), abs(scale.z)))
        when (c["shape"]) {
            "BOX" -> {
                val h = vector(c["halfExtents"]).scl(abs(scale.x), abs(scale.y), abs(scale.z))
                val corners = listOf(-1f, 1f).flatMap { x -> listOf(-1f, 1f).flatMap { y -> listOf(-1f, 1f).map { z -> Vector3(x * h.x, y * h.y, z * h.z).mul(frame) } } }
                // corners are indexed x * 4 + y * 2 + z; an edge joins corners that differ in one bit
                for (i in 0 until 8) for (bit in listOf(1, 2, 4)) if (i and bit == 0) sink.line(corners[i], corners[i or bit])
            }
            "SPHERE" -> sphere(frame, (c["radius"] as Float) * largest, Vector3(), sink)
            "CAPSULE" -> {
                val r = (c["radius"] as Float) * largest
                val half = (c["halfHeight"] as Float) * largest
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
            "HEIGHT_FIELD" -> {
                val name = content.terrains.firstOrNull { it.entityId == id }?.assetName
                val size = name?.let(terrainSize) ?: return
                val corners = listOf(0f to 0f, size * scale.x to 0f, size * scale.x to size * scale.z, 0f to size * scale.z)
                    .map { (x, z) -> Vector3(x, 0f, z).mul(frame) }
                for (i in corners.indices) sink.line(corners[i], corners[(i + 1) % corners.size])
            }
            else -> {
                val d = 0.25f * largest
                val points = listOf(Vector3(d, 0f, 0f), Vector3(-d, 0f, 0f), Vector3(0f, d, 0f), Vector3(0f, -d, 0f), Vector3(0f, 0f, d), Vector3(0f, 0f, -d))
                    .map { it.mul(frame) }
                for (i in 0 until 2) for (j in 2 until 6) sink.line(points[i], points[j])
                sink.line(points[2], points[4]); sink.line(points[4], points[3]); sink.line(points[3], points[5]); sink.line(points[5], points[2])
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
    private fun transformOf(id: String, components: JsonNode?, content: SceneContent): Matrix4 {
        val position = components?.get("PositionComponent")
        val authored = PositionValues(position)
        content.models.firstOrNull { it.entityId == id }?.transform?.let { return matrix(it.position, it.rotation.let { q -> Quaternion(q.x, q.y, q.z, q.w) }, it.scale) }
        content.terrains.firstOrNull { it.entityId == id }?.transform?.let { return matrix(it.position, Quaternion(it.rotation.x, it.rotation.y, it.rotation.z, it.rotation.w), it.scale) }
        val at = content.entityPositions[id]?.let { Vector3(it.x, it.y, it.z) } ?: authored.position
        return Matrix4().set(at, authored.rotation, authored.scale)
    }

    private fun matrix(p: Vec3, q: Quaternion, s: Vec3) = Matrix4().set(Vector3(p.x, p.y, p.z), q.nor(), Vector3(s.x, s.y, s.z))

    private class PositionValues(node: JsonNode?) {
        val position = node?.get("localPosition").let { Vector3(f(it, "x", 0f), f(it, "y", 0f), f(it, "z", 0f)) }
        val rotation = node?.get("localRotation").let { Quaternion(f(it, "x", 0f), f(it, "y", 0f), f(it, "z", 0f), f(it, "w", 1f)).nor() }
        val scale = node?.get("localScale").let { Vector3(f(it, "x", 1f), f(it, "y", 1f), f(it, "z", 1f)) }

        private fun f(n: JsonNode?, key: String, default: Float) = n?.get(key)?.takeIf { it.isNumber }?.floatValue() ?: default
    }

    private class Sink(val out: MutableList<OverlaySegment>, val color: Rgba, val selected: Boolean) {
        fun line(a: Vector3, b: Vector3) {
            out += OverlaySegment(Vec3(a.x, a.y, a.z), Vec3(b.x, b.y, b.z), color, selected)
        }

        fun dashed(a: Vector3, b: Vector3) {
            val length = a.dst(b)
            val steps = max(1, (length / DASH).toInt())
            for (i in 0 until steps step 2) {
                line(Vector3(a).lerp(b, i.toFloat() / steps), Vector3(a).lerp(b, minOf(1f, (i + 1).toFloat() / steps)))
            }
        }

        fun cross(p: Vector3, size: Float) {
            for (axis in listOf(Vector3.X, Vector3.Y, Vector3.Z)) line(Vector3(p).mulAdd(axis, -size), Vector3(p).mulAdd(axis, size))
        }
    }
}
