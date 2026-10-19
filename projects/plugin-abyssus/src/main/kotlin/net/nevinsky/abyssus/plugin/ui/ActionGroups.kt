/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.Component

fun showActionGroup(e: AnActionEvent, group: ActionGroup, title: String?) =
    showActionGroup(e.dataContext, group, title)

/** Buttons retain their underneath placement; actions use the context's best position. EDT only. */
fun showActionGroup(context: DataContext, group: ActionGroup, title: String?, underneath: Component? = null) {
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        title, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
    )
    if (underneath == null) popup.showInBestPositionFor(context) else popup.showUnderneathOf(underneath)
}
