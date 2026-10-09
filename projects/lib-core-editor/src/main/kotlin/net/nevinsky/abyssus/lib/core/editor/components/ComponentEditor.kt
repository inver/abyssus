/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.components

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.gdx.editor.EditorMessages
import net.nevinsky.abyssus.lib.gdx.editor.document.SceneEntityTree
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.editor.ecs.EcsWriter
import org.slf4j.helpers.NOPLogger
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent

/** What an edit of a scene's JSON tree did. The tree is only touched for [Changed]. */
sealed interface EditResult {
    data object Changed : EditResult

    data object Unchanged : EditResult

    data class Rejected(val reason: String) : EditResult
}

/**
 * Creates, updates and removes the modeled components of an entity in a scene's JSON tree (`ecs.entities.<id>`): the
 * built-in kinds. Values go through the
 * component codecs, so defaults and number text match what a scene load and write do, and an update applies only the
 * keys that really differ onto the file's own component object.
 */
class ComponentEditor(private val messages: EditorMessages, contributions: List<ComponentKind<*>> = emptyList()) {
    private val mapper = JsonProcessor(NOPLogger.NOP_LOGGER).mapper
    private val reader = ComponentReader(mapper, NOPLogger.NOP_LOGGER)
    private val writer = EcsWriter(mapper)

    val kinds: List<ComponentKind<*>> = BuiltInComponentKinds(reader, writer).kinds + contributions

    private val byName = kinds.flatMap { listOf(it.name to it, it.codec.type.name to it) }.toMap()

    fun kindOf(name: String): ComponentKind<*>? = byName[name]

    private fun entities(root: JsonNode): JsonNode? = SceneEntityTree(root).entities()

    private fun componentsOf(root: JsonNode, entityId: String): ObjectNode? = SceneEntityTree(root).components(entityId)

    /** The modeled kinds [entityId] lacks, in the order the view lists them; empty when the entity is missing. */
    fun missingKinds(root: JsonNode, entityId: String): List<ComponentKind<*>> {
        val components = componentsOf(root, entityId) ?: return emptyList()
        return kinds.filter { kind -> components.fieldNames().asSequence().none { byName[it] === kind } }
    }

    /** The fields of the component [kindName] of [entityId] with their values, or null when it is not there. */
    fun read(root: JsonNode, entityId: String, kindName: String): List<FieldValue>? {
        val kind = byName[kindName] ?: return null
        val node = componentsOf(root, entityId)?.get(kindName) ?: return null
        val fields = readFields(kind, node)
        val spotlight = componentsOf(root, entityId)?.get("TypeComponent")?.get("type")?.asText() == "LIGHT_SPOT"
        return if (kindName == "LightComponent" && !spotlight) fields.filterNot { it.field in listOf("coneAngle", "edgeSoftness") } else fields
    }

    private fun <C : Component> readFields(kind: ComponentKind<C>, node: JsonNode): List<FieldValue> {
        val component = kind.codec.read(node)
        return kind.fields.map { FieldValue(it.name, it.kind, it.get(component), it.choices, it.optional, it.label, it.group, it.assetType) }
    }

    /**
     * Adds a [kindName] component to [entityId], started from its defaults and then set from [initial] (field name to
     * text). A render component needs `assetName`; [assets], when given, lists the asset folders it may name.
     */
    fun add(
        root: JsonNode,
        entityId: String,
        kindName: String,
        initial: Map<String, String> = emptyMap(),
        assets: Set<String>? = null,
        assetsByType: Map<String, Set<String>>? = null,
    ): EditResult {
        val components = componentsOf(root, entityId) ?: return rejected("componentEntityMissing", entityId)
        val kind = byName[kindName] ?: return rejected("componentKindUnknown", kindName)
        if (components.fieldNames().asSequence().any { byName[it] === kind }) return rejected("componentAlreadyPresent", entityId, kind.label)
        return addTo(root, components, entityId, kind, initial, Assets(assets, assetsByType))
    }

    /** The asset folders a value may name: [render] for a render component, [byType] for a schema's typed reference. */
    private class Assets(val render: Set<String>?, val byType: Map<String, Set<String>>?)

    private fun <C : Component> addTo(
        root: JsonNode, components: ObjectNode, entityId: String, kind: ComponentKind<C>,
        initial: Map<String, String>, assets: Assets,
    ): EditResult {
        val component = kind.create()
        for ((name, text) in initial) {
            val field = kind.fields.firstOrNull { it.name == name } ?: return rejected("componentFieldUnknown", kind.label, name)
            checkValue(root, entityId, kind, field, text, assets)?.let { return it }
            field.set(component, text)
        }
        if (kind.name == "RenderComponent" && (component as RenderComponent).assetName.isEmpty()) {
            return rejected("componentAssetRequired")
        }
        components.set<JsonNode>(kind.name, kind.codec.write(component))
        return EditResult.Changed
    }

    /** Sets [field] of the component [kindName] of [entityId] to [text]; only the keys that differ are rewritten. */
    fun update(
        root: JsonNode,
        entityId: String,
        kindName: String,
        field: String,
        text: String,
        assets: Set<String>? = null,
        assetsByType: Map<String, Set<String>>? = null,
    ): EditResult {
        val components = componentsOf(root, entityId) ?: return rejected("componentEntityMissing", entityId)
        val kind = byName[kindName] ?: return rejected("componentKindUnknown", kindName)
        val node = components.get(kindName) ?: return rejected("componentMissing", entityId, kind.label)
        return updateIn(root, components, node, entityId, kind, field, text, Assets(assets, assetsByType))
    }

