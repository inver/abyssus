/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import java.math.BigDecimal
import kotlin.math.abs

/** [MESH_ID] is a choice among the meshes of the model the component names; the choices come from the model, not the kind. */
enum class FieldKind { FLOAT, INT, BOOLEAN, TEXT, CHOICE, ENTITY_REF, ASSET_NAME, MESH_ID }

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
    /** Keys of [EditorMessages] for the label and tooltip the panel shows instead of [label] (no tooltip when null). */
    val labelKey: String? = null,
    val tooltipKey: String? = null,
    /** The unit the panel shows after a number (`kg`, `m`, `N`, `s`, `m²`, `°`), or empty. */
    val unit: String = "",
)

/** A component decimal as the scene files spell it (`22`, not `22.0`). */
internal fun decimalText(v: Float): String {
    val finite = if (v.isFinite()) v else 0f
    return if (finite == Math.rint(finite.toDouble()).toFloat() && abs(finite) < 1e9f) finite.toInt().toString()
    else BigDecimal(finite.toString()).toString()
}

/** A modeled kind of component: its file name, its class, how a new one starts and what it holds. */
class ComponentKind<C : Component>(
    val name: String,
    val type: Class<C>,
    val fields: List<ComponentField<C>>,
    internal val create: () -> C,
    /** The name the view shows: by default the file name without the `Component` suffix. */
    val label: String = name.removeSuffix("Component").ifEmpty { name },
)

/** A field of an entity's component as the panel shows it. */
data class FieldValue(
    val field: String, val kind: FieldKind, val value: String, val choices: List<String>, val optional: Boolean,
    val label: String = field, val group: String = "", val assetType: String? = null,
    /** Help text for the field, or null. */
    val tooltip: String? = null,
    /** The unit shown after a number, or empty. */
    val unit: String = "",
    /** True when the file does not write this value, so the default applies; never true for a value the file holds. */
    val isDefault: Boolean = false,
)

