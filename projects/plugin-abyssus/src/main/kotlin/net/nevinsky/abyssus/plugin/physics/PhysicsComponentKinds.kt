/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.physics

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.editor.EditorMessages
import net.nevinsky.abyssus.lib.gdx.editor.components.*
import net.nevinsky.abyssus.lib.gdx.editor.ecs.EcsWriter
import net.nevinsky.abyssus.lib.physics.*
import org.slf4j.helpers.NOPLogger

/** Fixed physics fields supplied by plugin composition, with no platform, GL or native dependencies. */
class PhysicsComponentKinds(private val messages: EditorMessages) {
    private val mapper = JsonProcessor(NOPLogger.NOP_LOGGER).mapper
    private val reader = ComponentReader(mapper, NOPLogger.NOP_LOGGER)
    private val writer = EcsWriter(mapper)

    private fun <C : Component> kind(type: Class<C>, fields: List<ComponentField<C>>, create: () -> C) =
        ComponentKind(type.simpleName, object : ComponentCodec<C> {
            override val name = type.simpleName
            override val type = type
            override fun read(node: JsonNode): C = reader.read(type, node)
            override fun write(component: C): JsonNode = writer.writeComponent(component)
        }, fields, create, messages.message("physics.${type.simpleName}"))

    private fun <C : Component> number(name: String, get: (C) -> Float, set: (C, Float) -> Unit,
                                      min: Double? = null, exclusive: Boolean = false) =
        ComponentField(name, FieldKind.FLOAT, { c: C -> get(c).let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() } },
            { c, text -> set(c, text.trim().toFloat()) }, label = messages.message("physics.${name.substringBefore('.')}"),
            min = min, minExclusive = exclusive)

    private fun <C : Component> vector(name: String, get: (C) -> Vector3, positive: Boolean = false) = listOf(
        number<C>("$name.x", { get(it).x }, { c, v -> get(c).x = v }, if (positive) 0.0 else null, positive),
        number<C>("$name.y", { get(it).y }, { c, v -> get(c).y = v }, if (positive) 0.0 else null, positive),
        number<C>("$name.z", { get(it).z }, { c, v -> get(c).z = v }, if (positive) 0.0 else null, positive),
    )

    val kinds: List<ComponentKind<*>> = listOf(
        kind(RigidBodyComponent::class.java, listOf(
            ComponentField("motionType", FieldKind.CHOICE, { it: RigidBodyComponent -> it.motionType.name },
                { c, t -> c.motionType = MotionType.valueOf(t.trim()) }, choices = MotionType.entries.map { it.name },
                label = messages.message("physics.motionType")),
            number("mass", { it.mass }, { c, v -> c.mass = v }, 0.0, true),
            number("friction", { it.friction }, { c, v -> c.friction = v }),
            number("restitution", { it.restitution }, { c, v -> c.restitution = v }),
            number("linearDamping", { it.linearDamping }, { c, v -> c.linearDamping = v }),
            number("angularDamping", { it.angularDamping }, { c, v -> c.angularDamping = v }),
            number("gravityFactor", { it.gravityFactor }, { c, v -> c.gravityFactor = v }),
        ), ::RigidBodyComponent),
        kind(ColliderComponent::class.java, listOf(
            ComponentField("shape", FieldKind.CHOICE, { it: ColliderComponent -> it.shape.name },
                { c, t -> c.shape = ColliderShape.valueOf(t.trim()) }, choices = ColliderShape.entries.map { it.name },
                label = messages.message("physics.shape")),
            number("radius", { it.radius }, { c, v -> c.radius = v }, 0.0, true),
            number("halfHeight", { it.halfHeight }, { c, v -> c.halfHeight = v }, 0.0, true),
        ) + vector<ColliderComponent>("halfExtents", { it.halfExtents }, true) + vector("offset", { it.offset }), ::ColliderComponent),
        kind(ConstraintComponent::class.java, listOf(
            ComponentField("kind", FieldKind.CHOICE, { it: ConstraintComponent -> it.kind.name },
                { c, t -> c.kind = ConstraintKind.valueOf(t.trim()) }, choices = ConstraintKind.entries.map { it.name },
                label = messages.message("physics.kind")),
            ComponentField("other", FieldKind.ENTITY_REF, { it: ConstraintComponent -> it.other.toString() },
                { c, t -> c.other = t.trim().toInt() }, label = messages.message("physics.other")),
            number("minDistance", { it.minDistance }, { c, v -> c.minDistance = v }, 0.0),
            number("maxDistance", { it.maxDistance }, { c, v -> c.maxDistance = v }, 0.0),
        ) + vector<ConstraintComponent>("anchor", { it.anchor }) + vector("otherAnchor", { it.otherAnchor }) +
            vector("hingeAxis", { it.hingeAxis }), ::ConstraintComponent),
    )
}
