/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import net.nevinsky.abyssus.lib.core.ui.model.UiNavigation
import net.nevinsky.abyssus.lib.core.ui.model.UiScreenKind
import net.nevinsky.abyssus.plugin.AbyssusBundle
import javax.swing.JButton
import javax.swing.SwingConstants

/** Asks for a screen name; null when cancelled or blank. */
typealias UiNameAsker = (prompt: String, initial: String) -> String?

private fun askWithDialog(project: Project?): UiNameAsker = { prompt, initial ->
    Messages.showInputDialog(
        project,
        AbyssusBundle.message(prompt),
        AbyssusBundle.message("uiScreens"),
        null,
        initial,
        null
    )?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * The canvas toolbar's screens control: a button showing the displayed screen that opens a menu of every screen (entry and
 * overlay marked) with Add Screen, Add Overlay, Rename, Delete and the arrow-focus switch. Every choice is a call on the
 * one [controller].
 */
class UiScreensMenu(
    private val controller: UiEditorController,
    project: Project?,
    private val ask: UiNameAsker = askWithDialog(project),
) : JButton() {
    init {
        name = "screens-button"
        icon = AllIcons.General.ArrowDown
        horizontalTextPosition = SwingConstants.LEADING
        addActionListener { open() }
        controller.addListener(::refresh)
        refresh()
    }

    /** The screen button's text: the displayed screen's name and marks. */
    private fun refresh() {
        val c = controller.collection
        val screen = c?.screen(controller.screenId.orEmpty())
        text = screen?.let { label(it.id) } ?: AbyssusBundle.message("uiScreens")
        isEnabled = c != null
    }

    /** A screen's menu text: its name, "(overlay)" for an overlay, and the entry mark. */
    internal fun label(id: String): String {
        val c = controller.collection ?: return id
        val s = c.screen(id) ?: return id
        val entry =
            s.id == c.entryScreen || (c.entryScreen == null && s === c.screens.firstOrNull { it.kind == UiScreenKind.SCREEN })
        return buildString {
            append(s.name)
            if (s.kind == UiScreenKind.OVERLAY) append(" (").append(AbyssusBundle.message("uiOverlayMark")).append(")")
            if (entry) append("  [").append(AbyssusBundle.message("uiEntryScreen")).append("]")
        }
    }

    /** The menu's entries in order: one per screen, then the management actions. */
    internal fun group(): DefaultActionGroup = DefaultActionGroup().apply {
        controller.collection?.screens?.forEach { add(ScreenChoice(it.id)) }
        add(Separator.getInstance())
        add(plain("uiAddScreen") { addScreen("screen") })
        add(plain("uiAddOverlay") { addScreen("overlay") })
        add(plain("uiRenameScreen") { rename() })
        add(plain("uiDeleteScreen") { delete() })
        add(Separator.getInstance())
        add(ArrowsToggle())
    }

    private fun open() {
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                AbyssusBundle.message("uiScreens"),
                group(),
                DataManager.getInstance().getDataContext(this),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
            .showUnderneathOf(this)
    }

    internal fun addScreen(kind: String) {
        ask("uiScreenNamePrompt", "")?.let { controller.addScreen(it, kind) }
    }

    internal fun rename() {
        val id = controller.screenId ?: return
        ask(
            "uiScreenRenamePrompt",
            controller.collection?.screen(id)?.name.orEmpty()
        )?.let { controller.renameScreen(id, it) }
    }

    internal fun delete() {
        controller.screenId?.let(controller::deleteScreen)
    }

    internal val arrows: Boolean get() = controller.collection?.screen(controller.screenId.orEmpty())?.navigation == UiNavigation.ARROWS

    internal fun setArrows(on: Boolean) {
        controller.screenId?.let { controller.setNavigation(it, on) }
    }

    private fun plain(key: String, run: () -> Unit): AnAction = object : AnAction(AbyssusBundle.message(key)) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun actionPerformed(e: AnActionEvent) = run()
    }

    private inner class ScreenChoice(private val id: String) : ToggleAction(label(id)) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun isSelected(e: AnActionEvent) = controller.screenId == id
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) controller.selectScreen(id)
        }
    }

    private inner class ArrowsToggle : ToggleAction(AbyssusBundle.message("uiScreenArrows")) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun isSelected(e: AnActionEvent) = arrows
        override fun setSelected(e: AnActionEvent, state: Boolean) = setArrows(state)
    }
}
