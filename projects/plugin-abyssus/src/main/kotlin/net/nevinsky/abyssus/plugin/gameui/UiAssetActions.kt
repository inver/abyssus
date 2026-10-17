/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.terrain.checkFolderName
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.projectView.AbyssusTreeAction
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode
import net.nevinsky.abyssus.plugin.projectView.asset.assetsNodeProjectFile
import java.io.File

/** The names in the project file's `uiAssetNames`, from its current text; empty when it is missing or malformed. */
fun registeredUiAssets(abss: VirtualFile): List<String> = runCatchingKeepingCancellation {
    SceneJson().parse(textOf(abss))["uiAssetNames"]?.takeIf { it.isArray }
        ?.mapNotNull { it.takeIf { n -> n.isTextual }?.textValue() }
}.getOrNull().orEmpty()

/** Right-click on the Assets node: create a UI asset in this project. */
open class NewUiAssetAction : AbyssusTreeAction<VirtualFile>() {
    init {
        templatePresentation.text = AbyssusBundle.message("uiNewAssetAction")
    }

    override fun targetOf(node: Any?): VirtualFile? = assetsNodeProjectFile(node)

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        val abss = target
        val assets = File(abss.parent.path, "assets")
        val name = Messages.showInputDialog(
            project,
            AbyssusBundle.message("uiNewAssetPrompt"),
            AbyssusBundle.message("commandNewUiAsset"),
            null,
            "menus",
            object : InputValidator {
                override fun checkInput(input: String?) = input != null && checkFolderName(assets, input) == null
                override fun canClose(input: String?) = checkInput(input)
            }) ?: return
        createUiAsset(project, abss, name)
    }
}

/** A UI asset row of the Abyssus view, with the project file that owns it and whether it is registered. */
class UiAssetTarget(val abss: VirtualFile, val name: String, val registered: Boolean)

internal fun uiAssetTarget(node: Any?): UiAssetTarget? = runCatchingKeepingCancellation {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val asset = entry.value as? Asset<*> ?: return null
    val abss = entry.source?.takeIf { it.isValid && it.extension == "abss" } ?: return null
    if (asset.type != MetaType.UI) return null
    UiAssetTarget(abss, asset.name, asset.name in registeredUiAssets(abss))
}.getOrNull()

open class RegisterUiAssetAction : AbyssusTreeAction<UiAssetTarget>() {
    init {
        templatePresentation.text = AbyssusBundle.message("uiRegisterAction")
    }

    override fun targetOf(node: Any?) = uiAssetTarget(node)?.takeIf { !it.registered }
    override fun perform(project: Project, target: UiAssetTarget, e: AnActionEvent) {
        registerUiAsset(project, target.abss, target.name)
    }
}

open class UnregisterUiAssetAction : AbyssusTreeAction<UiAssetTarget>() {
    init {
        templatePresentation.text = AbyssusBundle.message("uiUnregisterAction")
    }

    override fun targetOf(node: Any?) = uiAssetTarget(node)?.takeIf { it.registered }
    override fun perform(project: Project, target: UiAssetTarget, e: AnActionEvent) {
        unregisterUiAsset(project, target.abss, target.name)
    }
}

/**
 * Other UI assets of the project, for navigation in a preview: only registered names, read from the editor's current text
 * (unsaved included), the way a game opening a registered collection sees them.
 */
class ProjectUiSource(
    private val abss: VirtualFile?,
    private val decoder: net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder = net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder()
) :
    net.nevinsky.abyssus.lib.core.ui.state.UiCollectionSource {
    override fun collection(assetName: String): net.nevinsky.abyssus.lib.core.ui.model.UiCollection? {
        val project = abss ?: return null
        if (assetName !in registeredUiAssets(project)) return null
        val meta = project.parent?.findChild("assets")?.findChild(assetName)?.findChild("meta.json") ?: return null
        val tree = runCatchingKeepingCancellation { SceneJson().parse(textOf(meta)) }.getOrNull() ?: return null
        return (decoder.decode(tree) as? net.nevinsky.abyssus.lib.core.ui.model.UiAdmission.Admitted)?.collection
    }
}
