/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.ecs.scene

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.dto.float
import net.nevinsky.abyssus.dto.obj
import net.nevinsky.abyssus.dto.opt
import net.nevinsky.abyssus.dto.text
import net.nevinsky.abyssus.ecs.NO_ENTITY
import net.nevinsky.abyssus.ecs.component.*
import net.nevinsky.abyssus.ecs.render.AssetResolver
import net.nevinsky.abyssus.ecs.render.AssetType
import net.nevinsky.abyssus.ecs.render.RenderComponent
import net.nevinsky.abyssus.ecs.render.RenderableObjectDelegate
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.scene.ColorDto
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
internal fun number(value: Float): JsonNode {
    val v = if (value.isFinite()) value else 0f
    return if (v == Math.rint(v.toDouble()).toFloat() && abs(v) < 1e9f) IntNode.valueOf(v.toInt()) else SceneJson.parse(
        v.toString()
    )
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

    override fun read(node: JsonNode) = PositionComponent(node.opt("lookAtId")?.asInt(NO_ENTITY) ?: NO_ENTITY).also {
        readVector(node.obj("localPosition"), it.localPosition)
        readQuaternion(node.obj("localRotation"), it.localRotation)
        readVector(node.obj("localScale"), it.localScale, 1f)
    }

    override fun write(component: PositionComponent): JsonNode = nodes.objectNode()
        .putIf("localRotation", quaternionDiff(component.localRotation))
        .putId("lookAtId", component.lookAtId)
        .putIf("localPosition", vectorDiff(component.localPosition))
        .putIf("localScale", vectorDiff(component.localScale, 1f))
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
        )
        return LightComponent(light, nested = nestedNode != null || node.obj("color") == null)
    }

    override fun write(component: LightComponent): JsonNode {
        val values = nodes.objectNode()
        values.set<JsonNode>("color", colorNode(component.light.color))
        values.set<JsonNode>("intensity", number(component.light.intensity))
        return if (component.nested) nodes.objectNode().set("light", values) else values
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
        if (clazz != RenderableObjectDelegate.MUNDUS_CLASS) {
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
        out.put("class", RenderableObjectDelegate.MUNDUS_CLASS)
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
    warnings: SceneEcsWarnings = SceneEcsWarnings()
) {
    val all: List<ComponentCodec<*>> = listOf(
        NameCodec(), TypeCodec(), ParentCodec(), PositionCodec(), CameraCodec(), LightCodec(), Point2PointCodec(),
        RenderCodec(resolver, warnings),
    )

    private val byName = all.associateBy { it.name }

    operator fun get(name: String): ComponentCodec<*>? = byName[name]
}
