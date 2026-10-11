/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.DocumentTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome

/**
 * The foliage settings diff as one text edit. A member is written only when its setting differs (a cleared nullable
 * member is removed, so an omitted default stays omitted), and every other entry keeps its key order, number text
 * and unknown payload: entries the settings do not know, such as an id-less layer, stay where they are.
 */
class FoliageMetaEdits(private val editor: DocumentTextEditor = DocumentTextEditor()) {
    fun edit(text: String, before: FoliageMeta, after: FoliageMeta): TextEditOutcome =
        editor.edit(text, DocumentKind.ASSET) { root ->
            if (before == after || root["type"]?.asText() != MetaType.FOLIAGE.name) return@edit false
            val stored = root.get("additional")
            if (stored != null && !stored.isObject) return@edit false
            val additional = stored as? ObjectNode ?: (root as ObjectNode).putObject("additional")
            additional.change(FOLIAGE_TERRAIN, before.terrain, after.terrain)
            additional.change(FOLIAGE_DATA_FILE_KEY, before.dataFile, after.dataFile)
            additional.change(FOLIAGE_MASK_RESOLUTION, before.maskResolution, after.maskResolution)
            if (before.layers != after.layers) changeLayers(additional, before.layers, after.layers)
            true
        }

    private fun changeLayers(additional: ObjectNode, before: List<FoliageLayerMeta>, after: List<FoliageLayerMeta>) {
        val stored = additional[FOLIAGE_LAYERS]
        if (stored != null && !stored.isArray) return
        val settings = before.associateBy { it.id }
        val old = HashMap<Int, JsonNode>()
        stored?.forEach { element -> idOf(element)?.let { old.putIfAbsent(it, element) } }
        val rebuilt = ArrayList<JsonNode>()
        after.forEach { layer ->
            val node = (old[layer.id] as? ObjectNode)?.deepCopy() ?: JsonNodeFactory.instance.objectNode()
            if (!node.has(FOLIAGE_ID)) node.put(FOLIAGE_ID, layer.id)
            updateLayer(node, settings[layer.id] ?: FoliageLayerMeta(id = layer.id), layer)
            rebuilt.add(node)
        }
        additional.set<JsonNode>(FOLIAGE_LAYERS, merged(stored, rebuilt, settings.keys, ::idOf))
    }

    private fun updateLayer(node: ObjectNode, before: FoliageLayerMeta, after: FoliageLayerMeta) {
        node.change(FOLIAGE_KIND, before.kind, after.kind)
        if (before.models != after.models) changeModels(node, before.models, after.models)
        node.change(FOLIAGE_DENSITY, before.density, after.density)
        if (before.scale != after.scale) {
            node.objectMember(FOLIAGE_SCALE)?.let { scale ->
                scale.change(FOLIAGE_MIN, before.scale.min, after.scale.min)
                scale.change(FOLIAGE_MAX, before.scale.max, after.scale.max)
            }
        }
        node.change(FOLIAGE_ALIGN_TO_NORMAL, before.alignToNormal, after.alignToNormal)
        node.change(FOLIAGE_MIN_HEIGHT, before.minHeight, after.minHeight)
        node.change(FOLIAGE_MAX_HEIGHT, before.maxHeight, after.maxHeight)
        node.change(FOLIAGE_MAX_SLOPE, before.maxSlope, after.maxSlope)
        node.change(FOLIAGE_DRAW_DISTANCE, before.drawDistance, after.drawDistance)
        node.change(FOLIAGE_SEED, before.seed, after.seed)
    }

    private fun changeModels(node: ObjectNode, before: List<FoliageModelMeta>, after: List<FoliageModelMeta>) {
        val stored = node[FOLIAGE_MODELS]
        if (stored != null && !stored.isArray) return
        val settings = before.associateBy { it.asset }
        val old = HashMap<String, JsonNode>()
        stored?.forEach { entry -> assetOf(entry)?.let { old.putIfAbsent(it, entry) } }
        val rebuilt = ArrayList<JsonNode>()
        after.forEach { model ->
            val entry = (old[model.asset] as? ObjectNode)?.deepCopy() ?: JsonNodeFactory.instance.objectNode()
            if (!entry.has(FOLIAGE_ASSET)) entry.put(FOLIAGE_ASSET, model.asset)
            val previous = settings[model.asset] ?: FoliageModelMeta(asset = model.asset)
            entry.change(FOLIAGE_WEIGHT, previous.weight, model.weight)
            rebuilt.add(entry)
        }
        node.set<JsonNode>(FOLIAGE_MODELS, merged(stored, rebuilt, settings.keys, ::assetOf))
    }

    /**
     * The array a settings diff writes: the entries the settings know are taken from [rebuilt] in order — a slot
     * freed by a removed entry drops and a new entry appends — while entries they do not know keep their place.
     */
    private fun merged(stored: JsonNode?, rebuilt: List<JsonNode>, known: Set<Any?>, key: (JsonNode) -> Any?): ArrayNode {
        val out = JsonNodeFactory.instance.arrayNode()
        var next = 0
        stored?.forEach { element ->
            val id = key(element)
            if (id != null && id in known) {
                if (next < rebuilt.size) out.add(rebuilt[next++])
            } else {
                out.add(element)
            }
        }
        while (next < rebuilt.size) out.add(rebuilt[next++])
        return out
    }

    private fun idOf(node: JsonNode): Int? = node[FOLIAGE_ID]?.takeIf { it.isIntegralNumber }?.asInt()

    private fun assetOf(node: JsonNode): String? = node[FOLIAGE_ASSET]?.takeIf { it.isTextual }?.asText()

    private fun ObjectNode.objectMember(key: String): ObjectNode? {
        val stored = get(key)
        if (stored != null && !stored.isObject) return null
        return stored as? ObjectNode ?: putObject(key)
    }

    private fun ObjectNode.change(key: String, before: Any?, after: Any?) {
        if (before == after) return
        when (after) {
            null -> remove(key)
            is Float -> put(key, after)
            is Int -> put(key, after)
            is String -> put(key, after)
            else -> error("Unsupported foliage field: $key")
        }
    }
}
