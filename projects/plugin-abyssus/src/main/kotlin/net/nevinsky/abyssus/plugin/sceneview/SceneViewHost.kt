package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.content.Vec3

/** Tree integration supplied by the project pane, without exposing tree nodes to the scene editor. */
interface SceneViewHost {
    fun listen(file: VirtualFile, parent: Disposable, selected: (String) -> Unit)
    fun select(file: VirtualFile, entityId: String)
    fun lightActions(file: VirtualFile, position: () -> Vec3): DefaultActionGroup
    fun assetActions(file: VirtualFile, position: () -> Vec3): DefaultActionGroup
    fun canAddLight(file: VirtualFile): Boolean
    fun canAddAsset(file: VirtualFile): Boolean
}
