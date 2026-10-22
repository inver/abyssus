/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.io.JsonFormat
import net.nevinsky.abyssus.lib.core.io.NativeJsonTree

/** Unknown fields are ignored and properties keep declaration order, so a DTO lists its fields as the file does. */
private val format = JsonFormat()

private val sceneMapper: JsonMapper = format.mapperBuilder().build()

private val prettyPrinter = format.prettyPrinter

/**
 * Reading and writing of scene JSON, and the one [mapper] that binds asset files to their DTOs. Nulls are kept (a
 * scene's `"skyboxName": null` is data), key order and number text are unchanged, and HTML characters are not escaped.
 * Stateless: every instance shares one mapper.
 */
class SceneJson {
    private val tree = NativeJsonTree()

    val mapper: JsonMapper get() = sceneMapper

    /** Parses [text] as exactly one JSON document; throws on malformed, empty or trailing input. */
    fun parse(text: String): JsonNode = tree.parse(text)

    fun parseObject(text: String): ObjectNode = tree.parseObject(text)

    /** Binds an already parsed [node] to [type]; throws when a field has the wrong shape. */
    fun <T> bind(node: JsonNode, type: Class<T>): T = mapper.treeToValue(node, type)

    fun pretty(node: JsonNode): String = mapper.writer(prettyPrinter).writeValueAsString(node) + "\n"

    fun compact(node: JsonNode): String = mapper.writeValueAsString(node)

    /** Serializes a new typed document directly; edit paths keep using their lossless trees. */
    fun pretty(document: Any): String = mapper.writer(prettyPrinter).writeValueAsString(document) + "\n"

    fun compact(document: Any): String = mapper.writeValueAsString(document)

    /**
     * [node] in the style of [original]: indented when the original spans several lines, else on one line, and ending
     * with a newline only when the original does (an asset `meta.json` written without one stays without one).
     */
    fun inStyleOf(original: String, node: JsonNode): String =
        if (original.contains('\n')) {
            pretty(node).let {
                if (original.endsWith('\n')) {
                    it
                } else it.removeSuffix("\n")
            }
        } else compact(node)

    /** The scene as indented JSON; null when [text] is not a JSON object (so a half-edited or foreign file is never rewritten). */
    fun pretty(text: String): String? = runCatchingKeepingCancellation {
        val node = parse(text)
        if (!node.isObject) return null
        pretty(node)
    }.getOrNull()
}
