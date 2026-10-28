/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.ui.model.UiActionType

/** A text value for a UI document member. */
fun uiTextNode(value: String): JsonNode = JsonNodeFactory.instance.textNode(value)

/** The action object a widget stores: [type] with the [target] member it needs (a screen id, or an action name for `emitAction`). */
fun uiActionNode(type: UiActionType, target: String?, assetName: String?): JsonNode =
    JsonNodeFactory.instance.objectNode().apply {
        put("type", type.json)
        if (assetName != null) put("assetName", assetName)
        when (type) {
            UiActionType.OPEN_SCREEN, UiActionType.SHOW_OVERLAY, UiActionType.CLOSE_OVERLAY -> target?.let { put("screenId", it) }
            UiActionType.EMIT_ACTION -> target?.let { put("name", it) }
            UiActionType.BACK -> Unit
        }
    }

/** An edit of a UI document: the host writes the lambda and passes the tree it receives to [UiDocumentEdits]. */
typealias UiTreeEdit = (UiDocumentEdits, JsonNode) -> UiEditResult
