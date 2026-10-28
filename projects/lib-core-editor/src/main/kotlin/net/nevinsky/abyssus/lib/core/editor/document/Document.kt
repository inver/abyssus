/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.format.FormatRejection
import net.nevinsky.abyssus.lib.core.format.UnsupportedDocumentFormat
import net.nevinsky.abyssus.lib.core.util.*

/**
 * A parsed scene, project or asset document, read-only. Hosts (the plugin, the play host) hold it and ask it for plain
 * values; they never see Jackson's tree types: reading a document into values is `lib-core`'s job, changing and writing it
 * is `lib-core-editor`'s. Editing goes through [DocumentEdit] and the editing API, not through this class.
 */
class Document(internal val tree: JsonNode) {
    /** The string at [path], or null when anything on the way is missing, null or of another kind. */
    fun textAt(vararg path: String): String? = tree.textAt(*path)

    /** The boolean at [path], or null. */
    fun boolAt(vararg path: String): Boolean? = tree.boolAt(*path)

    /** The number at [path] as written (`2.50` stays `2.50`), or null. */
    fun numberTextAt(vararg path: String): String? = tree.numberTextAt(*path)

    /** The whole number at [path] (`40` or `40.0`), or null when it is missing or fractional. */
    fun wholeNumberAt(vararg path: String): Int? = tree.wholeNumberAt(*path)

    /** The string entries of the array at [path]; empty when it is no array. */
    fun textsAt(vararg path: String): List<String> = tree.textsAt(*path)

    /** The string member [member] of every element of the array at [path]. */
    fun memberTextsAt(member: String, vararg path: String): List<String> = tree.memberTextsAt(member, *path)

    /** The value at [path] as display text; empty when it is missing or null. */
    fun displayTextAt(vararg path: String): String = tree.displayTextAt(*path)

    /** The node at [path] (any kind), or null when the path leads nowhere. */
    fun at(vararg path: String): Document? = tree.rawAt(*path)?.let(::Document)

    /** The object at [path], or null. */
    fun objectAt(vararg path: String): Document? = tree.objectAt(*path)?.let(::Document)

    /** True when the member [name] of the node at [path] is present. */
    fun hasAt(vararg path: String): Boolean = tree.hasAt(*path)

    /** True when this document has the member [name], even as an explicit null. */
    fun hasMember(name: String): Boolean = tree.hasMember(name)

    /** True when [path] leads to an object (this document itself for an empty path). */
    fun isObjectAt(vararg path: String): Boolean = tree.isObjectAt(*path)

    /** True when this node is an object or an array, so a tree row shows children instead of a value. */
    val isContainer: Boolean get() = tree.isContainerNode

    /** The members of an object, or the elements of an array by index, in file order; empty for a scalar. */
    fun members(): List<Pair<String, Document>> = tree.members().map { (k, v) -> k to Document(v) }

    /** Every string under a member called one of [fields], anywhere below this node. */
    fun textValuesOf(vararg fields: String): Set<String> = tree.textValuesOf(*fields)

    /** True when [text] is one of the strings below this node, at any depth. */
    fun containsText(text: String): Boolean = tree.containsText(text)

    /** The number at [path] as written, or null (the node itself for an empty path). */
    fun numberText(): String? = tree.numberTextOrNull()

    /** The number at [path] as a [Double], or null. */
    fun numberAt(vararg path: String): Double? = tree.rawAt(*path).numberOrNull()

    /** Why the document is not a supported native [kind] (marker, version, legacy fields), or null when it is supported. */
    fun rejection(kind: DocumentKind, format: AbyssusDocumentFormat = AbyssusDocumentFormat()): FormatRejection? = format.validate(tree, kind)

    /** Throws [UnsupportedDocumentFormat] unless the document is a supported native [kind]. */
    fun requireSupported(kind: DocumentKind, format: AbyssusDocumentFormat = AbyssusDocumentFormat()) = format.requireSupported(tree, kind)

    /** The document's compact JSON text, number text as written. */
    override fun toString(): String = tree.toString()

    override fun equals(other: Any?): Boolean = other is Document && other.tree == tree

    override fun hashCode(): Int = tree.hashCode()
}

