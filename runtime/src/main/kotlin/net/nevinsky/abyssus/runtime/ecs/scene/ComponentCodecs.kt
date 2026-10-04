/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs.scene

import net.nevinsky.abyssus.runtime.ecs.render.MUNDUS_RENDERABLE_OBJECT_CLASS
import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.assets.json.float
import net.nevinsky.abyssus.assets.json.obj
import net.nevinsky.abyssus.assets.json.opt
import net.nevinsky.abyssus.assets.json.text
import net.nevinsky.abyssus.runtime.ecs.NO_ENTITY
import net.nevinsky.abyssus.runtime.ecs.component.*
import net.nevinsky.abyssus.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.runtime.ecs.render.AssetType
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderableObjectDelegate
import com.fasterxml.jackson.databind.node.DecimalNode
import java.math.BigDecimal
import net.nevinsky.abyssus.runtime.scene.ColorDto
import kotlin.math.abs

/** Reads and writes one kind of component as the Mundus scene format holds it, under its short [name]. */
interface ComponentCodec<C : Component> {
    val name: String
    val type: Class<C>

    fun read(node: JsonNode): C

    fun write(component: C): JsonNode
}

private val nodes = JsonNodeFactory.instance

/** A float as the scene file writes it: whole numbers without a fraction, others with the shortest float text. */
fun number(value: Float): JsonNode {
    val v = if (value.isFinite()) value else 0f
    return if (v == Math.rint(v.toDouble()).toFloat() && abs(v) < 1e9f) IntNode.valueOf(v.toInt()) else DecimalNode(BigDecimal(v.toString()))
}

/** An object of the [fields] that differ from their default; null when none do. */
private fun diff(vararg fields: Triple<String, Float, Float>): ObjectNode? {
    val out = nodes.objectNode()
    for ((name, value, default) in fields) if (value != default) out.set<JsonNode>(name, number(value))
    return out.takeIf { it.size() > 0 }
}

internal fun readVector(node: JsonNode?, into: Vector3, default: Float = 0f): Vector3 {
    node ?: return into
    return into.set(node.float("x") ?: default, node.float("y") ?: default, node.float("z") ?: default)
}

internal fun vectorDiff(v: Vector3, default: Float = 0f) =
    diff(Triple("x", v.x, default), Triple("y", v.y, default), Triple("z", v.z, default))

internal fun readQuaternion(node: JsonNode?, into: Quaternion): Quaternion {
    node ?: return into
    return into.set(node.float("x") ?: 0f, node.float("y") ?: 0f, node.float("z") ?: 0f, node.float("w") ?: 1f)
}

internal fun quaternionDiff(q: Quaternion) =
    diff(Triple("x", q.x, 0f), Triple("y", q.y, 0f), Triple("z", q.z, 0f), Triple("w", q.w, 1f))

internal fun readColor(node: JsonNode?, default: ColorDto): ColorDto =
    node?.let { ColorDto(it.float("r") ?: 0f, it.float("g") ?: 0f, it.float("b") ?: 0f, it.float("a") ?: 0f) }
        ?: default

internal fun colorNode(c: ColorDto): ObjectNode = nodes.objectNode()
    .set<ObjectNode>("r", number(c.r)).set<ObjectNode>("g", number(c.g))
    .set<ObjectNode>("b", number(c.b)).set<ObjectNode>("a", number(c.a))

private fun ObjectNode.putIf(name: String, node: JsonNode?) = apply { if (node != null) set<JsonNode>(name, node) }

private fun ObjectNode.putId(name: String, id: Int) =
    apply { if (id != NO_ENTITY) set<JsonNode>(name, IntNode.valueOf(id)) }

class NameCodec : ComponentCodec<NameComponent> {
    override val name = "NameComponent"
    override val type = NameComponent::class.java
    override fun read(node: JsonNode) = NameComponent(node.text("name"))
    override fun write(component: NameComponent): JsonNode =
        nodes.objectNode().apply { component.name?.let { put("name", it) } }
}

class TypeCodec : ComponentCodec<TypeComponent> {
    override val name = "TypeComponent"
    override val type = TypeComponent::class.java
    override fun read(node: JsonNode) =
        TypeComponent(node.text("type")?.let { t -> TypeComponent.Type.entries.firstOrNull { it.name == t } })

    override fun write(component: TypeComponent): JsonNode =
        nodes.objectNode().apply { component.type?.let { put("type", it.name) } }
}

