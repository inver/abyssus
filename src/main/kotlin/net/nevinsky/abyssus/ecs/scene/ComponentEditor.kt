/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs.scene

import net.nevinsky.abyssus.runtime.ecs.scene.*
import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.runtime.ecs.NO_ENTITY
import net.nevinsky.abyssus.runtime.ecs.component.CameraComponent
import net.nevinsky.abyssus.runtime.ecs.component.LightComponent
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import net.nevinsky.abyssus.runtime.ecs.component.ParentComponent
import net.nevinsky.abyssus.runtime.ecs.component.Point2PointPositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.TypeComponent
import net.nevinsky.abyssus.runtime.ecs.render.AssetReference
import net.nevinsky.abyssus.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.runtime.ecs.render.AssetType
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderableObjectDelegate
import net.nevinsky.abyssus.runtime.schema.ComponentSchema
import net.nevinsky.abyssus.runtime.schema.FieldType
import net.nevinsky.abyssus.runtime.schema.SchemaColor
import net.nevinsky.abyssus.runtime.schema.SchemaField
import net.nevinsky.abyssus.runtime.schema.SchemaJson
import net.nevinsky.abyssus.runtime.schema.SchemaVector

/** What an edit of a scene's JSON tree did. The tree is only touched for [Changed]. */
sealed interface EditResult {
    data object Changed : EditResult

    data object Unchanged : EditResult

    data class Rejected(val reason: String) : EditResult
}

enum class FieldKind { FLOAT, INT, BOOLEAN, TEXT, CHOICE, ENTITY_REF, ASSET_NAME }

/** One editable value of a component, read and written as text. */
class ComponentField<C : Component>(
    val name: String,
    val kind: FieldKind,
    val get: (C) -> String,
    val set: (C, String) -> Unit,
    /** The values a [FieldKind.CHOICE] takes; an optional field also accepts the empty text. */
    val choices: List<String> = emptyList(),
    val optional: Boolean = false,
    /** What the panel shows for the field, and the group it is listed under (none when empty). */
    val label: String = name,
    val group: String = "",
    /** Limits of a number; [minExclusive] makes [min] itself refused. */
    val min: Double? = null,
    val max: Double? = null,
    val minExclusive: Boolean = false,
    /** For a schema's asset reference: the meta type the asset folder must have. */
    val assetType: String? = null,
)

/** A modeled kind of component: its file name, how to read and write it, how a new one starts and what it holds. */
class ComponentKind<C : Component>(
    val name: String,
    internal val codec: ComponentCodec<C>,
    val fields: List<ComponentField<C>>,
    internal val create: () -> C,
    /** The name the view shows: by default the file name without the `Component` suffix. */
    val label: String = name.removeSuffix("Component").ifEmpty { name },
)

/** A field of an entity's component as the panel shows it. */
data class FieldValue(
    val field: String, val kind: FieldKind, val value: String, val choices: List<String>, val optional: Boolean,
    val label: String = field, val group: String = "", val assetType: String? = null,
)

/** A schema-declared component as the editor holds it: its values by field name (see [SchemaField.default]). */
class SchemaValues(val values: MutableMap<String, Any>) : Component

/** Reads and writes a schema-declared component through [SchemaJson], with no game class. */
class SchemaCodec(val schema: ComponentSchema, private val json: SchemaJson) : ComponentCodec<SchemaValues> {
    override val name get() = schema.name
    override val type = SchemaValues::class.java
    override fun read(node: JsonNode) = SchemaValues(json.decode(schema, node))
    override fun write(component: SchemaValues): JsonNode = json.encode(schema, component.values)
}

private fun f(v: Float) = number(v).asText()

private fun <C : Component> floatField(name: String, get: (C) -> Float, set: (C, Float) -> Unit) =
    ComponentField<C>(name, FieldKind.FLOAT, { f(get(it)) }, { c, t -> set(c, t.trim().toFloat()) })

private fun <C : Component> refField(name: String, get: (C) -> Int, set: (C, Int) -> Unit) =
    ComponentField<C>(name, FieldKind.ENTITY_REF, { get(it).toString() }, { c, t -> set(c, t.trim().toInt()) })

private val MODEL_ASSETS = AssetResolver { type, name -> AssetReference(name, type) }

private fun delegateOf(c: RenderComponent) = c.renderable as? RenderableObjectDelegate

