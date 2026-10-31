/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.plugin.AbyssusBundle

/** Tells the user why an edit was refused; anything else needs no word. */
internal fun reportRejection(project: Project, result: EditResult) {
    if (result is EditResult.Rejected) {
        Messages.showErrorDialog(project, result.reason, AbyssusBundle.message("componentRejectedTitle"))
    }
}
