/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor

import java.io.File

/** Guards native-document creation and known-field read sites; lossless editing has an explicit exemption. */
fun manualJsonViolations(root: File, sites: List<String>, exceptions: Set<String> = emptySet()): List<String> {
    val forbidden = Regex(
        """\b(?:ObjectNode|ArrayNode|ObjectMapper)\s*\(|\bJsonNodeFactory\b|\.\s*(?:objectNode|arrayNode|readTree|readObject|parseObject)\s*\(|(?:SceneJson\s*\(\s*\)|\bjson)\s*\.\s*parse\s*\(""",
    )
    return sites.flatMap { path ->
        val file = File(root, path)
        require(file.isFile) { "Missing guarded source: $file" }
        if (path in exceptions) emptyList() else file.readLines().mapIndexedNotNull { line, text ->
            if (forbidden.containsMatchIn(text)) "$path:${line + 1}: ${text.trim()}" else null
        }
    }
}
