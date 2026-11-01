/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.dto.ProjectDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.editor.util.JsonValues
import net.nevinsky.abyssus.lib.core.editor.util.at
import net.nevinsky.abyssus.lib.core.editor.util.objectAt
import net.nevinsky.abyssus.lib.core.editor.util.textAt
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import org.slf4j.Logger

/**
 * Reads `.abss` and `.scene` text from the editor (saved or not) as native documents: the format marker and version are
 * checked before anything is bound. A failure is logged once and rethrown, so an error row can show its cause.
 */
class DocumentParsing(
    private val json: JsonProcessor,
    private val log: Logger,
    registry: ComponentRegistry = ComponentRegistry(),
    private val format: net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat = net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat(),
) {
    /** Binds a scene with its components resolved through [registry]; what it does not know is kept raw. */
    private val scenes = SceneParser(json, registry)

    fun requireSupported(text: String, kind: DocumentKind) {
        format.requireSupported(json.readObject(text), kind)
    }

    fun project(text: String): ProjectDto = json.bind(supported(text, DocumentKind.PROJECT), ProjectDto::class.java)

    fun assetMeta(text: String): AssetMeta<Any> = AssetMetaParser(json).read("", text)

    /** Narrow editor reads must not bind (and reject) unrelated settings or UUIDs. */
    fun assetFields(text: String): AssetDocumentFields {
        val root = values(text)
        return AssetDocumentFields(
            MetaType.entries.firstOrNull { it.name == root.textAt("type") } ?: MetaType.UNKNOWN,
            root["uuid"]?.takeIf { it !is Map<*, *> && it !is List<*> }?.toString(),
            root.textAt("additional", "clouds"),
            root.at("additional", "terrainFile")?.toString().orEmpty(),
        )
    }

    /** Keeps the editor's existing wrong-type defaults, rather than runtime DTO coercions. */
    fun foliageMeta(text: String): FoliageMeta =
        values(text).objectAt("additional")?.let { FoliageSettingsBinding(json).read(it) } ?: FoliageMeta()

    private fun supported(text: String, kind: DocumentKind) = json.readObject(text).also { format.requireSupported(it, kind) }

    @Suppress("UNCHECKED_CAST")
    private fun values(text: String): JsonValues {
        json.readObject(text)
        return json.parse(text, Map::class.java) as JsonValues
    }

    fun projectName(text: String): String? {
        val root = json.readObject(text)
        return json.bind(root, ProjectName::class.java).name
    }

    /** [source] names the document in the log. */
    fun projectName(source: String, readText: () -> String): String? =
        reportedOrThrow("project $source") { projectName(readText()) }

    /** The `uiAssetNames` of the project [text]: its textual entries in order; an absent or malformed list is empty. */
    fun uiAssetNames(text: String): List<String> {
        // A registry query has always ignored malformed fields unrelated to the registry.
        val registry = json.bind(supported(text, DocumentKind.PROJECT), ProjectRegistry::class.java)
        return ProjectDto(uiAssetNames = registry.uiAssetNames).uiAssetNames
    }

    fun uiAssetNames(source: String, readText: () -> String): List<String> =
        reportedOrThrow("project $source") { uiAssetNames(readText()) }

    fun parse(text: String): SceneDto {
        return scenes.parse(text)
    }

    /** Includes failures obtaining the text (an editor's filesystem) in the same logging boundary. */
    fun parse(source: String, readText: () -> String): SceneDto = reportedOrThrow("scene $source") { parse(readText()) }

    private fun <T> reportedOrThrow(source: String, read: () -> T): T = runCatchingKeepingCancellation(read)
        .getOrElse { error -> log.warn("Could not read $source: ${error.message}", error); throw error }
}

data class AssetDocumentFields(val type: MetaType, val uuidText: String?, val clouds: String?, val terrainFile: String)

private data class ProjectName(val name: String? = null)

private data class ProjectRegistry(
    @param:com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = net.nevinsky.abyssus.lib.core.dto.UiAssetNamesDeserializer::class)
    val uiAssetNames: List<String> = emptyList(),
)
