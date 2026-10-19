package net.nevinsky.abyssus.plugin.projectView.asset

import net.nevinsky.abyssus.plugin.projectView.DropdownAction

class AssetDropdownAction : DropdownAction {
    override val actionIds = ASSETS_MENU_ACTION_IDS

    override fun appliesTo(node: Any?) = assetsNodeProjectFile(node) != null
}
