/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.schema

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import java.lang.reflect.Modifier
import java.lang.reflect.Field as JavaField

/**
 * Turns an annotated component class into its [ComponentSchema] with plain Java reflection: a new instance (from the
 * no-argument constructor) gives each `@Field`'s default. Fails with a [ComponentRegistrationException] naming the
 * component and the reason.
 */
class ComponentSchemaReader {
    /** The schemas of [classes] in order; a name in [taken] (the built-in components) or used twice fails. */
    fun readAll(classes: List<Class<*>>, taken: Set<String>): List<ComponentSchema> {
        val seen = HashSet<String>()
        return classes.map { type ->
            val schema = read(type)
            if (schema.name in taken) fail(schema.name, "${schema.name} is a built-in component")
            if (!seen.add(schema.name)) fail(schema.name, "${schema.name} is registered twice")
            schema
        }
    }

    fun read(type: Class<*>): ComponentSchema {
        val annotation = type.getAnnotation(SceneComponent::class.java)
            ?: fail(type.name, "${type.name} has no @SceneComponent annotation")
        val name = annotation.name
        if (name.isBlank()) fail(type.name, "${type.name} declares an empty short name")
        val instance = newInstance(type, name)
        val fields = editableFields(type).map { field -> schemaField(name, field, instance) }
        return ComponentSchema(name, type.name, annotation.label.ifEmpty { name.removeSuffix("Component").ifEmpty { name } }, fields)
    }

    private fun schemaField(component: String, field: JavaField, instance: Any): SchemaField {
        val declared = field.getAnnotation(Field::class.java)
        val type = fieldType(component, field)
        val choices = if (type == FieldType.CHOICE) field.type.enumConstants.map { (it as Enum<*>).name } else emptyList()
        // a vector's limits hold for each of its axes
        val numeric = type == FieldType.DECIMAL || type == FieldType.WHOLE || type == FieldType.VECTOR
        return SchemaField(
            name = field.name,
            label = declared.label.ifEmpty { field.name },
            type = type,
            default = schemaValue(type, field.get(instance)),
            group = declared.group,
            min = declared.min.takeIf { numeric && it.isFinite() },
            max = declared.max.takeIf { numeric && it.isFinite() },
            minExclusive = numeric && declared.min.isFinite() && declared.minExclusive,
            choices = choices,
            assetType = field.getAnnotation(AssetRef::class.java)?.type,
        )
    }

    private fun fieldType(component: String, field: JavaField): FieldType {
        val t = field.type
        val entityRef = field.isAnnotationPresent(EntityRef::class.java)
        val assetRef = field.isAnnotationPresent(AssetRef::class.java)
        val type = when {
            t == java.lang.Float.TYPE || t == java.lang.Float::class.java -> FieldType.DECIMAL
            t == Integer.TYPE || t == Integer::class.java -> if (entityRef) FieldType.ENTITY else FieldType.WHOLE
            t == java.lang.Boolean.TYPE || t == java.lang.Boolean::class.java -> FieldType.BOOLEAN
            t == String::class.java -> if (assetRef) FieldType.ASSET else FieldType.TEXT
            t.isEnum -> FieldType.CHOICE
            t == Vector3::class.java -> FieldType.VECTOR
            t == Color::class.java -> FieldType.COLOR
            else -> fail(component, "$component field ${field.name} has the unsupported type ${t.simpleName}")
        }
        if (entityRef && type != FieldType.ENTITY) fail(component, "$component field ${field.name}: @EntityRef needs an Int field")
        if (assetRef && type != FieldType.ASSET) fail(component, "$component field ${field.name}: @AssetRef needs a String field")
        return type
    }

    private fun newInstance(type: Class<*>, name: String): Any {
        val constructor = try {
            type.getDeclaredConstructor()
        } catch (_: NoSuchMethodException) {
            fail(name, "$name has no no-argument constructor, so it cannot be created with its defaults")
        }
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    private fun fail(component: String, reason: String): Nothing =
        throw ComponentRegistrationException("Cannot register component $component: $reason")
}

/** The `@Field`s of [type] in declaration order, made accessible. */
internal fun editableFields(type: Class<*>): List<JavaField> =
    type.declaredFields.filter { it.isAnnotationPresent(Field::class.java) && !Modifier.isStatic(it.modifiers) }
        .onEach { it.isAccessible = true }

/** A Java field's [value] as the schema holds it (see [SchemaField.default]). */
internal fun schemaValue(type: FieldType, value: Any?): Any = when (type) {
    FieldType.DECIMAL -> (value as? Float) ?: 0f
    FieldType.WHOLE -> (value as? Int) ?: 0
    FieldType.ENTITY -> (value as? Int) ?: -1
    FieldType.BOOLEAN -> (value as? Boolean) ?: false
    FieldType.TEXT, FieldType.ASSET -> (value as? String) ?: ""
    FieldType.CHOICE -> (value as? Enum<*>)?.name ?: ""
    FieldType.VECTOR -> (value as? Vector3)?.let { SchemaVector(it.x, it.y, it.z) } ?: SchemaVector(0f, 0f, 0f)
    FieldType.COLOR -> (value as? Color)?.let { SchemaColor(it.r, it.g, it.b, it.a) } ?: SchemaColor(0f, 0f, 0f, 0f)
}

/** A schema [value] as the Java field [field] takes it. */
internal fun javaValue(field: JavaField, type: FieldType, value: Any): Any? = when (type) {
    FieldType.CHOICE -> field.type.enumConstants.firstOrNull { (it as Enum<*>).name == value }
    FieldType.VECTOR -> (value as SchemaVector).let { Vector3(it.x, it.y, it.z) }
    FieldType.COLOR -> (value as SchemaColor).let { Color(it.r, it.g, it.b, it.a) }
    else -> value
}
