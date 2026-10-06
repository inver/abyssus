/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.format

import com.fasterxml.jackson.databind.JsonNode

enum class DocumentKind { PROJECT, SCENE, ASSET }
enum class FormatProblem { MARKER, VERSION, LEGACY_FIELD }
data class FormatRejection(val problem: FormatProblem, val path: String)

class UnsupportedDocumentFormat(val kind: DocumentKind, val reason: FormatRejection) :
    IllegalArgumentException("Unsupported ${kind.name.lowercase()} format: ${reason.path} (${reason.problem.name.lowercase()}); requires Abyssus format version 1")

/** Pure admission checks, on the caller's thread. Custom component and marker payloads stay opaque. */
class AbyssusDocumentFormat {
    fun validate(document: JsonNode, kind: DocumentKind): FormatRejection? {
        val marker = document.get("format")
        if (marker == null || !marker.isTextual || marker.textValue() != "abyssus")
            return FormatRejection(FormatProblem.MARKER, "format")
        val version = document.get("formatVersion")
        if (version == null || !version.isIntegralNumber || !version.canConvertToInt() || version.intValue() != 1)
            return FormatRejection(FormatProblem.VERSION, "formatVersion")
        return if (kind == DocumentKind.SCENE) document.get("ecs")?.let(::validateEcs) else null
    }

    /** Raw ECS helpers have no enclosing header; they must still refuse reserved legacy dispatch fields. */
    fun validateEcs(ecs: JsonNode): FormatRejection? {
        if (ecs.has("componentIdentifiers"))
            return FormatRejection(FormatProblem.LEGACY_FIELD, "ecs.componentIdentifiers")
        val wrapped = ecs.get("entities")?.takeIf { it.isObject }
        val entities = wrapped ?: ecs
        if (!entities.isObject) return null
        for ((id, entity) in entities.properties()) {
            val components = entity.get("components") ?: continue
            for (name in listOf("RenderComponent", "net.nevinsky.abyssus.lib.runtime.ecs.render.RenderComponent")) {
                val renderable = components.get(name)?.get("renderable")
                if (renderable?.has("class") == true)
                    return FormatRejection(
                        FormatProblem.LEGACY_FIELD,
                        (if (wrapped != null) "ecs.entities.$id" else "ecs.$id") + ".components.$name.renderable.class"
                    )
            }
        }
        return null
    }

    fun requireRenderable(renderable: JsonNode) {
        if (renderable.has("class")) throw UnsupportedDocumentFormat(
            DocumentKind.SCENE, FormatRejection(FormatProblem.LEGACY_FIELD, "RenderComponent.renderable.class"),
        )
    }

    fun requireSupported(document: JsonNode, kind: DocumentKind) {
        validate(document, kind)?.let { throw UnsupportedDocumentFormat(kind, it) }
    }

    fun requireEcs(ecs: JsonNode) {
        validateEcs(ecs)?.let { throw UnsupportedDocumentFormat(DocumentKind.SCENE, it) }
    }
}
