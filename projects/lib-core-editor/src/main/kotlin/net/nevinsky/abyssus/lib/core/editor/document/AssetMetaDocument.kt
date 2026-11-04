/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import java.util.UUID
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.MetaType

/** The `meta.json` text of a new [type] asset with [additional]: compact, or indented when [pretty]. */
fun <A : Any> newAssetMetaText(
    parser: AssetMetaParser,
    type: MetaType,
    additional: A,
    uuid: UUID,
    lastModified: Long,
    pretty: Boolean = false,
): String =
    parser.write(AssetMeta(type = type, lastModified = lastModified, additional = additional, uuid = uuid), pretty)

/** The deliberately sparse initial UI document, without the optional defaults of a bound runtime widget. */
data class UiAssetAdditional(val ui: InitialUiCollection = InitialUiCollection())

data class InitialUiCollection(
    val schemaVersion: Int = 1,
    val entryScreen: String = "main",
    val screens: List<InitialUiScreen> = listOf(InitialUiScreen()),
)

data class InitialUiScreen(
    val id: String = "main",
    val name: String = "Main",
    val root: InitialUiPanel = InitialUiPanel(),
)

data class InitialUiPanel(
    val id: String = "root",
    val type: String = "panel",
    val children: List<InitialUiLabel> = listOf(InitialUiLabel()),
)

data class InitialUiLabel(
    val id: String = "title",
    val type: String = "label",
    val layout: InitialUiLayout = InitialUiLayout(),
    val properties: InitialUiProperties = InitialUiProperties(),
)

data class InitialUiLayout(val anchor: String = "center")

data class InitialUiProperties(val text: String = "Hello")

data class TextureAssetAdditional(val file: String)