/** The document [text] holds; throws when it is not JSON. */
fun parseDocument(text: String): Document = Document(SceneJson().parse(text))

/** The document [text] holds, checked as a supported native [kind]; throws [UnsupportedDocumentFormat] or on bad JSON. */
fun parseSupportedDocument(text: String, kind: DocumentKind): Document = parseDocument(text).also { it.requireSupported(kind) }

/** The document [text] holds when it is a JSON object; throws otherwise. */
fun parseDocumentObject(text: String): Document = Document(SceneJson().parseObject(text))

/** An edit the host asks the editing API to make on a document it has just parsed; its lambda receives the tree and passes it on. */
typealias DocumentEdit<R> = (JsonNode) -> R

/** Replaces everything in the document [root] (a JSON object) by the members of the object document [text]. */
fun replaceDocument(root: JsonNode, text: String) {
    val target = SceneJson().parse(text) as? ObjectNode ?: throw IllegalArgumentException("the replacement is not a JSON object")
    (root as? ObjectNode ?: throw IllegalArgumentException("the document is not a JSON object")).removeAll().setAll<ObjectNode>(target)
}

/** The document node at [keys] below [root] (object member names, array indexes); null when the path leads nowhere. */
private fun JsonNode.child(key: String): JsonNode? = when (this) {
    is ObjectNode -> get(key)
    is com.fasterxml.jackson.databind.node.ArrayNode -> key.toIntOrNull()?.let { if (it in 0 until size()) get(it) else null }
    else -> null
}

/** Flips the boolean member [toggle] of the object at [keys] below [root] from [current]; false, changing nothing, when there is none. */
fun flipToggle(root: JsonNode, keys: List<String>, toggle: String, current: Boolean): Boolean {
    val target = keys.fold(root as JsonNode?) { node, key -> node?.child(key) } as? ObjectNode ?: return false
    if (target.get(toggle)?.isBoolean != true) return false
    target.put(toggle, !current)
    return true
}

/**
 * Sets the member [key] of the object document [root] to the text [value], or to null for a null [value]. False, changing
 * nothing, when [root] is no object or ([skipIfSame]) the member already holds that value.
 */
fun setTextMember(root: JsonNode, key: String, value: String?, skipIfSame: Boolean = false): Boolean {
    val scene = root as? ObjectNode ?: return false
    if (skipIfSame && (scene.get(key)?.let { if (it.isNull) null else it.textValue() }) == value && (scene.has(key) || value == null)) return false
    if (value == null) scene.putNull(key) else scene.put(key, value)
    return true
}

/** Sets the boolean member [key] of the object document [root] to [on]; false, changing nothing, when it already is or [root] is no object. */
fun setBooleanMember(root: JsonNode, key: String, on: Boolean): Boolean {
    val obj = root as? ObjectNode ?: return false
    if (obj.get(key)?.let { it.isBoolean && it.booleanValue() == on } == true) return false
    obj.put(key, on)
    return true
}

/** Sets the text member [key] to [value], or removes it for a null [value]; false, changing nothing, when it already is so. */
fun setOrRemoveText(root: JsonNode, key: String, value: String?): Boolean {
    val obj = root as? ObjectNode ?: return false
    val current = obj.get(key)
    if (value == null) return current != null && obj.remove(key) != null
    if (current?.isTextual == true && current.textValue() == value) return false
    obj.put(key, value)
    return true
}

/** A copy of [root] with the boolean member [key] replaced by [value] when it has one; [root] itself when it is no object. */
fun copyWithBoolean(root: Document, key: String, value: Boolean): Document {
    val copy = root.tree.deepCopy<JsonNode>() as? ObjectNode ?: return root
    if (copy.has(key)) copy.put(key, value)
    return Document(copy)
}

/** A copy of [root] without the members [keys], for comparing the rest of a document; null when it is no object. */
fun withoutMembers(root: Document, keys: List<String>): Document? = (root.tree.deepCopy<JsonNode>() as? ObjectNode)?.also { it.remove(keys) }?.let(::Document)

/** Removes entity [id] from the scene document [root]'s entity map. */
fun removeEntityNode(root: JsonNode, id: String) {
    (SceneEntityTree(root).entities() as? ObjectNode)?.remove(id)
}
