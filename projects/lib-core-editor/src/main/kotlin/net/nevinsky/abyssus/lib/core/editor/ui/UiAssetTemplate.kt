/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import java.util.UUID

/** The `meta.json` text of a newly created UI asset: native markers, an editable `main` screen, nothing registered yet. */
class UiAssetTemplate(private val decoder: UiCollectionDecoder = UiCollectionDecoder()) {
    fun create(uuid: UUID, lastModified: Long): String {
        val f = JsonNodeFactory.instance
        val meta = f.objectNode().apply {
            put("format", "abyssus"); put("formatVersion", 1); put("version", 1); put("lastModified", lastModified)
            put("uuid", uuid.toString()); put("type", "UI")
            putObject("additional").putObject("ui").apply {
                put("schemaVersion", 1); put("entryScreen", "main")
                putArray("screens").addObject().apply {
                    put("id", "main"); put("name", "Main")
                    putObject("root").apply {
                        put("id", "root"); put("type", "panel")
                        putArray("children").addObject().apply {
                            put("id", "title"); put("type", "label")
                            putObject("layout").apply { put("anchor", "center") }
                            putObject("properties").put("text", "Hello")
                        }
                    }
                }
            }
        }
        check(decoder.decode(meta) is UiAdmission.Admitted) { "the UI asset template is not valid UI" }
        return SceneJson().pretty(meta)
    }
}