    private fun <C : Component> updateIn(
        root: JsonNode, components: ObjectNode, node: JsonNode, entityId: String, kind: ComponentKind<C>,
        fieldName: String, text: String, assets: Assets,
    ): EditResult {
        val field = kind.fields.firstOrNull { it.name == fieldName } ?: return rejected("componentFieldUnknown", kind.label, fieldName)
        val component = kind.codec.read(node)
        checkValue(root, entityId, kind, field, text, assets)?.let { return it }
        val before = field.get(component)
        val base = kind.codec.write(component)
        field.set(component, text)
        if (field.get(component) == before) return EditResult.Unchanged
        val target = node as? ObjectNode ?: return EditResult.Changed.also { components.set<JsonNode>(kind.name, kind.codec.write(component)) }
        applyDiff(target, base, kind.codec.write(component))
        return EditResult.Changed
    }

    /** Copies onto [target] what differs between [base] and [new]: changed and added keys are set, dropped ones removed. */
    private fun applyDiff(target: ObjectNode, base: JsonNode?, new: JsonNode) {
        val keys = LinkedHashSet<String>()
        base?.fieldNames()?.forEach(keys::add)
        new.fieldNames().forEach(keys::add)
        for (key in keys) {
            val b = base?.get(key)
            val n = new.get(key)
            when {
                b == n -> Unit
                n == null -> target.remove(key)
                n is ObjectNode && target.get(key) is ObjectNode -> applyDiff(target.get(key) as ObjectNode, b, n)
                else -> target.set<JsonNode>(key, n)
            }
        }
    }

    /** Removes the component [kindName] from [entityId], unless another entity still refers to the position it holds. */
    fun remove(root: JsonNode, entityId: String, kindName: String): EditResult {
        val components = componentsOf(root, entityId) ?: return rejected("componentEntityMissing", entityId)
        val kind = byName[kindName] ?: return rejected("componentKindUnknown", kindName)
        if (!components.has(kindName)) return rejected("componentMissing", entityId, kind.label)
        if (kindName == "PositionComponent") {
            referrer(root, entityId)?.let { return rejected("componentReferencedBy", kind.label, entityId, it) }
        }
        components.remove(kindName)
        return EditResult.Changed
    }

    /** An entity other than [entityId] that looks at it, has it as parent or ends a point-to-point at it. */
    private fun referrer(root: JsonNode, entityId: String): String? {
        val wanted = entityId.toIntOrNull() ?: return null
        for ((id, c) in SceneEntityTree(root).componentsById()) {
            if (id == entityId) continue
            val refs = listOf(
                c.get("PositionComponent")?.get("lookAtId"), c.get("ParentComponent")?.get("parentEntityId"),
                c.get("Point2PointPositionComponent")?.get("entity1Id"), c.get("Point2PointPositionComponent")?.get("entity2Id"),
            )
            if (refs.any { it?.asInt(NO_ENTITY) == wanted }) return id
        }
        return null
    }

    private fun checkValue(
        root: JsonNode, entityId: String, kind: ComponentKind<*>, field: ComponentField<*>, text: String, assets: Assets,
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
                if (number == null) reject("componentNotAnInteger", label, text) else checkLimits(field, label, number.toDouble(), value)
            }
            FieldKind.BOOLEAN -> if (value == "true" || value == "false") null else reject("componentNotABoolean", label, text)
            FieldKind.TEXT -> null
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
            FieldKind.ENTITY_REF -> checkReference(root, entityId, kind, field, label, value)
        }
    }

    /** Refuses a number outside the [field]'s declared limits, naming the limit. */
    private fun checkLimits(field: ComponentField<*>, label: String, number: Double, text: String): EditResult.Rejected? {
        val min = field.min
        val max = field.max
        return when {
            min != null && field.minExclusive && number <= min -> reject("componentNotAboveMinimum", label, text, limitText(min))
            min != null && number < min -> reject("componentBelowMinimum", label, text, limitText(min))
            max != null && number > max -> reject("componentAboveMaximum", label, text, limitText(max))
            else -> null
        }
    }

    private fun limitText(limit: Double) = java.math.BigDecimal(limit.toString()).stripTrailingZeros().toPlainString()

    private fun checkReference(
        root: JsonNode, entityId: String, kind: ComponentKind<*>, field: ComponentField<*>, label: String, value: String,
    ): EditResult.Rejected? {
        val target = value.toIntOrNull() ?: return reject("componentNotAnInteger", label, value)
        if (target == NO_ENTITY) return null
        val entities = entities(root)
        if (entities?.has(target.toString()) != true) return reject("componentUnknownEntity", label, target)
        if (kind.name == "ParentComponent" && field.name == "parentEntityId") {
            if (target.toString() == entityId) return reject("componentParentSelf", label)
            var current: Int = target
            val seen = HashSet<Int>()
            while (current != NO_ENTITY && seen.add(current)) {
                val next = SceneEntityTree(root).components(current.toString())?.get("ParentComponent")?.get("parentEntityId")?.asInt(NO_ENTITY) ?: NO_ENTITY
                if (next.toString() == entityId) return reject("componentParentCycle", label, target, entityId)
                current = next
            }
        }
        return null
    }

    private fun reject(key: String, vararg params: Any) = EditResult.Rejected(messages.message(key, *params))

    private fun rejected(key: String, vararg params: Any): EditResult = reject(key, *params)
}
