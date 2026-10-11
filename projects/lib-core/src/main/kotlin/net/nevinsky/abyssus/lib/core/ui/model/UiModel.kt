/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.model

import com.fasterxml.jackson.databind.JsonNode

/** The `additional.ui.schemaVersion` this build reads and writes. */
const val UI_SCHEMA_VERSION = 1
const val UI_MAX_DEPTH = 64
const val UI_MAX_WIDGETS = 10_000
const val UI_MAX_IMAGE_SIDE = 8192
const val UI_DEFAULT_REFERENCE_WIDTH = 1280f
const val UI_DEFAULT_REFERENCE_HEIGHT = 720f

/** True when an image of [width] x [height] pixels is within the per-side limit (positive sizes only). */
fun uiImageWithinLimits(width: Int, height: Int): Boolean =
    width in 1..UI_MAX_IMAGE_SIDE && height in 1..UI_MAX_IMAGE_SIDE

enum class UiScreenKind(val json: String) { SCREEN("screen"), OVERLAY("overlay") }

enum class UiWidgetType(val json: String, val container: Boolean) {
    PANEL("panel", true), ROW("row", true), COLUMN("column", true),
    LABEL("label", false), IMAGE("image", false), BUTTON("button", false),
    PROGRESS_BAR("progressBar", false), SLIDER("slider", false),
    CHECKBOX("checkbox", false), TEXT_FIELD("textField", false),
}

enum class UiAnchor(val json: String) {
    TOP_LEFT("topLeft"), TOP("top"), TOP_RIGHT("topRight"),
    LEFT("left"), CENTER("center"), RIGHT("right"),
    BOTTOM_LEFT("bottomLeft"), BOTTOM("bottom"), BOTTOM_RIGHT("bottomRight"),
}

enum class UiAlign(val json: String) { START("start"), CENTER("center"), END("end"), STRETCH("stretch") }

enum class UiBindingType(val json: String) { NUMBER("number"), BOOLEAN("boolean"), STRING("string") }

enum class UiBindingAccess(val json: String) { READ_ONLY("readOnly"), WRITE("write") }

enum class UiResourceKind(val json: String) { IMAGE("image"), BITMAP_FONT("bitmapFont") }

enum class UiActionType(val json: String) {
    OPEN_SCREEN("openScreen"), BACK("back"), SHOW_OVERLAY("showOverlay"),
    CLOSE_OVERLAY("closeOverlay"), EMIT_ACTION("emitAction"),
}

/**
 * Layout of one widget in logical, top-left-origin units. Fields not authored keep their defaults: [anchor] and
 * [pivot] apply inside a panel parent, [grow] inside a row or column, [padding], [spacing] and [align] to a container's
 * own children.
 */
data class UiLayout(
    val anchor: UiAnchor? = null,
    val pivot: UiAnchor? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float? = null,
    val height: Float? = null,
    val grow: Float = 0f,
    val padding: Float = 0f,
    val spacing: Float = 0f,
    val align: UiAlign = UiAlign.START,
)

/** One action of a widget. [type] is null for a kind this build does not know; [raw] keeps the authored object. */
data class UiAction(
    val rawType: String,
    val type: UiActionType?,
    val assetName: String? = null,
    val screenId: String? = null,
    val name: String? = null,
    val raw: JsonNode,
)

/**
 * A widget. [type] is null for a kind this build does not know ([rawType] keeps the authored name); such a widget is an
 * inert placeholder and [raw] keeps its whole object, children included.
 */
data class UiWidget(
    val id: String,
    val rawType: String,
    val type: UiWidgetType?,
    val layout: UiLayout = UiLayout(),
    val style: String? = null,
    val properties: Map<String, JsonNode> = emptyMap(),
    val bindings: Map<String, String> = emptyMap(),
    val actions: Map<String, UiAction> = emptyMap(),
    val children: List<UiWidget> = emptyList(),
    val raw: JsonNode,
)

data class UiScreen(
    val id: String,
    val name: String,
    val kind: UiScreenKind,
    val referenceWidth: Float,
    val referenceHeight: Float,
    val modal: Boolean,
    val root: UiWidget,
)

data class UiBinding(
    val name: String,
    val type: UiBindingType,
    val access: UiBindingAccess,
    val sample: JsonNode?,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val maxLength: Int? = null,
)

data class UiResource(val id: String, val kind: UiResourceKind, val path: String)

/** A named style; [values] is the authored object, interpreted by the renderer. */
data class UiStyle(val name: String, val values: JsonNode)

/** The decoded `additional.ui` of one UI asset. [entryScreen] is null when not authored (the first screen opens). */
data class UiCollection(
    val schemaVersion: Int,
    val entryScreen: String?,
    val screens: List<UiScreen>,
    val bindings: List<UiBinding>,
    val resources: List<UiResource>,
    val styles: List<UiStyle>,
) {
    fun screen(id: String): UiScreen? = screens.firstOrNull { it.id == id }
    fun binding(name: String): UiBinding? = bindings.firstOrNull { it.name == name }
}

/** The typed `additional` of a UI asset: the decoded [admission]. */
data class UiMeta(val admission: UiAdmission)

enum class UiIssueCode {
    FORMAT_MARKER, FORMAT_VERSION, NOT_UI_ASSET, MISSING_UI_BLOCK, UI_VERSION, MALFORMED,
    UNKNOWN_ENTRY_SCREEN, DUPLICATE_SCREEN_ID, DUPLICATE_WIDGET_ID, DUPLICATE_BINDING, DUPLICATE_RESOURCE,
    BAD_GEOMETRY, TOO_DEEP, TOO_MANY_WIDGETS, CHILDREN_ON_LEAF, BAD_BINDING,
    UNKNOWN_WIDGET, UNRESOLVED_BINDING, UNRESOLVED_SCREEN,
}

/** A diagnostic carrying data, not text: [path] is a JSON pointer-like location, hosts choose presentation. */
data class UiIssue(val code: UiIssueCode, val path: String)

/** Result of admitting a UI asset: refused as a whole, or [Admitted] with non-fatal [warnings]. */
sealed interface UiAdmission {
    data class Admitted(val collection: UiCollection, val warnings: List<UiIssue>) : UiAdmission
    data class Refused(val issues: List<UiIssue>) : UiAdmission
}
