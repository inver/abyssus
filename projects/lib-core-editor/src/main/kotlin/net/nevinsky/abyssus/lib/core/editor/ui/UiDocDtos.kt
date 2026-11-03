/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * The editing model of a UI asset's `meta.json`: plain mutable DTOs bound and printed by `JsonProcessor`, so the editor
 * holds no JSON tree. Members are declared in the order they are written. A member the model does not name is kept in
 * [extras] and written back after the named ones; numbers read as `Int` / `Double` and are written as that (spelling such
 * as `1.00` is not kept).
 */
abstract class UiDocBase {
    private val unmodeled = LinkedHashMap<String, Any?>()

    /** Members this model does not name, in file order. */
    @get:JsonAnyGetter
    val extras: Map<String, Any?> get() = unmodeled

    @JsonAnySetter
    fun putExtra(key: String, value: Any?) {
        unmodeled[key] = value
    }

    @JsonIgnore
    fun hasExtras(): Boolean = unmodeled.isNotEmpty()
}

/** A whole `meta.json` of a UI asset. */
class UiMetaDoc : UiDocBase() {
    var formatVersion: Any? = null
    var version: Any? = null
    var lastModified: Any? = null
    var uuid: Any? = null
    var type: Any? = null
    var additional: UiAdditionalDoc? = null
}

class UiAdditionalDoc : UiDocBase() {
    var ui: UiBodyDoc? = null
}

/** `additional.ui`. */
class UiBodyDoc : UiDocBase() {
    var schemaVersion: Any? = null
    var entryScreen: String? = null
    var screens: MutableList<UiScreenDoc>? = null
    var bindings: MutableList<UiBindingDoc>? = null
    var resources: MutableList<UiResourceDoc>? = null
    var styles: MutableList<UiStyleDoc>? = null
}

class UiScreenDoc : UiDocBase() {
    var id: String? = null
    var name: String? = null
    var kind: String? = null
    var referenceSize: Any? = null
    var modal: Any? = null
    var navigation: String? = null
    var root: UiWidgetDoc? = null
}

class UiWidgetDoc : UiDocBase() {
    var id: String? = null
    var type: String? = null
    var layout: UiLayoutDoc? = null
    var style: String? = null
    var properties: MutableMap<String, Any?>? = null
    var bindings: MutableMap<String, String?>? = null
    var actions: MutableMap<String, UiActionDoc>? = null
    var children: MutableList<UiWidgetDoc>? = null
    var template: UiWidgetDoc? = null
    var empty: UiWidgetDoc? = null

    /** Every widget directly inside this one: its children, then a list's template and empty state. */
    @JsonIgnore
    fun inside(): List<UiWidgetDoc> = children.orEmpty() + listOfNotNull(template, empty)
}

class UiLayoutDoc : UiDocBase() {
    var anchor: String? = null
    var pivot: String? = null
    var x: Any? = null
    var y: Any? = null
    var width: Any? = null
    var height: Any? = null
    var grow: Any? = null
    var padding: Any? = null
    var spacing: Any? = null
    var align: String? = null

    @JsonIgnore
    fun isBlank(): Boolean = hasExtras().not() && listOf(anchor, pivot, x, y, width, height, grow, padding, spacing, align).all { it == null }
}

class UiActionDoc : UiDocBase() {
    var type: String? = null
    var assetName: String? = null
    var screenId: String? = null
    var name: String? = null
}

class UiBindingDoc : UiDocBase() {
    var name: String? = null
    var type: String? = null
    var access: String? = null
    var sample: Any? = null
    var min: Any? = null
    var max: Any? = null
    var step: Any? = null
    var maxLength: Any? = null
    var fields: MutableList<UiRecordFieldDoc>? = null
}

class UiRecordFieldDoc : UiDocBase() {
    var name: String? = null
    var type: String? = null
}

class UiResourceDoc : UiDocBase() {
    var id: String? = null
    var kind: String? = null
    var path: String? = null
}

/** A named style: [name], and the style's values as [extras]. */
class UiStyleDoc : UiDocBase() {
    var name: String? = null
}