class ParentCodec : ComponentCodec<ParentComponent> {
    override val name = "ParentComponent"
    override val type = ParentComponent::class.java
    override fun read(node: JsonNode) = ParentComponent(node.opt("parentEntityId")?.asInt(NO_ENTITY) ?: NO_ENTITY)
    override fun write(component: ParentComponent): JsonNode =
        nodes.objectNode().putId("parentEntityId", component.parentEntityId)
}

/** Position, rotation and scale: only the values that differ from the defaults are written, as Mundus does. */
class PositionCodec : ComponentCodec<PositionComponent> {
    override val name = "PositionComponent"
    override val type = PositionComponent::class.java

    override fun read(node: JsonNode): PositionComponent {
        val lookAt = node.opt("lookAtId")
        val (id, ref) = decodeLookAt(lookAt)
        return PositionComponent(id).also {
            it.lookAtRef = ref
            it.lookAtSource = lookAt?.deepCopy()
            readVector(node.obj("localPosition"), it.localPosition)
            readQuaternion(node.obj("localRotation"), it.localRotation)
            readVector(node.obj("localScale"), it.localScale, 1f)
        }
    }

    override fun write(component: PositionComponent): JsonNode {
        val out = nodes.objectNode()
            .putIf("localRotation", quaternionDiff(component.localRotation))
        // the file's own node while the reference is as it was read (`"3"`, `"-1"`, `"h"`, `3`); an edit writes an integer
        val source = component.lookAtSource
        if (source != null && decodeLookAt(source) == (component.lookAtId to component.lookAtRef)) out.set<JsonNode>("lookAtId", source.deepCopy())
        else out.putId("lookAtId", component.lookAtId)
        return out
            .putIf("localPosition", vectorDiff(component.localPosition))
            .putIf("localScale", vectorDiff(component.localScale, 1f))
    }

    /** A `lookAtId` node as the numeric id (`-1` when none or not a number) and the reference text (null for none or `-1`). */
    private fun decodeLookAt(node: JsonNode?): Pair<Int, String?> = when {
        node == null -> NO_ENTITY to null
        node.isIntegralNumber -> node.asInt(NO_ENTITY).let { it to if (it == NO_ENTITY || it < 0) null else it.toString() }
        node.isTextual -> node.asText().let { t -> (t.trim().toIntOrNull() ?: NO_ENTITY) to t.takeIf { it.isNotBlank() && it != "-1" } }
        else -> node.asInt(NO_ENTITY) to null
    }
}

class CameraCodec : ComponentCodec<CameraComponent> {
    override val name = "CameraComponent"
    override val type = CameraComponent::class.java

    override fun read(node: JsonNode) = CameraComponent().also {
        val camera = node.obj("camera") ?: return@also
        val c = it.camera
        readVector(camera.obj("viewPointPosition"), c.direction)
        readVector(camera.obj("position"), c.position)
        camera.float("far")?.let { v -> c.far = v }
        camera.float("near")?.let { v -> c.near = v }
        camera.float("fieldOfView")?.let { v -> c.fieldOfView = v }
    }

    override fun write(component: CameraComponent): JsonNode {
        val c = component.camera
        val camera = nodes.objectNode()
            .putIf("viewPointPosition", vectorDiff(c.direction))
            .putIf("position", vectorDiff(c.position))
        camera.set<JsonNode>("far", number(c.far))
        camera.set<JsonNode>("near", number(c.near))
        camera.set<JsonNode>("fieldOfView", number(c.fieldOfView))
        return nodes.objectNode().set("camera", camera)
    }
}

/** Color and intensity, either directly in the component or in a nested `light` object ([LightComponent.nested]). */
class LightCodec : ComponentCodec<LightComponent> {
    override val name = "LightComponent"
    override val type = LightComponent::class.java

    override fun read(node: JsonNode): LightComponent {
        val nestedNode = node.obj("light")
        val values = nestedNode ?: node
        val defaults = LightData()
        val light = LightData(
            readColor(values.obj("color"), defaults.color),
            values.float("intensity") ?: defaults.intensity,
            values.float("range") ?: defaults.range,
            values.float("coneAngle") ?: defaults.coneAngle,
            values.float("edgeSoftness") ?: defaults.edgeSoftness,
        )
        return LightComponent(light, nested = nestedNode != null || listOf("color", "intensity", "range", "coneAngle", "edgeSoftness").none(node::has))
            .also { it.source = node.deepCopy() }
    }

