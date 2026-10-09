/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.editor.ecs.EcsWriter

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

/** Reads and writes one kind of component as the native scene format holds it, under its short [name]. */
interface ComponentCodec<C : Component> {
    val name: String
    val type: Class<C>

    fun read(node: JsonNode): C

    fun write(component: C): JsonNode
}

/** A built-in component bound by the runtime's own loader and writer, so defaults and number text match a scene load. */
internal class RuntimeCodec<C : Component>(
    override val name: String,
    override val type: Class<C>,
    private val reader: ComponentReader,
    private val writer: EcsWriter,
) : ComponentCodec<C> {
    override fun read(node: JsonNode): C = reader.read(type, node)

    override fun write(component: C): JsonNode = writer.writeComponent(component)
}

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

