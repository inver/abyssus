package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.editor.components.EditResult

/** Tree integration supplied by the project pane, without exposing tree nodes to the scene editor. */
interface SceneViewHost {
    fun listen(file: VirtualFile, parent: Disposable, selected: (Long) -> Unit)
    fun select(file: VirtualFile, entityId: Long)
    fun lightActions(file: VirtualFile, position: () -> Vector3): DefaultActionGroup
    fun assetActions(file: VirtualFile, position: () -> Vector3): DefaultActionGroup
    /** The project's models to arrange along a path; choosing one calls [start]. */
    fun pathActions(file: VirtualFile, start: (BaseMeta) -> Unit): DefaultActionGroup = DefaultActionGroup()
    // G7: the water members (listenWater, selectWater, waterActions, canAddWater) are disabled; scene water is not modeled.
    fun canAddLight(file: VirtualFile): Boolean
    fun canAddPath(file: VirtualFile): Boolean = false
    fun canAddAsset(file: VirtualFile): Boolean

    /** Removes entity [entityId] of [file] as one undoable command; refused (and rejected) while it is still referenced. */
    fun removeEntity(file: VirtualFile, entityId: Long): EditResult = EditResult.Unchanged
}
