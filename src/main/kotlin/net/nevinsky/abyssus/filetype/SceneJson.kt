/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.filetype

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.dto.Json

/**
 * Reading and writing of scene JSON. Nulls are kept (a scene's `"skyboxName": null` is data), key order and number
 * text are unchanged, and HTML characters are not escaped.
 */
object SceneJson {
    fun pretty(node: JsonNode): String = Json.writePretty(node) + "\n"

    fun compact(node: JsonNode): String = Json.write(node)

    /** [node] in the style of [original]: indented when the original spans several lines, else on one line. */
    fun inStyleOf(original: String, node: JsonNode): String =
        if (original.contains('\n')) pretty(node) else compact(node)

    /** The scene as indented JSON; null when [text] is not a JSON object (so a half-edited or foreign file is never rewritten). */
    fun pretty(text: String): String? = runCatching {
        val node = Json.parse(text)
        if (!node.isObject) return null
        pretty(node)
    }.getOrNull()
}
