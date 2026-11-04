/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.ui.model.UiActionType

/**
 * The action a widget stores: [type] with the [target] member it needs (a screen id, or an action name for
 * `emitAction`).
 */
fun uiActionDoc(type: UiActionType, target: String?, assetName: String?): UiActionDoc =
    UiActionDoc().apply {
        this.type = type.json
        this.assetName = assetName
        when (type) {
            UiActionType.OPEN_SCREEN, UiActionType.SHOW_OVERLAY, UiActionType.CLOSE_OVERLAY -> screenId = target
            UiActionType.EMIT_ACTION -> name = target
            UiActionType.BACK -> Unit
        }
    }

/** An edit of a UI document: the host writes the lambda and passes the document it receives to [UiDocumentEdits]. */
typealias UiTreeEdit = (UiDocumentEdits, UiMetaDoc) -> UiEditResult
