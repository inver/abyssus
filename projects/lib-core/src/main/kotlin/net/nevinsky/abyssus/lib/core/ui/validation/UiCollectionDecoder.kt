/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.validation

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.format.FormatProblem
import net.nevinsky.abyssus.lib.core.ui.model.*

/**
 * Admits the `meta.json` of a UI asset: native identity through [AbyssusDocumentFormat], then the UI schema. Pure and
 * on the caller's thread, no IO. The tree is never modified; unknown fields stay in it, and unknown widget kinds keep
 * their authored object. Errors refuse the whole collection; unresolved references are warnings (the renderer disables
 * the affected controls).
 */
class UiCollectionDecoder(private val format: AbyssusDocumentFormat = AbyssusDocumentFormat()) {

    fun decode(meta: JsonNode): UiAdmission {
        format.validate(meta, DocumentKind.ASSET)?.let {
            val code = if (it.problem == FormatProblem.MARKER) UiIssueCode.FORMAT_MARKER else UiIssueCode.FORMAT_VERSION
            return UiAdmission.Refused(listOf(UiIssue(code, it.path)))
        }
        if (meta.get("type")?.takeIf { it.isTextual }?.textValue() != "UI")
            return refuse(UiIssueCode.NOT_UI_ASSET, "type")
        val ui = meta.get("additional")?.get("ui")?.takeIf { it.isObject }
            ?: return refuse(UiIssueCode.MISSING_UI_BLOCK, "additional.ui")
        val version = ui.get("schemaVersion")
        if (version == null || !version.isIntegralNumber || !version.canConvertToInt() || version.intValue() != UI_SCHEMA_VERSION)
            return refuse(UiIssueCode.UI_VERSION, "additional.ui.schemaVersion")
        return Decoding().decode(ui)
    }

    private fun refuse(code: UiIssueCode, path: String) = UiAdmission.Refused(listOf(UiIssue(code, path)))

    private class Decoding {
        val errors = mutableListOf<UiIssue>()
        val warnings = mutableListOf<UiIssue>()
        val screenIds = HashSet<String>()
        val widgetIds = HashSet<String>()

        fun error(code: UiIssueCode, path: String) {
            errors += UiIssue(code, path)
        }

        fun decode(ui: JsonNode): UiAdmission {
            val base = "additional.ui"
            val screensNode = ui.get("screens")
            if (screensNode != null && !screensNode.isArray) error(UiIssueCode.MALFORMED, "$base.screens")
            val limits = checkLimits(screensNode, "$base.screens")
            if (limits != null) return UiAdmission.Refused(listOf(limits))
            val bindings = list(ui, "bindings", base)
                .mapIndexedNotNull { i, node -> binding(node, "$base.bindings[$i]") }
            val bindingNames = HashSet<String>()
            bindings.forEachIndexed { i, b ->
                if (!bindingNames.add(b.name)) error(
                    UiIssueCode.DUPLICATE_BINDING,
                    "$base.bindings[$i].name"
                )
            }
            val resources =
                list(ui, "resources", base).mapIndexedNotNull { i, node -> resource(node, "$base.resources[$i]") }
            val resourceIds = HashSet<String>()
            resources.forEachIndexed { i, r ->
                if (!resourceIds.add(r.id)) error(
                    UiIssueCode.DUPLICATE_RESOURCE,
                    "$base.resources[$i].id"
                )
            }
            val styles = list(ui, "styles", base).mapIndexedNotNull { i, node -> style(node, "$base.styles[$i]") }
            val screens = list(ui, "screens", base).mapIndexedNotNull { i, node -> screen(node, "$base.screens[$i]") }
            val entryNode = ui.get("entryScreen")
            val entry = entryNode?.takeIf { it.isTextual }?.textValue()
            if (entryNode != null && (entry == null || entry !in screenIds)) error(
                UiIssueCode.UNKNOWN_ENTRY_SCREEN,
                "$base.entryScreen"
            )
            if (errors.isNotEmpty()) return UiAdmission.Refused(errors.toList())
            val collection = UiCollection(UI_SCHEMA_VERSION, entry, screens, bindings, resources, styles)
            references(collection, bindingNames, base)
            return UiAdmission.Admitted(collection, warnings.toList())
        }

