package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import com.badlogic.gdx.math.Vector3

/** Tree integration supplied by the project pane, without exposing tree nodes to the scene editor. */
interface SceneViewHost {
    fun listen(file: VirtualFile, parent: Disposable, selected: (String) -> Unit)
    fun select(file: VirtualFile, entityId: String)
    fun lightActions(file: VirtualFile, position: () -> Vector3): DefaultActionGroup
    fun assetActions(file: VirtualFile, position: () -> Vector3): DefaultActionGroup
    /** The project's models to arrange along a path; choosing one calls [start]. */
    fun pathActions(file: VirtualFile, start: (RenderAsset) -> Unit): DefaultActionGroup = DefaultActionGroup()
    /** Called with the id of the water surface of [file] selected in the tree. */
    fun listenWater(file: VirtualFile, parent: Disposable, selected: (String) -> Unit) {}
    fun selectWater(file: VirtualFile, waterId: String) {}
    /** The Lake and Sea presets, placed at [position] when chosen. */
    fun waterActions(file: VirtualFile, position: () -> Vector3): DefaultActionGroup = DefaultActionGroup()
    fun canAddWater(file: VirtualFile): Boolean = false
    fun canAddLight(file: VirtualFile): Boolean
    fun canAddPath(file: VirtualFile): Boolean = false
    fun canAddAsset(file: VirtualFile): Boolean

    /** Removes entity [entityId] of [file] as one undoable command; refused (and rejected) while it is still referenced. */
    fun removeEntity(file: VirtualFile, entityId: String): EditResult = EditResult.Unchanged
}
