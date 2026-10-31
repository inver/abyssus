/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.water

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.plugin.AbyssusBundle

/** Tells the user why a water edit was refused; anything else needs no word. */
internal fun reportWaterRejection(project: Project, edit: WaterEdit) {
    if (edit is WaterEdit.Rejected) Messages.showErrorDialog(
        project,
        waterErrorText(edit.error),
        AbyssusBundle.message("waterRejectedTitle")
    )
}