        private fun list(parent: JsonNode, field: String, path: String): List<JsonNode> {
            val node = parent.get(field) ?: return emptyList()
            if (!node.isArray) {
                error(UiIssueCode.MALFORMED, "$path.$field"); return emptyList()
            }
            return node.toList()
        }

        /** Counts depth and widgets iteratively, before anything is built. */
        private fun checkLimits(screens: JsonNode?, path: String): UiIssue? {
            if (screens == null || !screens.isArray) return null
            var count = 0
            screens.forEachIndexed { i, screen ->
                val root = screen.get("root")?.takeIf { it.isObject } ?: return@forEachIndexed
                val stack = ArrayDeque<Pair<JsonNode, Int>>()
                stack.addLast(root to 1)
                while (stack.isNotEmpty()) {
                    val (node, depth) = stack.removeLast()
                    if (depth > UI_MAX_DEPTH) return UiIssue(UiIssueCode.TOO_DEEP, "$path[$i].root")
                    if (++count > UI_MAX_WIDGETS) return UiIssue(UiIssueCode.TOO_MANY_WIDGETS, "$path[$i].root")
                    val children = node.get("children")
                    if (children != null && children.isArray) children.forEach { if (it.isObject) stack.addLast(it to depth + 1) }
                    listOf("template", "empty").forEach { f ->
                        node.get(f)?.takeIf { it.isObject }?.let { stack.addLast(it to depth + 1) }
                    }
                }
            }
            return null
        }

        private fun text(node: JsonNode, field: String, path: String, required: Boolean = true): String? {
            val value = node.get(field)
            if (value == null) {
                if (required) error(UiIssueCode.MALFORMED, "$path.$field"); return null
            }
            if (!value.isTextual || value.textValue().isBlank()) {
                error(UiIssueCode.MALFORMED, "$path.$field"); return null
            }
            return value.textValue()
        }

        private fun number(node: JsonNode, field: String, path: String): Double? {
            val value = node.get(field) ?: return null
            if (!value.isNumber || !value.doubleValue().isFinite()) {
                error(UiIssueCode.BAD_GEOMETRY, "$path.$field"); return null
            }
            return value.doubleValue()
        }

        private fun nonNegative(node: JsonNode, field: String, path: String): Float? =
            number(node, field, path)?.let {
                if (it < 0) {
                    error(UiIssueCode.BAD_GEOMETRY, "$path.$field"); null
                } else it.toFloat()
            }

        private inline fun <reified E : Enum<E>> enumOf(
            node: JsonNode,
            field: String,
            path: String,
            json: (E) -> String,
            code: UiIssueCode
        ): E? {
            val value = node.get(field) ?: return null
            val match = if (value.isTextual) enumValues<E>().firstOrNull { json(it) == value.textValue() } else null
            if (match == null) error(code, "$path.$field")
            return match
        }

