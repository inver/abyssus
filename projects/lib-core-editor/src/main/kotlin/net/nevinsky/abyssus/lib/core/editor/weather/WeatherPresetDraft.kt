/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.weather

import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.newAssetMetaText
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import java.util.UUID

/**
 * Snapshots the clouds a procedural sky names as a new `CLOUDS` asset: the source's typed settings, with the runtime's
 * defaults filled in, under a new [UUID]. Extension keys the typed settings do not model are not carried over.
 */
class WeatherPresetDraft(
    private val processor: JsonProcessor,
    private val messages: EditorMessages,
    private val parser: AssetMetaParser = AssetMetaParser(processor),
) {
    fun create(skyText: String, sourceText: String, uuid: UUID, lastModified: Long): String {
        val sky = parser.read("sky", skyText)
        require(sky.type == MetaType.SKYBOX_PROCEDURAL) { messages.message("weatherSourceSky") }
        val reference = sky.typedAdditional<ProceduralSkyMeta>().cloudsReference
        require(reference != null) { messages.message("weatherSourceReference") }
        val source = parser.read("source", sourceText)
        require(source.type == MetaType.CLOUDS && source.uuid?.toString() == reference) {
            messages.message("weatherSourceReference")
        }
        val settings = source.typedAdditional<CloudMeta>()
        require(settings.visible) { messages.message("weatherSourceEmpty") }
        return newAssetMetaText(parser, MetaType.CLOUDS, settings, uuid, lastModified)
    }
}
