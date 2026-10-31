/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.util.text
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation

/** One parsed `meta.json`: its [type], its raw [json] tree, and the typed binding on request. */
class MetaDocument internal constructor(val type: MetaType, val json: JsonNode, private val processor: JsonProcessor) {
    /** Binds the document to [clazz], or null when it does not fit (a missing required field, say). */
    fun <T, M : AssetMeta<T>> typed(clazz: Class<M>): M? =
        runCatchingKeepingCancellation { processor.bind(json, clazz) }.getOrNull()

    /** The document bound to its typed [AssetMeta] (`additional` as the class of its `type`); the name is left empty. */
    fun bound(): AssetMeta<Any> = net.nevinsky.abyssus.lib.core.assets.AssetMetaBinder(processor).bind("", json)

    /**
     * The `uuid`s the document holds in the fields Abyssus resolves to other assets: the splat textures, the `materials`
     * list and a procedural sky's `clouds`. Files named in `meta.json` live in the asset's own folder and are not references.
     */
    fun referenceUuids(): List<String> {
        val additional = json.get("additional")?.takeIf { it.isObject } ?: return emptyList()
        val materials = additional.get("materials")?.takeIf { it.isArray }?.filter { it.isTextual }?.map { it.textValue() }.orEmpty()
        val splats = net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS.mapNotNull { additional.get(it)?.takeIf { v -> v.isTextual }?.textValue() }
        return splats + materials + listOfNotNull(additional.get("clouds")?.takeIf { it.isTextual }?.textValue())
    }
}

/**
 * Parses `meta.json` text once into a [MetaDocument]. Every reader of asset metadata (the loaders, the project
 * listing, the properties panel) goes through it, so they agree on the type and read the text once.
 */
class AssetMetaReader(private val json: JsonProcessor) {
    /**
     * [text] as a document; throws when it is not a JSON object. A missing or unknown `type` reads as
     * [MetaType.UNKNOWN].
     */
    fun read(text: String): MetaDocument = read(json.readObject(text))

    /** [text] as a document for display; same as [read] now that number text is no longer kept. */
    fun readExact(text: String): MetaDocument = read(text)

    /** An already parsed [tree] (a caller that needs its own number text, say) as a document. */
    fun read(tree: JsonNode): MetaDocument {
        return MetaDocument(typeOf(tree), tree, json)
    }

    private fun typeOf(tree: JsonNode): MetaType =
        tree.text("type")?.let { name -> MetaType.entries.firstOrNull { it.name == name } } ?: MetaType.UNKNOWN
}