        private fun screen(node: JsonNode, path: String): UiScreen? {
            if (!node.isObject) {
                error(UiIssueCode.MALFORMED, path); return null
            }
            val id = text(node, "id", path)
            if (id != null && !screenIds.add(id)) error(UiIssueCode.DUPLICATE_SCREEN_ID, "$path.id")
            val kind =
                enumOf<UiScreenKind>(node, "kind", path, { it.json }, UiIssueCode.MALFORMED) ?: UiScreenKind.SCREEN
            var width = UI_DEFAULT_REFERENCE_WIDTH
            var height = UI_DEFAULT_REFERENCE_HEIGHT
            node.get("referenceSize")?.let { size ->
                if (!size.isObject) error(UiIssueCode.BAD_GEOMETRY, "$path.referenceSize")
                else {
                    val w = number(size, "width", "$path.referenceSize")
                    val h = number(size, "height", "$path.referenceSize")
                    if (w != null && w > 0) width = w.toFloat() else if (size.has("width")) error(
                        UiIssueCode.BAD_GEOMETRY,
                        "$path.referenceSize.width"
                    )
                    if (h != null && h > 0) height = h.toFloat() else if (size.has("height")) error(
                        UiIssueCode.BAD_GEOMETRY,
                        "$path.referenceSize.height"
                    )
                }
            }
            val navigation =
                enumOf<UiNavigation>(node, "navigation", path, { it.json }, UiIssueCode.MALFORMED) ?: UiNavigation.TAB
            val modalNode = node.get("modal")
            if (modalNode != null && !modalNode.isBoolean) error(UiIssueCode.MALFORMED, "$path.modal")
            val rootNode = node.get("root")
            val root = if (rootNode == null || !rootNode.isObject) {
                error(UiIssueCode.MALFORMED, "$path.root"); null
            } else widget(rootNode, "$path.root")
            if (id == null || root == null) return null
            return UiScreen(
                id, node.get("name")?.takeIf { it.isTextual }?.textValue() ?: id, kind, width, height,
                modalNode?.takeIf { it.isBoolean }?.booleanValue() ?: false, root, navigation
            )
        }

        private fun widget(node: JsonNode, path: String): UiWidget? {
            val id = text(node, "id", path)
            val rawType = text(node, "type", path)
            if (id != null && id.contains('#')) error(
                UiIssueCode.MALFORMED,
                "$path.id"
            ) // '#' marks an expanded list row at runtime
            if (id != null && !widgetIds.add(id)) error(UiIssueCode.DUPLICATE_WIDGET_ID, "$path.id")
            if (id == null || rawType == null) return null
            val type = UiWidgetType.entries.firstOrNull { it.json == rawType }
            if (type == null) {
                warnings += UiIssue(UiIssueCode.UNKNOWN_WIDGET, path)
                // the placeholder keeps its authored place and size, so the rest of the screen lays out as designed
                return UiWidget(id, rawType, null, layout(node.get("layout"), "$path.layout"), raw = node)
            }
            val childrenNode = node.get("children")
            val children = mutableListOf<UiWidget>()
            if (childrenNode != null) {
                if (!childrenNode.isArray) error(UiIssueCode.MALFORMED, "$path.children")
                else if (!type.container) {
                    if (childrenNode.size() > 0) error(UiIssueCode.CHILDREN_ON_LEAF, "$path.children")
                } else childrenNode.forEachIndexed { i, child ->
                    if (!child.isObject) error(UiIssueCode.MALFORMED, "$path.children[$i]")
                    else widget(child, "$path.children[$i]")?.let(children::add)
                }
            }
            fun nested(field: String): UiWidget? = node.get(field)?.let {
                if (!it.isObject) {
                    error(UiIssueCode.MALFORMED, "$path.$field"); null
                } else widget(it, "$path.$field")
            }

            val template = if (type == UiWidgetType.LIST) nested("template") else null
            val empty = if (type == UiWidgetType.LIST) nested("empty") else null
            if (type == UiWidgetType.LIST && node.get("template") == null) error(
                UiIssueCode.MISSING_TEMPLATE,
                "$path.template"
            )
            val style = node.get("style")?.let {
                if (it.isTextual) it.textValue() else {
                    error(UiIssueCode.MALFORMED, "$path.style"); null
                }
            }
            return UiWidget(
                id,
                rawType,
                type,
                layout(node.get("layout"), "$path.layout"),
                style,
                objectMap(node.get("properties"), "$path.properties"),
                bindingMap(node.get("bindings"), "$path.bindings"),
                actionMap(node.get("actions"), "$path.actions"),
                children,
                template,
                empty,
                node
            )
        }

