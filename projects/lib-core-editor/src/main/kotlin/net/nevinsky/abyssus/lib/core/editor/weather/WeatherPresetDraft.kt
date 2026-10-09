/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.weather

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.AssetMetaBinder
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import java.util.UUID

/** Snapshots admitted metadata with the runtime's cloud defaults, without IO or rendering state. */
class WeatherPresetDraft(
    processor: JsonProcessor,
    private val messages: EditorMessages,
    private val json: SceneJson = SceneJson(),
    private val reader: AssetMetaReader = AssetMetaReader(processor),
    private val binder: AssetMetaBinder = AssetMetaBinder(processor),
) {
    fun create(skyText: String, sourceText: String, uuid: UUID, lastModified: Long): String {
        val sky = reader.read(json.parseObject(skyText))
        require(sky.type == MetaType.SKYBOX_PROCEDURAL) { messages.message("weatherSourceSky") }
        val reference = sky.json["additional"]?.get("clouds")
        require(reference != null && reference.isTextual && reference.textValue().isNotBlank()) {
            messages.message("weatherSourceReference")
        }
        val source = reader.read(json.parseObject(sourceText))
        require(source.type == MetaType.CLOUDS && source.json["uuid"]?.asText() == reference.textValue()) {
            messages.message("weatherSourceReference")
        }
        val settings = binder.bind("source", source.json).typedAdditional<CloudMeta>()
        require(settings.visible) { messages.message("weatherSourceEmpty") }
        val original = source.json["additional"]
        val additional = json.parseObject("{}")
        additional.put("technique", settings.technique.key)
        for (band in listOfNotNull(settings.low, settings.mid, settings.high)) {
            additional.set<JsonNode>(band.level.key, bandJson(band, original?.get(band.level.key)))
        }
        copyExtensions(original, additional, setOf("technique", "low", "mid", "high"))
        val root = json.parseObject("{}")
        root.put("format", "abyssus")
        root.put("formatVersion", 1)
        root.put("version", 1)
        root.put("lastModified", lastModified)
        root.put("uuid", uuid.toString())
        root.put("type", "CLOUDS")
        root.set<JsonNode>("additional", additional)
        copyExtensions(
            source.json,
            root,
            setOf("format", "formatVersion", "version", "lastModified", "uuid", "type", "additional")
        )
        return json.compact(root)
    }

    private fun bandJson(band: CloudBand, source: JsonNode?): ObjectNode {
        val root = json.parseObject("{}")
        root.put("type", band.type.key)
        for ((key, value) in listOf(
            "base" to band.base,
            "top" to band.top,
            "coverage" to band.coverage,
            "density" to band.density
        )) {
            root.set<JsonNode>(key, source?.get(key) ?: number(value))
        }
        val wind = json.parse("[]") as com.fasterxml.jackson.databind.node.ArrayNode
        wind.add(source?.get("wind")?.get(0) ?: source?.get("windX") ?: number(band.windX))
        wind.add(source?.get("wind")?.get(1) ?: source?.get("windZ") ?: number(band.windZ))
        root.set<JsonNode>("wind", wind)
        copyExtensions(
            source,
            root,
            setOf("type", "base", "top", "coverage", "density", "wind", "level", "windX", "windZ")
        )
        return root
    }

    private fun number(value: Float): JsonNode = json.parse(
        if (value.toDouble() == Math.rint(value.toDouble())) value.toLong().toString() else value.toString()
    )

    private fun copyExtensions(source: JsonNode?, target: ObjectNode, known: Set<String>) {
        source?.fieldNames()?.forEachRemaining { key ->
            if (key !in known) target.set<JsonNode>(key, source[key].deepCopy<JsonNode>())
        }
    }
}
