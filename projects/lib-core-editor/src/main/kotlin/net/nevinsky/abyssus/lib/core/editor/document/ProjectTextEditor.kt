/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.dto.ProjectDto
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/**
 * The text transform behind every `.abss` write: bind the text to a [ProjectDto], let the caller make a changed copy,
 * and print it with [JsonProcessor.pretty]. The file is re-printed in the standard style; keys [ProjectDto] does not
 * model are not kept.
 */
class ProjectTextEditor(private val parsing: DocumentParsing, private val json: JsonProcessor) {
    /** [change] returns the edited project, or null to decline; an equal project is [TextEditOutcome.Unchanged]. */
    fun edit(text: String, change: (ProjectDto) -> ProjectDto?): TextEditOutcome {
        val before = runCatchingKeepingCancellation { parsing.project(text) }
            .getOrElse { return TextEditOutcome.Refused(it) }

        val after = change(before) ?: return TextEditOutcome.Unchanged
        if (after == before) {
            return TextEditOutcome.Unchanged
        }

        return runCatchingKeepingCancellation { json.pretty(after) }
            .fold({ TextEditOutcome.Edited(it) }, { TextEditOutcome.Refused(it) })
    }
}
