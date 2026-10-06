/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.document

import net.nevinsky.abyssus.core.assets.displayMessage
import net.nevinsky.abyssus.core.format.UnsupportedDocumentFormat
import net.nevinsky.abyssus.editor.EditorMessages

/** Why a document could not be used, as shown to the user: a native-format refusal names its problem and path. */
fun Throwable.documentDisplayMessage(messages: EditorMessages): String = when (this) {
    is UnsupportedDocumentFormat -> messages.message("unsupportedFormat.${reason.problem.name}", reason.path)
    else -> displayMessage()
}
