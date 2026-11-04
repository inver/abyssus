/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS
import net.nevinsky.abyssus.lib.core.editor.util.JsonValues
import net.nevinsky.abyssus.lib.core.editor.util.arrayAt
import net.nevinsky.abyssus.lib.core.editor.util.textAt
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/**
 * One native `meta.json`: its [type], its keys as plain [values] (in file order, whatever the type, for display) and
 * the typed binding on request.
 */
class MetaDocument
internal constructor(
    val type: MetaType,
    val values: JsonValues,
    private val text: String,
    private val processor: JsonProcessor,
) {
    /** The text at [path] in the document; null when it is missing or not text. */
    fun text(vararg path: String): String? = values.textAt(*path)

    /**
     * The document bound to its typed [AssetMeta] (`additional` as the class of its `type`); the name is left empty.
     */
    fun bound(): AssetMeta<Any> = AssetMetaParser(processor).read("", text)

    /** The document admitted as UI (or why not); the decoder reads the text afresh, so the document stays read-only. */
    fun ui(): net.nevinsky.abyssus.lib.core.ui.model.UiAdmission =
        net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder().decode(processor.readObject(text))

    /**
     * The `uuid`s the document holds in the fields Abyssus resolves to other assets: the splat textures, the
     * `materials` list and a procedural sky's `clouds`. Files named in `meta.json` live in the asset's own folder and
     * are not references.
     */
    fun referenceUuids(): List<String> {
        val materials = values.arrayAt("additional", "materials").orEmpty().filterIsInstance<String>()
        val splats = SPLAT_FIELDS.mapNotNull { values.textAt("additional", it) }
        return splats + materials + listOfNotNull(values.textAt("additional", "clouds"))
    }
}

/**
 * Reads `meta.json` text once into a [MetaDocument], refusing a document that is not native (no supported
 * `formatVersion`). Every reader of asset metadata (the loaders, the project listing, the properties panel) goes
 * through it, so they agree on the type and read the text once.
 */
class AssetMetaReader(
    private val json: JsonProcessor,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {
    /**
     * [text] as a document; throws when it is not a JSON object or not native. A missing or unknown `type` reads as
     * [MetaType.UNKNOWN].
     */
    fun read(text: String): MetaDocument {
        format.requireSupported(json.readObject(text), DocumentKind.ASSET)
        @Suppress("UNCHECKED_CAST") val values = json.parse(text, Map::class.java) as JsonValues
        val type =
            (values["type"] as? String)?.let { name -> MetaType.entries.firstOrNull { it.name == name } }
                ?: MetaType.UNKNOWN
        return MetaDocument(type, values, text, json)
    }

    /** [text] as a document for display; same as [read]. */
    fun readExact(text: String): MetaDocument = read(text)
}
