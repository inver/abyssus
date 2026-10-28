/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditResult
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.projectView.AbyssusTreeAction
import net.nevinsky.abyssus.plugin.projectView.action.ui.UiFolder
import net.nevinsky.abyssus.plugin.projectView.action.ui.uiFolder

/** Right-click on the UI folder: add a screen (or an overlay) to one of its UI assets. */
abstract class NewUiScreenActionBase(private val kind: String, textKey: String) : AbyssusTreeAction<UiFolder>() {
    init {
        templatePresentation.text = AbyssusBundle.message(textKey)
    }

    override fun targetOf(node: Any?): UiFolder? = uiFolder(node)

    override fun isEnabled(e: AnActionEvent, target: UiFolder) = target.assetNames.isNotEmpty()

    override fun perform(project: Project, target: UiFolder, e: AnActionEvent) {
        val asset = chooseAsset(project, target.assetNames) ?: return
        val meta = target.abss.parent?.findChild(ASSETS_DIR)?.findChild(asset)?.findChild(META_FILE) ?: return
        val name = Messages.showInputDialog(
            project, AbyssusBundle.message("uiScreenNamePrompt"), AbyssusBundle.message("uiCommandAddScreen"), null,
        )?.takeIf { it.isNotBlank() } ?: return
        val outcome = editUiMeta(project, meta, AbyssusBundle.message("uiCommandAddScreen")) { edits, json ->
            edits.addScreen(json, name, kind)
        }
        if (outcome !is UiEditOutcome.Edited || outcome.result !is UiEditResult.Added) {
            Messages.showErrorDialog(
                project,
                AbyssusBundle.message("uiScreenAddFailed", asset),
                AbyssusBundle.message("uiCommandAddScreen")
            )
        }
    }

    /** The only UI asset, or the one the user picks; null when cancelled. */
    private fun chooseAsset(project: Project, names: List<String>): String? {
        if (names.size == 1) return names.single()
        val index = Messages.showChooseDialog(
            project, AbyssusBundle.message("uiScreenAssetPrompt"), AbyssusBundle.message("uiCommandAddScreen"),
            null, names.toTypedArray(), names.first(),
        )
        return names.getOrNull(index)
    }
}

open class NewUiScreenAction : NewUiScreenActionBase("screen", "uiCreateScreen")

open class NewUiOverlayAction : NewUiScreenActionBase("overlay", "uiCreateOverlay")