        private fun layout(node: JsonNode?, path: String): UiLayout {
            if (node == null) return UiLayout()
            if (!node.isObject) {
                error(UiIssueCode.BAD_GEOMETRY, path); return UiLayout()
            }
            return UiLayout(
                anchor = enumOf<UiAnchor>(node, "anchor", path, { it.json }, UiIssueCode.BAD_GEOMETRY),
                pivot = enumOf<UiAnchor>(node, "pivot", path, { it.json }, UiIssueCode.BAD_GEOMETRY),
                x = number(node, "x", path)?.toFloat() ?: 0f,
                y = number(node, "y", path)?.toFloat() ?: 0f,
                width = nonNegative(node, "width", path),
                height = nonNegative(node, "height", path),
                grow = nonNegative(node, "grow", path) ?: 0f,
                padding = nonNegative(node, "padding", path) ?: 0f,
                spacing = nonNegative(node, "spacing", path) ?: 0f,
                align = enumOf<UiAlign>(node, "align", path, { it.json }, UiIssueCode.BAD_GEOMETRY) ?: UiAlign.START,
            )
        }

        private fun objectMap(node: JsonNode?, path: String): Map<String, JsonNode> {
            if (node == null) return emptyMap()
            if (!node.isObject) {
                error(UiIssueCode.MALFORMED, path); return emptyMap()
            }
            return node.properties().associate { it.key to it.value }
        }

        private fun bindingMap(node: JsonNode?, path: String): Map<String, String> {
            if (node == null) return emptyMap()
            if (!node.isObject) {
                error(UiIssueCode.MALFORMED, path); return emptyMap()
            }
            val result = LinkedHashMap<String, String>()
            for ((property, target) in node.properties()) {
                if (target.isTextual && target.textValue().isNotBlank()) result[property] = target.textValue()
                else error(UiIssueCode.BAD_BINDING, "$path.$property")
            }
            return result
        }

        private fun actionMap(node: JsonNode?, path: String): Map<String, UiAction> {
            if (node == null) return emptyMap()
            if (!node.isObject) {
                error(UiIssueCode.MALFORMED, path); return emptyMap()
            }
            val result = LinkedHashMap<String, UiAction>()
            for ((event, action) in node.properties()) {
                val rawType = action.get("type")?.takeIf { it.isTextual }?.textValue()
                if (!action.isObject || rawType == null) {
                    error(UiIssueCode.MALFORMED, "$path.$event"); continue
                }
                fun str(f: String) = action.get(f)?.takeIf { it.isTextual && it.textValue().isNotBlank() }?.textValue()
                result[event] = UiAction(
                    rawType, UiActionType.entries.firstOrNull { it.json == rawType },
                    str("assetName"), str("screenId"), str("name"), action
                )
            }
            return result
        }

        private fun binding(node: JsonNode, path: String): UiBinding? {
            if (!node.isObject) {
                error(UiIssueCode.MALFORMED, path); return null
            }
            val name = text(node, "name", path)
            val type = enumOf<UiBindingType>(node, "type", path, { it.json }, UiIssueCode.BAD_BINDING)
            if (node.get("type") == null) error(UiIssueCode.BAD_BINDING, "$path.type")
            val access = enumOf<UiBindingAccess>(node, "access", path, { it.json }, UiIssueCode.BAD_BINDING)
                ?: UiBindingAccess.READ_ONLY
            val fields = if (type == UiBindingType.LIST) recordFields(node, path) else emptyList()
            if (type == UiBindingType.LIST && access != UiBindingAccess.READ_ONLY) error(
                UiIssueCode.BAD_BINDING,
                "$path.access"
            )
            val sample = node.get("sample")
            if (sample != null && type != null && !sampleMatches(type, sample, fields)) error(
                UiIssueCode.BAD_BINDING,
                "$path.sample"
            )
            val min = number(node, "min", path)
            val max = number(node, "max", path)
            if (min != null && max != null && min > max) error(UiIssueCode.BAD_BINDING, "$path.min")
            val step = number(node, "step", path)
            if (step != null && step <= 0) error(UiIssueCode.BAD_BINDING, "$path.step")
            val maxLength = node.get("maxLength")?.let {
                if (it.isIntegralNumber && it.canConvertToInt() && it.intValue() >= 0) it.intValue()
                else {
                    error(UiIssueCode.BAD_BINDING, "$path.maxLength"); null
                }
            }
            if (name == null || type == null) return null
            return UiBinding(name, type, access, sample, min, max, step, maxLength, fields)
        }

