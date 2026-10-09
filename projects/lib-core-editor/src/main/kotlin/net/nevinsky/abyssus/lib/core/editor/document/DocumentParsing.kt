/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.document

import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.dto.SceneDto
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
    fun projectName(text: String): String? {
        val root = json.readObject(text)
        format.requireSupported(root, DocumentKind.PROJECT)
        return json.bind(root, ProjectName::class.java).name
    }

    /** [source] names the document in the log. */
    fun projectName(source: String, readText: () -> String): String? =
        reportedOrThrow("project $source") { projectName(readText()) }

    fun parse(text: String): SceneDto {
        val root = json.readObject(text)
        format.requireSupported(root, DocumentKind.SCENE)
        return json.bind(root, SceneDto::class.java)
    }

    /** Includes failures obtaining the text (an editor's filesystem) in the same logging boundary. */
    fun parse(source: String, readText: () -> String): SceneDto = reportedOrThrow("scene $source") { parse(readText()) }

    private fun <T> reportedOrThrow(source: String, read: () -> T): T = runCatchingKeepingCancellation(read)
        .getOrElse { error -> log.warn("Could not read $source: ${error.message}", error); throw error }
}

private data class ProjectName(val name: String? = null)