/** The editor fields of a schema [field]: one per value, a vector or color as dotted decimals (`leadout.x`). */
private fun schemaFields(field: SchemaField): List<ComponentField<SchemaValues>> {
    fun <T : Any> one(kind: FieldKind, show: (T) -> String, parse: (String) -> Any, choices: List<String> = emptyList(), optional: Boolean = false) =
        ComponentField<SchemaValues>(
            field.name, kind, { @Suppress("UNCHECKED_CAST") show(it.values[field.name] as T) }, { c, t -> c.values[field.name] = parse(t) },
            choices, optional, field.label, field.group, field.min, field.max, field.minExclusive, field.assetType,
        )
    fun parts(keys: List<String>, read: (Any) -> List<Float>, make: (List<Float>) -> Any) = keys.mapIndexed { i, key ->
        ComponentField<SchemaValues>(
            "${field.name}.$key", FieldKind.FLOAT, { f(read(it.values.getValue(field.name))[i]) },
            { c, t -> c.values[field.name] = make(read(c.values.getValue(field.name)).toMutableList().also { p -> p[i] = t.trim().toFloat() }) },
            label = "${field.label} $key", group = field.group,
        )
    }
    return when (field.type) {
        FieldType.DECIMAL -> listOf(one<Float>(FieldKind.FLOAT, ::f, { it.trim().toFloat() }))
        FieldType.WHOLE -> listOf(one<Int>(FieldKind.INT, Int::toString, { it.trim().toInt() }))
        FieldType.BOOLEAN -> listOf(one<Boolean>(FieldKind.BOOLEAN, Boolean::toString, { it.trim().toBooleanStrict() }))
        FieldType.TEXT -> listOf(one<String>(FieldKind.TEXT, { it }, { it }, optional = true))
        FieldType.CHOICE -> listOf(one<String>(FieldKind.CHOICE, { it }, { it.trim() }, field.choices))
        FieldType.ENTITY -> listOf(one<Int>(FieldKind.ENTITY_REF, Int::toString, { it.trim().toInt() }))
        FieldType.ASSET -> listOf(one<String>(FieldKind.ASSET_NAME, { it }, { it.trim() }, optional = true))
        FieldType.VECTOR -> parts(listOf("x", "y", "z"), { (it as SchemaVector).let { v -> listOf(v.x, v.y, v.z) } }) { SchemaVector(it[0], it[1], it[2]) }
        FieldType.COLOR -> parts(listOf("r", "g", "b", "a"), { (it as SchemaColor).let { c -> listOf(c.r, c.g, c.b, c.a) } }) { SchemaColor(it[0], it[1], it[2], it[3]) }
    }
}

/**
 * Creates, updates and removes the modeled components of an entity in a scene's JSON tree (`ecs.entities.<id>`): the
 * built-in kinds and one kind per component of [schemas] (whose built-in names are ignored). Values go through the
 * component codecs, so defaults and number text match what a scene load and write do, and an update applies only the
 * keys that really differ onto the file's own component object.
 */
class ComponentEditor(schemas: List<ComponentSchema> = emptyList(), private val json: SchemaJson = SchemaJson()) {
    private val codecs = ComponentCodecs(MODEL_ASSETS)

    private fun <C : Component> kind(
        codecName: String,
        fields: List<ComponentField<C>>,
        create: () -> C,
    ): ComponentKind<C> {
        @Suppress("UNCHECKED_CAST")
        return ComponentKind(codecName, codecs[codecName] as ComponentCodec<C>, fields, create)
    }

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
                    { c, t -> delegateOf(c)?.let { d -> d.asset = AssetReference(d.asset.assetName, AssetType.valueOf(t)) } },
                    choices = AssetType.entries.map { it.name },
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
        ) { RenderComponent(RenderableObjectDelegate(AssetReference("", AssetType.MODEL), null)) },
    ) + schemas.filter { it.name !in BUILT_IN_COMPONENTS }.map { schema ->
        ComponentKind(schema.name, SchemaCodec(schema, json), schema.fields.flatMap(::schemaFields), {
            SchemaValues(schema.fields.associateTo(LinkedHashMap()) { it.name to it.default })
        }, schema.label)
    }

    private val byName = kinds.associateBy { it.name }

    fun kindOf(name: String): ComponentKind<*>? = byName[name]

    private fun entities(root: JsonNode): JsonNode? = SceneEcsPaths().entities(root)

    private fun componentsOf(root: JsonNode, entityId: String): ObjectNode? = SceneEcsPaths().components(root, entityId)

    /** The modeled kinds [entityId] lacks, in the order the view lists them; empty when the entity is missing. */
    fun missingKinds(root: JsonNode, entityId: String): List<ComponentKind<*>> {
        val components = componentsOf(root, entityId) ?: return emptyList()
        return kinds.filter { !components.has(it.name) }
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
        if (components.has(kindName)) return rejected("componentAlreadyPresent", entityId, kind.label)
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
        if (kind.name == "RenderComponent" && delegateOf(component as RenderComponent)?.asset?.assetName.isNullOrEmpty()) {
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
        if (component is RenderComponent && delegateOf(component) == null) return rejected("componentRenderNotEditable")
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
        for ((id, entity) in entities(root)?.properties().orEmpty()) {
            if (id == entityId) continue
            val c = SceneEcsPaths().componentsOf(entity) ?: continue
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
                val next = SceneEcsPaths().componentsOf(entities.get(current.toString()))?.get("ParentComponent")?.get("parentEntityId")?.asInt(NO_ENTITY) ?: NO_ENTITY
                if (next.toString() == entityId) return reject("componentParentCycle", label, target, entityId)
                current = next
            }
        }
        return null
    }

    private fun reject(key: String, vararg params: Any) = EditResult.Rejected(AbyssusBundle.message(key, *params))

    private fun rejected(key: String, vararg params: Any): EditResult = reject(key, *params)
}