        private fun sampleMatches(
            type: UiBindingType,
            sample: JsonNode,
            fields: List<UiRecordField> = emptyList()
        ): Boolean = when (type) {
            UiBindingType.NUMBER -> sample.isNumber && sample.doubleValue().isFinite()
            UiBindingType.BOOLEAN -> sample.isBoolean
            UiBindingType.STRING -> sample.isTextual
            UiBindingType.LIST -> sample.isArray && sample.all { row ->
                row.isObject && row.properties().all { (k, v) ->
                    fields.firstOrNull { it.name == k }?.let { f -> sampleMatches(f.type, v) } == true
                }
            }
        }

        /** The record of a `list` binding: at least one uniquely named field of a scalar type. */
        private fun recordFields(node: JsonNode, path: String): List<UiRecordField> {
            val array = node.get("fields")
            if (array == null || !array.isArray || array.isEmpty) {
                error(UiIssueCode.BAD_BINDING, "$path.fields"); return emptyList()
            }
            val result = mutableListOf<UiRecordField>()
            array.forEachIndexed { i, f ->
                val name = text(f, "name", "$path.fields[$i]")
                val type = f.get("type")?.takeIf { it.isTextual }
                    ?.let { t -> UiBindingType.entries.firstOrNull { it.json == t.textValue() } }
                if (type == null || type == UiBindingType.LIST) {
                    error(UiIssueCode.BAD_BINDING, "$path.fields[$i].type"); return@forEachIndexed
                }
                if (name == null) return@forEachIndexed
                if (result.any { it.name == name }) error(
                    UiIssueCode.BAD_BINDING,
                    "$path.fields[$i].name"
                ) else result += UiRecordField(name, type)
            }
            return result
        }

        private fun resource(node: JsonNode, path: String): UiResource? {
            if (!node.isObject) {
                error(UiIssueCode.MALFORMED, path); return null
            }
            val id = text(node, "id", path)
            val kind = enumOf<UiResourceKind>(node, "kind", path, { it.json }, UiIssueCode.MALFORMED)
            if (node.get("kind") == null) error(UiIssueCode.MALFORMED, "$path.kind")
            val file = text(node, "path", path)
            if (id == null || kind == null || file == null) return null
            return UiResource(id, kind, file)
        }

        private fun style(node: JsonNode, path: String): UiStyle? {
            if (!node.isObject) {
                error(UiIssueCode.MALFORMED, path); return null
            }
            return text(node, "name", path)?.let { UiStyle(it, node) }
        }

        private fun references(collection: UiCollection, bindingNames: Set<String>, base: String) {
            for ((i, screen) in collection.screens.withIndex()) collection.walkScoped(screen.root, "$base.screens[$i].root") { widget, path, item ->
                for ((property, name) in widget.bindings) {
                    when {
                        name.startsWith(UI_ITEM_PREFIX) -> when {
                            item == null -> warnings += UiIssue(
                                UiIssueCode.ITEM_OUTSIDE_LIST,
                                "$path.bindings.$property"
                            )

                            name.removePrefix(UI_ITEM_PREFIX) !in item -> warnings += UiIssue(
                                UiIssueCode.UNRESOLVED_ITEM_FIELD,
                                "$path.bindings.$property"
                            )
                        }

                        name !in bindingNames -> warnings += UiIssue(
                            UiIssueCode.UNRESOLVED_BINDING,
                            "$path.bindings.$property"
                        )
                    }
                }
                for ((event, action) in widget.actions) {
                    val local = action.assetName == null && action.screenId != null
                    if (local && (action.type == UiActionType.OPEN_SCREEN || action.type == UiActionType.SHOW_OVERLAY) && collection.screen(
                            action.screenId
                        ) == null
                    )
                        warnings += UiIssue(UiIssueCode.UNRESOLVED_SCREEN, "$path.actions.$event")
                }
            }
        }
    }
}
