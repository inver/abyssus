/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.format

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.dto.EntityType

enum class DocumentKind { PROJECT, SCENE, ASSET }
enum class FormatProblem { MARKER, VERSION, LEGACY_FIELD, ENTITY_SHAPE }
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
        if (ecs.has("componentIdentifiers")) {
            return FormatRejection(FormatProblem.LEGACY_FIELD, "ecs.componentIdentifiers")
        }
        if (ecs.get("entities")?.isObject == true) {
            return FormatRejection(FormatProblem.LEGACY_FIELD, "ecs.entities")
        }
        if (!ecs.isObject) {
            return null
        }
        for ((id, entity) in ecs.properties()) {
            if (id.toLongOrNull() == null || !entity.isObject)
                return FormatRejection(FormatProblem.ENTITY_SHAPE, "ecs.$id")
            if (entity.has("enabled"))
                return FormatRejection(FormatProblem.LEGACY_FIELD, "ecs.$id.enabled")
            val type = entity.get("type")
            if (type != null && !(type.isTextual && EntityType.entries.any { it.name == type.textValue() }))
                return FormatRejection(FormatProblem.ENTITY_SHAPE, "ecs.$id.type")
            val disabled = entity.get("disabledComponents")
            if (disabled != null && !(disabled.isArray && disabled.all { it.isTextual }))
                return FormatRejection(FormatProblem.ENTITY_SHAPE, "ecs.$id.disabledComponents")
            val flags = entity.get("flags")
            if (flags != null && !(flags.isIntegralNumber && flags.canConvertToInt()))
                return FormatRejection(FormatProblem.ENTITY_SHAPE, "ecs.$id.flags")
            val components = entity.get("components") ?: continue
            if (!components.isObject)
                return FormatRejection(FormatProblem.ENTITY_SHAPE, "ecs.$id.components")
            for (name in components.fieldNames()) {
                if (name.substringAfterLast('.') == "TypeComponent")
                    return FormatRejection(FormatProblem.LEGACY_FIELD, "ecs.$id.components.$name")
            }
            for (name in listOf("RenderComponent", "net.nevinsky.abyssus.lib.runtime.ecs.render.RenderComponent")) {
                val renderable = components.get(name)?.get("renderable")
                if (renderable?.has("class") == true)
                    return FormatRejection(FormatProblem.LEGACY_FIELD, "ecs.$id.components.$name.renderable.class")
            }
        }
        return null
    }

    fun requireSupported(document: JsonNode, kind: DocumentKind) {
        validate(document, kind)?.let { throw UnsupportedDocumentFormat(kind, it) }
    }

    fun requireEcs(ecs: JsonNode) {
        validateEcs(ecs)?.let { throw UnsupportedDocumentFormat(DocumentKind.SCENE, it) }
    }
}
