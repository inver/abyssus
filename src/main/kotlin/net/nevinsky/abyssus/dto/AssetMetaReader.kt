/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.dto

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.format.DocumentKind
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.runtime.text
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation

/** One parsed `meta.json`: its [type], its raw [json] tree, and the typed binding on request. */
class MetaDocument internal constructor(val type: MetaType, val json: JsonNode, private val processor: JsonProcessor) {
    /** Binds the document to [clazz], or null when it does not fit (a missing required field, say). */
    fun <T, M : AssetMeta<T>> typed(clazz: Class<M>): M? =
        runCatchingKeepingCancellation { processor.bind(json, clazz) }.getOrNull()
}

/**
 * Parses `meta.json` text once into a [MetaDocument]. Every reader of asset metadata (the loaders, the project
 * listing, the properties panel) goes through it, so they agree on the type and read the text once.
 */
class AssetMetaReader(private val json: JsonProcessor, private val format: AbyssusDocumentFormat = AbyssusDocumentFormat()) {
    /**
     * [text] as a document; throws when it is not a JSON object. A missing or unknown `type` reads as
     * [MetaType.UNKNOWN].
     */
    fun read(text: String): MetaDocument = read(json.readObject(text))

    /** An already parsed [tree] (a caller that needs its own number text, say) as a document. */
    fun read(tree: JsonNode): MetaDocument {
        format.requireSupported(tree, DocumentKind.ASSET)
        return MetaDocument(typeOf(tree), tree, json)
    }

    private fun typeOf(tree: JsonNode): MetaType =
        tree.text("type")?.let { name -> MetaType.entries.firstOrNull { it.name == name } } ?: MetaType.UNKNOWN
}
