package net.nevinsky.abyssus.plugin.projectView.scene

import net.nevinsky.abyssus.plugin.projectView.DropdownAction

class SceneDropdownAction : DropdownAction {
    override val actionIds = SCENE_MENU_ACTION_IDS

    override fun appliesTo(node: Any?) = sceneNodeProjectFile(node) != null
}
