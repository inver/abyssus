/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs.scene

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.runtime.ecs.EcsWriter
import net.nevinsky.abyssus.runtime.ecs.component.CameraComponent
import net.nevinsky.abyssus.runtime.ecs.component.LightComponent
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import net.nevinsky.abyssus.runtime.ecs.component.ParentComponent
import net.nevinsky.abyssus.runtime.ecs.component.Point2PointPositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.TypeComponent
import net.nevinsky.abyssus.runtime.ecs.render.AssetReference
import net.nevinsky.abyssus.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderableObjectDelegate

private fun <C : Component> floatField(name: String, get: (C) -> Float, set: (C, Float) -> Unit) =
    ComponentField<C>(name, FieldKind.FLOAT, { decimalText(get(it)) }, { c, t -> set(c, t.trim().toFloat()) })

private fun <C : Component> refField(name: String, get: (C) -> Int, set: (C, Int) -> Unit) =
    ComponentField<C>(name, FieldKind.ENTITY_REF, { get(it).toString() }, { c, t -> set(c, t.trim().toInt()) })

/** Model assets are named as the file has them; the editor never resolves them. */
internal val MODEL_ASSETS = AssetResolver { type, name -> AssetReference(name, type) }

internal fun delegateOf(c: RenderComponent) = c.renderable as? RenderableObjectDelegate

/** The built-in kinds the editor models, in the order the view lists them, bound through [reader] and [writer]. */
internal class BuiltInComponentKinds(private val reader: ComponentReader, private val writer: EcsWriter) {
    private inline fun <reified C : Component> kind(
        codecName: String,
        fields: List<ComponentField<C>>,
        noinline create: () -> C,
    ): ComponentKind<C> = ComponentKind(codecName, RuntimeCodec(codecName, C::class.java, reader, writer), fields, create)

    val kinds: List<ComponentKind<*>> = listOf(
        kind<NameComponent>(
            "NameComponent",
            listOf(ComponentField("name", FieldKind.TEXT, { it.name.orEmpty() }, { c, t -> c.name = t.ifEmpty { null } }, optional = true)),
        ) { NameComponent() },
        kind<TypeComponent>(
            "TypeComponent",
            listOf(
                ComponentField(
                    "type", FieldKind.CHOICE, { it.type?.name.orEmpty() },
                    { c, t -> c.type = TypeComponent.Type.entries.firstOrNull { e -> e.name == t } },
                    choices = TypeComponent.Type.entries.map { it.name }, optional = true,
                ),
            ),
        ) { TypeComponent() },
        kind<ParentComponent>("ParentComponent", listOf(refField("parentEntityId", { it.parentEntityId }, { c, v -> c.parentEntityId = v }))) { ParentComponent() },
        kind<PositionComponent>(
            "PositionComponent",
            listOf(
                refField("lookAtId", { it.lookAtId }, { c, v -> c.lookAtId = v; c.lookAtRef = v.takeIf { it >= 0 }?.toString() }),
                floatField("localPosition.x", { it.localPosition.x }, { c, v -> c.localPosition.x = v }),
                floatField("localPosition.y", { it.localPosition.y }, { c, v -> c.localPosition.y = v }),
                floatField("localPosition.z", { it.localPosition.z }, { c, v -> c.localPosition.z = v }),
                floatField("localRotation.x", { it.localRotation.x }, { c, v -> c.localRotation.x = v }),
                floatField("localRotation.y", { it.localRotation.y }, { c, v -> c.localRotation.y = v }),
                floatField("localRotation.z", { it.localRotation.z }, { c, v -> c.localRotation.z = v }),
                floatField("localRotation.w", { it.localRotation.w }, { c, v -> c.localRotation.w = v }),
                floatField("localScale.x", { it.localScale.x }, { c, v -> c.localScale.x = v }),
                floatField("localScale.y", { it.localScale.y }, { c, v -> c.localScale.y = v }),
                floatField("localScale.z", { it.localScale.z }, { c, v -> c.localScale.z = v }),
            ),
        ) { PositionComponent() },
        kind<CameraComponent>(
            "CameraComponent",
            listOf(
                floatField("camera.position.x", { it.camera.position.x }, { c, v -> c.camera.position.x = v }),
                floatField("camera.position.y", { it.camera.position.y }, { c, v -> c.camera.position.y = v }),
                floatField("camera.position.z", { it.camera.position.z }, { c, v -> c.camera.position.z = v }),
                floatField("camera.viewPointPosition.x", { it.camera.direction.x }, { c, v -> c.camera.direction.x = v }),
                floatField("camera.viewPointPosition.y", { it.camera.direction.y }, { c, v -> c.camera.direction.y = v }),
                floatField("camera.viewPointPosition.z", { it.camera.direction.z }, { c, v -> c.camera.direction.z = v }),
                floatField("camera.far", { it.camera.far }, { c, v -> c.camera.far = v }),
                floatField("camera.near", { it.camera.near }, { c, v -> c.camera.near = v }),
                floatField("camera.fieldOfView", { it.camera.fieldOfView }, { c, v -> c.camera.fieldOfView = v }),
            ),
        ) { CameraComponent() },
        kind<LightComponent>(
            "LightComponent",
            listOf(
                floatField("color.r", { it.light.color.r }, { c, v -> c.light.color = c.light.color.copy(r = v) }),
                floatField("color.g", { it.light.color.g }, { c, v -> c.light.color = c.light.color.copy(g = v) }),
                floatField("color.b", { it.light.color.b }, { c, v -> c.light.color = c.light.color.copy(b = v) }),
                floatField("color.a", { it.light.color.a }, { c, v -> c.light.color = c.light.color.copy(a = v) }),
                floatField("intensity", { it.light.intensity }, { c, v -> c.light.intensity = v }),
                floatField("range", { it.light.range }, { c, v -> c.light.range = v }),
                floatField("coneAngle", { it.light.coneAngle }, { c, v -> c.light.coneAngle = v }),
                floatField("edgeSoftness", { it.light.edgeSoftness * 100f }, { c, v -> c.light.edgeSoftness = v / 100f }),
            ),
        ) { LightComponent() },
        kind<Point2PointPositionComponent>(
            "Point2PointPositionComponent",
            listOf(
                refField("entity1Id", { it.entity1Id }, { c, v -> c.entity1Id = v }),
                refField("entity2Id", { it.entity2Id }, { c, v -> c.entity2Id = v }),
            ),
        ) { Point2PointPositionComponent() },
        kind<RenderComponent>(
            "RenderComponent",
            listOf(
                ComponentField(
                    "assetType", FieldKind.CHOICE, { delegateOf(it)?.asset?.type?.name.orEmpty() },
                    { c, t -> delegateOf(c)?.let { d -> d.asset = AssetReference(d.asset.assetName, MetaType.valueOf(t)) } },
                    choices = listOf(MetaType.MODEL, MetaType.TERRAIN).map { it.name },
                ),
                ComponentField(
                    "assetName", FieldKind.ASSET_NAME, { delegateOf(it)?.asset?.assetName.orEmpty() },
                    { c, t -> delegateOf(c)?.let { d -> d.asset = AssetReference(t, d.asset.type) } },
                ),
                ComponentField(
                    "shaderKey", FieldKind.TEXT, { delegateOf(it)?.shaderKey.orEmpty() },
                    { c, t -> delegateOf(c)?.shaderKey = t.ifEmpty { null } }, optional = true,
                ),
            ),
        ) { RenderComponent(RenderableObjectDelegate(AssetReference("", MetaType.MODEL), null)) },
    )
}
