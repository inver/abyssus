/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.arrange.*

/** What an edit of a scene did. The scene is only touched for [Changed]. */
sealed interface EditResult {
    data object Changed : EditResult

    data object Unchanged : EditResult

    data class Rejected(val reason: String) : EditResult
}

/**
 * Creates, updates and removes the modeled components of an entity of a [SceneDto]: the built-in kinds plus the
 * contributed ones. A field is read and written as text on the component itself, so values match what a scene load and
 * write do.
 */
class ComponentEditor(
    private val messages: EditorMessages,
    contributions: List<ComponentKind<*>> = emptyList(),
) {
    private val references = EntityReferences(contributions)
    private val arrangements = ArrangementEdits(messages)
    val kinds: List<ComponentKind<*>> = BuiltInComponentKinds().kinds + contributions

    private val byName = kinds.flatMap { listOf(it.name to it, it.type.name to it) }.toMap()

    fun kindOf(name: String): ComponentKind<*>? = byName[name]

    private fun <C : Component> componentOf(entity: EntityDto, kind: ComponentKind<C>): C? =
        entity.components.firstOrNull { kind.type.isInstance(it) }?.let(kind.type::cast)

    /** The modeled kinds [entityId] lacks, in the order the view lists them; empty when the entity is missing. */
    fun missingKinds(scene: SceneDto, entityId: Long): List<ComponentKind<*>> {
        val entity = scene.entity(entityId) ?: return emptyList()
        return kinds.filter { kind ->
            kind.name != PathArrangementComponent::class.simpleName && entity.components.none { kind.type.isInstance(it) }
        }
    }

    /** The fields of the component [kindName] of [entityId] with their values, or null when it is not there. */
    fun read(scene: SceneDto, entityId: Long, kindName: String): List<FieldValue>? {
        val kind = byName[kindName] ?: return null
        val entity = scene.entity(entityId) ?: return null
        val fields = fieldsOf(kind, entity) ?: return null
        val spotlight = entity.components.filterIsInstance<net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent>()
            .firstOrNull()?.type == EntityType.LIGHT_SPOT
        return if (kindName == "LightComponent" && !spotlight) fields.filterNot {
            it.field in listOf("coneAngle", "edgeSoftness")
        } else fields
    }

    private fun <C : Component> fieldsOf(kind: ComponentKind<C>, entity: EntityDto): List<FieldValue>? =
        componentOf(entity, kind)?.let { readFields(kind, it) }

    /** A value is shown as a default when it equals what a new component of the kind holds. */
    private fun <C : Component> readFields(kind: ComponentKind<C>, component: C): List<FieldValue> {
        val fresh = kind.create()
        return kind.fields.map {
            val value = it.get(component)
            FieldValue(
                it.name,
                it.kind,
                value,
                it.choices,
                it.optional,
                it.labelKey?.let(messages::message) ?: it.label,
                it.group,
                it.assetType,
                it.tooltipKey?.let(messages::message),
                it.unit,
                value == it.get(fresh),
            )
        }
    }

    /**
     * Adds a [kindName] component to [entityId], started from its defaults and then set from [initial] (field name to
     * text). A render component needs `assetName`; [assets], when given, lists the asset folders it may name.
     */
    fun add(
        scene: SceneDto,
        entityId: Long,
        kindName: String,
        initial: Map<String, String> = emptyMap(),
        assets: Set<String>? = null,
        assetsByType: Map<String, Set<String>>? = null,
    ): EditResult {
        val entity = scene.entity(entityId) ?: return reject("componentEntityMissing", entityId)
        val kind = byName[kindName] ?: return reject("componentKindUnknown", kindName)
        if (entity.components.any { kind.type.isInstance(it) }) return reject(
            "componentAlreadyPresent",
            entityId,
            kind.label
        )
        return addTo(scene, entity, entityId, kind, initial, Assets(assets, assetsByType))
    }

    /** The asset folders a value may name: [render] for a render component, [byType] for a schema's typed reference. */
    private class Assets(val render: Set<String>?, val byType: Map<String, Set<String>>?)

    private fun <C : Component> addTo(
        scene: SceneDto, entity: EntityDto, entityId: Long, kind: ComponentKind<C>,
        initial: Map<String, String>, assets: Assets,
    ): EditResult {
        val component = kind.create()
        for ((name, text) in initial) {
            val field = kind.fields.firstOrNull { it.name == name } ?: return reject(
                "componentFieldUnknown",
                kind.label,
                name
            )
            checkValue(scene, entityId, kind, field, text, assets)?.let { return it }
            field.set(component, text)
        }
        if (kind.name == "RenderComponent" && (component as RenderComponent).assetName.isEmpty()) {
            return reject("componentAssetRequired")
        }
        entity.add(component)
        return EditResult.Changed
    }

    /**
     * Sets [field] of the component [kindName] of [entityId] to [text]. A change to a path arrangement regenerates its
     * copies in the same edit, standing on [heights] (the line's own height without it); a change to the
     * `PositionComponent` of an arranged copy detaches the copy.
     */
    fun update(
        scene: SceneDto,
        entityId: Long,
        kindName: String,
        field: String,
        text: String,
        assets: Set<String>? = null,
        assetsByType: Map<String, Set<String>>? = null,
        heights: GroundHeight? = null,
    ): EditResult {
        val entity = scene.entity(entityId) ?: return reject("componentEntityMissing", entityId)
        val kind = byName[kindName] ?: return reject("componentKindUnknown", kindName)
        return updateIn(
            scene, entity, entityId, kind, field, text, Assets(assets, assetsByType), heights ?: LineHeights()
        )
    }

    private fun <C : Component> updateIn(
        scene: SceneDto, entity: EntityDto, entityId: Long, kind: ComponentKind<C>,
        fieldName: String, text: String, assets: Assets, heights: GroundHeight,
    ): EditResult {
        val component = componentOf(entity, kind) ?: return reject("componentMissing", entityId, kind.label)
        val field = kind.fields.firstOrNull { it.name == fieldName } ?: return reject(
            "componentFieldUnknown",
            kind.label,
            fieldName
        )
        checkValue(scene, entityId, kind, field, text, assets)?.let { return it }
        val before = field.get(component)
        val previous = (component as? PathArrangementComponent)?.let { arrangementAlong(it.points, it) }
        field.set(component, text)
        if (field.get(component) == before) return EditResult.Unchanged
        if (component is PathArrangementComponent && PathLayout().slots(component) == null) {
            field.set(component, before)
            return reject("arrangementTooMany", MAX_ARRANGEMENT_SLOTS.toString())
        }
        if (previous != null) {
            val regenerated = arrangements.regenerate(scene, entityId, previous, heights)
            if (regenerated is ArrangementResult.Rejected) {
                field.set(component, before)
                return EditResult.Rejected(regenerated.reason)
            }
        }
        if (component is PositionComponent) arrangements.detach(scene, entityId)
        return EditResult.Changed
    }

    /** Removes the component [kindName] from [entityId], unless another entity still refers to the position it holds. */
    fun remove(scene: SceneDto, entityId: Long, kindName: String): EditResult {
        val entity = scene.entity(entityId) ?: return reject("componentEntityMissing", entityId)
        val kind = byName[kindName] ?: return reject("componentKindUnknown", kindName)
        if (entity.components.none { kind.type.isInstance(it) }) return reject("componentMissing", entityId, kind.label)
        if (kindName == "PositionComponent") {
            referrer(scene, entityId)?.let { return reject("componentReferencedBy", kind.label, entityId, it) }
        }
        entity.components.removeAll { kind.type.isInstance(it) }
        return EditResult.Changed
    }

    /** An entity other than [entityId] that has a reference field naming it, or null. */
    private fun referrer(scene: SceneDto, entityId: Long): Long? {
        for ((id, entity) in scene.entities()) {
            if (id == entityId) continue
            if (entity.components.any { references.fieldNaming(it, entityId) != null }) return id
        }
        return null
    }

    private fun checkValue(
        scene: SceneDto,
        entityId: Long,
        kind: ComponentKind<*>,
        field: ComponentField<*>,
        text: String,
        assets: Assets,
    ): EditResult.Rejected? {
        val label = "${kind.label} ${field.name}"
        val value = text.trim()
        return when (field.kind) {
            FieldKind.FLOAT -> when {
                value.toFloatOrNull()?.isFinite() != true -> reject("componentNotANumber", label, text)
                kind.name == "LightComponent" && field.name == "range" && value.toFloat() <= 0f ->
                    reject("componentNotPositive", label, text)

                kind.name == "LightComponent" && field.name == "coneAngle" && (value.toFloat() <= 0f || value.toFloat() >= 180f) ->
                    reject("componentConeAngleInvalid", label, text)

                kind.name == "LightComponent" && field.name == "edgeSoftness" && value.toFloat() !in 0f..100f ->
                    reject("componentSoftnessInvalid", label, text)

                else -> checkLimits(field, label, value.toFloat().toDouble(), value)
            }

            FieldKind.INT -> {
                val number = value.toIntOrNull()
                if (number == null) reject("componentNotAnInteger", label, text) else checkLimits(
                    field,
                    label,
                    number.toDouble(),
                    value
                )
            }

            FieldKind.BOOLEAN -> if (value == "true" || value == "false") null else reject(
                "componentNotABoolean",
                label,
                text
            )

            FieldKind.TEXT -> null
            // the meshes of a model are read from the model; whether the id is among them is shown, not enforced
            FieldKind.MESH_ID -> null
            FieldKind.CHOICE ->
                if (value in field.choices || (field.optional && value.isEmpty())) null
                else reject("componentNotAChoice", label, text, field.choices.joinToString(", "))

            FieldKind.ASSET_NAME -> {
                val type = field.assetType
                // a schema's asset reference may be cleared, and names a folder of its declared type
                val known = if (type != null) assets.byType?.get(type).orEmpty() else assets.render
                val checked = if (type != null) assets.byType != null else assets.render != null
                when {
                    value.isEmpty() -> if (type != null) null else reject("componentAssetRequired")
                    checked && value !in known.orEmpty() -> reject("componentAssetUnknown", label, text)
                    else -> null
                }
            }

            FieldKind.ENTITY_REF -> checkReference(scene, entityId, kind, field, label, value)
        }
    }

    /** Refuses a number outside the [field]'s declared limits, naming the limit. */
    private fun checkLimits(
        field: ComponentField<*>,
        label: String,
        number: Double,
        text: String
    ): EditResult.Rejected? {
        val min = field.min
        val max = field.max
        return when {
            min != null && field.minExclusive && number <= min -> reject(
                "componentNotAboveMinimum",
                label,
                text,
                limitText(min)
            )

            min != null && number < min -> reject("componentBelowMinimum", label, text, limitText(min))
            max != null && number > max -> reject("componentAboveMaximum", label, text, limitText(max))
            else -> null
        }
    }

    private fun limitText(limit: Double) = net.nevinsky.abyssus.lib.core.editor.util.numberText(limit)

    private fun checkReference(
        scene: SceneDto,
        entityId: Long,
        kind: ComponentKind<*>,
        field: ComponentField<*>,
        label: String,
        value: String,
    ): EditResult.Rejected? {
        val target = value.toIntOrNull() ?: return reject("componentNotAnInteger", label, value)
        if (target == NO_ENTITY) return null
        if (scene.entity(target.toLong()) == null) return reject("componentUnknownEntity", label, target)
        if (kind.name == "ParentComponent" && field.name == "parentEntityId") {
            if (target.toLong() == entityId) return reject("componentParentSelf", label)
            var current: Int = target
            val seen = HashSet<Int>()
            while (current != NO_ENTITY && seen.add(current)) {
                val next = scene.entity(current.toLong())?.components?.filterIsInstance<ParentComponent>()?.firstOrNull()
                    ?.parentEntityId ?: NO_ENTITY
                if (next.toLong() == entityId) return reject("componentParentCycle", label, target, entityId)
                current = next
            }
        }
        return null
    }

    private fun reject(key: String, vararg params: Any) = EditResult.Rejected(messages.message(key, *params))

}
