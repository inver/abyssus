/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.panel

import net.nevinsky.abyssus.lib.core.editor.components.EnabledTarget

/**
 * What the Abyssus Properties panel shows, with no Swing: the header, the sections of labelled rows, and the footer.
 * The plugin maps each [Row] to a component and keeps the commit, refuse and revert behavior of its field editors; every
 * fact the view needs (dim defaults, units, the `game` flag, badges) is decided here and unit-tested here.
 */
data class PanelModel(
    val header: Header,
    val sections: List<Section>,
    /** The footer note under the sections, or empty. */
    val footer: String = "",
)

/** The kind of a header badge. */
enum class BadgeKind { READ_ONLY, UNUSED }

data class Badge(val kind: BadgeKind, val text: String)

/**
 * The top of the panel. [breadcrumb] lists the node's path from the project down to its parent, [iconKey] names the
 * 24 px icon (the keys of the design's icon set, such as `project`, `scene`, `object`, `model`), [subtitle] says what
 * kind of node it is, and [note] is the line some assets show under the title.
 */
data class Header(
    val breadcrumb: List<String>,
    val iconKey: String,
    val title: String,
    val subtitle: String,
    val badges: List<Badge> = emptyList(),
    val note: String = "",
)

/**
 * The `Enabled` check box of a section: [checked] reflects the flag. A component or entity toggle writes through [target];
 * a scene setting writes its [settingKey] (`fogEnabled`) instead. Exactly one of the two is set.
 */
data class EnableToggle(val checked: Boolean, val target: EnabledTarget? = null, val settingKey: String? = null)

/**
 * One framed section. [key] is stable for the node, so a collapsed section stays collapsed across a refresh. [kind] is
 * the component's key in the scene file for a component section (the editors address fields by it), else null.
 * [classHint] is the component's class name; [game] marks a component registered by the game project.
 */
data class Section(
    val key: String,
    val title: String,
    val iconKey: String?,
    val rows: List<Row>,
    val kind: String? = null,
    val game: Boolean = false,
    val classHint: String = "",
    val enable: EnableToggle? = null,
    /** True when the section's component or setting is switched off (its icon, title and fields are drawn dim). */
    val disabled: Boolean = false,
)

/** Addresses an editable field: the component's key in the scene file and the field's name in its kind. */
data class FieldRef(val kind: String, val field: String)

/** One row of a section. [label] is empty for a row that spans the width (a note, a sub-heading, a table). */
sealed interface Row {
    val label: String

    /** True when the file does not write the value, so the default applies and the row is drawn dim. */
    val dim: Boolean
}

/** A text field; [optional] allows the empty text. */
data class TextRow(
    override val label: String, val value: String, override val dim: Boolean = false, val field: FieldRef? = null,
    val optional: Boolean = false, val tooltip: String? = null,
) : Row

/** A number field followed by [unit] when it has one. */
data class NumberRow(
    override val label: String, val value: String, val unit: String = "", override val dim: Boolean = false,
    val field: FieldRef? = null, val tooltip: String? = null,
) : Row

/** One box of a [VectorRow]: its [name] (`x`, `y`, `z`, `w`), its text and the field it edits. */
data class Axis(val name: String, val value: String, val field: FieldRef? = null)

/** A vector with one labelled box per axis: `x y z`, or `x y z w` for a rotation. */
data class VectorRow(override val label: String, val axes: List<Axis>, override val dim: Boolean = false) : Row

/** A combo box over [choices]; [optional] adds the empty choice. */
data class ChoiceRow(
    override val label: String, val value: String, val choices: List<String>, override val dim: Boolean = false,
    val field: FieldRef? = null, val optional: Boolean = false,
) : Row

data class CheckRow(
    override val label: String, val checked: Boolean, val text: String = "", override val dim: Boolean = false,
    val field: FieldRef? = null,
) : Row

/** A color shown as a swatch and its hex text; [axes] are the editable channels (`r g b a`) when it has any. */
data class ColorRow(
    override val label: String, val hex: String, override val dim: Boolean = false, val field: FieldRef? = null,
    val axes: List<Axis> = emptyList(),
) : Row

/**
 * A reference to an asset: the field shows [iconKey] and the [name], with a Browse button when [browse] is set. [choices]
 * lists the asset folders it may name; an empty list makes the row read only.
 */
data class AssetRow(
    override val label: String, val name: String, val iconKey: String, val browse: Boolean = false,
    val choices: List<String> = emptyList(), override val dim: Boolean = false, val field: FieldRef? = null,
) : Row

/** Read-only monospace text. */
data class ReadonlyRow(override val label: String, val value: String, override val dim: Boolean = false) : Row

/** What a [LinkRow] opens. */
enum class LinkTarget { SCREEN_EDITOR }

data class LinkRow(val text: String, val target: LinkTarget) : Row {
    override val label: String get() = ""
    override val dim: Boolean get() = false
}

data class NoteRow(val text: String) : Row {
    override val label: String get() = ""
    override val dim: Boolean get() = false
}

data class SubHeadingRow(val text: String) : Row {
    override val label: String get() = ""
    override val dim: Boolean get() = false
}

/** A read-only table with a header row. */
data class TableRow(val columns: List<String>, val rows: List<List<String>>) : Row {
    override val label: String get() = ""
    override val dim: Boolean get() = false
}

/** A slot where the plugin hosts one of the existing typed editors (terrain, foliage, sky, water, optics, ray settings). */
data class CustomRow(val key: String) : Row {
    override val label: String get() = ""
    override val dim: Boolean get() = false
}
