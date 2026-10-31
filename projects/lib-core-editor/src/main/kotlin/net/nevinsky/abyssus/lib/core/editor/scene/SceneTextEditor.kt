/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.scene.SceneParser

/**
 * The text transform behind every scene write: parse the text into a [SceneDto], let the edit change it, and write it
 * back through [SceneSerializer]. The plugin's `editSceneJson` wraps it in one undoable command; a caller without the
 * IDE uses it directly.
 */
class SceneTextEditor(
    private val parser: SceneParser,
    private val serializer: SceneSerializer,
) {
    fun edit(text: String, edit: SceneEdit): TextEditOutcome {
        val scene = runCatchingKeepingCancellation { parser.parse(text) }
            .getOrElse { return TextEditOutcome.Refused(it) }
        if (!edit.apply(scene)) return TextEditOutcome.Unchanged
        return runCatchingKeepingCancellation { serializer.serialize(scene) }
            .fold({ TextEditOutcome.Edited(it) }, { TextEditOutcome.Refused(it) })
    }
}