    override fun write(component: LightComponent): JsonNode {
        val root = (component.source as? ObjectNode)?.deepCopy() ?: nodes.objectNode()
        val values = if (component.nested) root.obj("light")?.deepCopy() ?: nodes.objectNode() else root
        val previous = component.source?.let { read(it).light }
        if (previous == null || previous.color != component.light.color) values.set<JsonNode>("color", colorNode(component.light.color))
        if (previous == null || previous.intensity != component.light.intensity) values.set<JsonNode>("intensity", number(component.light.intensity))
        fun optional(key: String, value: Float, default: Float, old: Float?) {
            if (value == default) values.remove(key)
            else if (old != value) values.set<JsonNode>(key, number(value))
        }
        optional("range", component.light.range, LIGHT_RANGE, previous?.range)
        optional("coneAngle", component.light.coneAngle, LIGHT_CONE_ANGLE, previous?.coneAngle)
        optional("edgeSoftness", component.light.edgeSoftness, LIGHT_EDGE_SOFTNESS, previous?.edgeSoftness)
        return if (component.nested) root.set("light", values) else values
    }
}

class Point2PointCodec : ComponentCodec<Point2PointPositionComponent> {
    override val name = "Point2PointPositionComponent"
    override val type = Point2PointPositionComponent::class.java

    override fun read(node: JsonNode) = Point2PointPositionComponent(
        node.opt("entity1Id")?.asInt(NO_ENTITY) ?: NO_ENTITY,
        node.opt("entity2Id")?.asInt(NO_ENTITY) ?: NO_ENTITY,
    )

    override fun write(component: Point2PointPositionComponent): JsonNode = nodes.objectNode()
        .putId("entity1Id", component.entity1Id)
        .putId("entity2Id", component.entity2Id)
}

/**
 * The render component: a Mundus `RenderableObjectDelegate` resolves its asset through [resolver]; any other
 * renderable (editor-only delegates) or an asset the project lacks loads without a renderable and keeps the file's
 * `renderable` object, which is written back.
 */
class RenderCodec(private val resolver: AssetResolver, private val warnings: SceneEcsWarnings) :
    ComponentCodec<RenderComponent> {
    override val name = "RenderComponent"
    override val type = RenderComponent::class.java

    override fun read(node: JsonNode): RenderComponent {
        val raw = node.obj("renderable") ?: return RenderComponent()
        val clazz = raw.text("class")
        if (clazz != MUNDUS_RENDERABLE_OBJECT_CLASS) {
            warnings.warn("renderable class $clazz is not supported and is kept unchanged")
            return RenderComponent(null, raw)
        }
        val asset = raw.obj("asset")
        val assetType = asset?.text("type")?.let { t -> AssetType.entries.firstOrNull { it.name == t } }
        val assetName = asset?.text("assetName")
        if (assetType == null || assetName == null) {
            warnings.warn("render asset ${asset ?: "(missing)"} names no MODEL or TERRAIN asset")
            return RenderComponent(null, raw)
        }
        val resolved = resolver.resolve(assetType, assetName)
        if (resolved == null) {
            warnings.warn("render asset $assetType $assetName has no folder in the project assets")
            return RenderComponent(null, raw)
        }
        return RenderComponent(RenderableObjectDelegate(resolved, raw.text("shaderKey")), raw)
    }

    override fun write(component: RenderComponent): JsonNode {
        val renderable = component.renderable
        val raw = component.raw
        if (renderable !is RenderableObjectDelegate) return nodes.objectNode().putIf("renderable", raw)
        val out = (raw as? ObjectNode)?.deepCopy() ?: nodes.objectNode()
        out.put("class", MUNDUS_RENDERABLE_OBJECT_CLASS)
        renderable.shaderKey?.let { out.put("shaderKey", it) }
        out.set<JsonNode>(
            "asset",
            nodes.objectNode().put("type", renderable.asset.type.name).put("assetName", renderable.asset.assetName),
        )
        return nodes.objectNode().set("renderable", out)
    }
}

/** The components of a scene file the plugin models, by the short name the file uses. */
class ComponentCodecs(
    resolver: AssetResolver = AssetResolver { _, _ -> null },
    warnings: SceneEcsWarnings = SceneEcsWarnings(net.nevinsky.abyssus.assets.AssetLog { _, _ -> })
) {
    val all: List<ComponentCodec<*>> = listOf(
        NameCodec(), TypeCodec(), ParentCodec(), PositionCodec(), CameraCodec(), LightCodec(), Point2PointCodec(),
        RenderCodec(resolver, warnings),
    )

    private val byName = all.associateBy { it.name }

    operator fun get(name: String): ComponentCodec<*>? = byName[name]

    /** The component [name] read from [node]; [C] must be the component class of that codec. */
    @Suppress("UNCHECKED_CAST")
    fun <C : Component> read(name: String, node: JsonNode): C = (byName.getValue(name) as ComponentCodec<C>).read(node)
}
