/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaBinder
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.dto.ProjectDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.slf4j.Logger

/**
 * Reads `.abss` and `.scene` text from the editor (saved or not) as native documents: the format marker and version are
 * checked before anything is bound. A failure is logged once and rethrown, so an error row can show its cause.
 */
class DocumentParsing(
    private val json: JsonProcessor,
    private val log: Logger,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {
    fun requireSupported(text: String, kind: DocumentKind) {
        supported(text, kind)
    }

    fun project(text: String): ProjectDto = json.bind(supported(text, DocumentKind.PROJECT), ProjectDto::class.java)

    fun assetMeta(text: String): AssetMeta<Any> = AssetMetaBinder(json).bind("", supported(text, DocumentKind.ASSET))

    /** Narrow editor reads must not bind (and reject) unrelated settings or UUIDs. */
    fun assetFields(text: String): AssetDocumentFields {
        val root = supported(text, DocumentKind.ASSET)
        val type = root["type"]?.takeIf { it.isTextual }?.textValue()
        return AssetDocumentFields(
            MetaType.entries.firstOrNull { it.name == type } ?: MetaType.UNKNOWN,
            root["uuid"]?.asText(),
            root["additional"]?.get("clouds")?.takeIf { it.isTextual }?.textValue(),
            root.path("additional").path("terrainFile").asText(),
        )
    }

    /** Keeps the editor's existing wrong-type defaults, rather than runtime DTO coercions. */
    fun foliageMeta(text: String): FoliageMeta = supported(text, DocumentKind.ASSET)["additional"]
        ?.let { FoliageSettingsBinding().read(it) } ?: FoliageMeta()

    private fun supported(text: String, kind: DocumentKind) =
        SceneJson().parseObject(text).also { format.requireSupported(it, kind) }

    fun projectName(text: String): String? {
        val root = json.readObject(text)
        format.requireSupported(root, DocumentKind.PROJECT)
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
        val root = json.readObject(text)
        format.requireSupported(root, DocumentKind.SCENE)
        return json.bind(root, SceneDto::class.java)
    }

    fun scene(text: String): SceneDto = parse(text)

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

/** Validation owns the temporary tree; document creators only supply typed values. */
internal fun validateAssetDocument(document: AssetMetaDocument<*>, format: AbyssusDocumentFormat) =
    format.validate(SceneJson().mapper.valueToTree(document), DocumentKind.ASSET)

/** The UI decoder still takes a tree; keep that adapter beside the other document admission paths. */
internal fun admitUiDocument(
    document: AssetMetaDocument<*>,
    decoder: net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder,
): net.nevinsky.abyssus.lib.core.ui.model.UiAdmission = decoder.decode(SceneJson().mapper.valueToTree(document))
