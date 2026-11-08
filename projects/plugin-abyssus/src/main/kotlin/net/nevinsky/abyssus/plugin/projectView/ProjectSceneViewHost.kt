package net.nevinsky.abyssus.plugin.projectView

import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.sceneview.SceneViewHost

/** Shared pane implementation also usable before the platform has created its tree component. */
class ProjectSceneViewHost(
    private val project: Project,
    private val metaFiles: MetaFiles,
) : SceneViewHost {
    /** The entry the pane and the editor provider use: resolves the application's meta files once. */
    constructor(project: Project) : this(project, service<AbyssusPluginCtx>().assets.metaFiles)

    override fun listen(file: VirtualFile, parent: Disposable, selected: (Long) -> Unit) {
        project.messageBus.connect(parent).subscribe(AbyssusSelectionListener.TOPIC, AbyssusSelectionListener { node ->
            componentTargetOf(node)?.takeIf { it.file == file }?.let { selected(it.entityId) }
        })
    }

    override fun listenPart(file: VirtualFile, parent: Disposable, selected: (Long, String) -> Unit) {
        project.messageBus.connect(parent).subscribe(AbyssusSelectionListener.TOPIC, AbyssusSelectionListener { node ->
            partTargetOf(node)?.takeIf { it.file == file }?.let { selected(it.entityId, it.node) }
        })
    }

    override fun selectPart(file: VirtualFile, entityId: Long, node: String) =
        selectPartInAbyssusView(project, file, entityId, node)

    override fun removeEntity(file: VirtualFile, entityId: Long): EditResult =
        SceneComponentEdits.removeEntity(project, file, entityId)

    // G7: the water members of the host (listenWater, selectWater, waterActions, canAddWater) are disabled.
    override fun select(file: VirtualFile, entityId: Long) = selectEntityInAbyssusView(project, file, entityId)
    override fun lightActions(file: VirtualFile, position: () -> Vector3) = AddLightGroup(project, file, position)
    override fun assetActions(file: VirtualFile, position: () -> Vector3) =
        AddAssetGroup(project, file, metaFiles, position, meshes = projectModelMeshes(file))

    override fun pathActions(file: VirtualFile, start: (BaseMeta) -> Unit) =
        AddPathArrangementGroup(project, file, start)

    override fun canAddPath(file: VirtualFile) = canAddPathArrangement(file, project.service<SceneDocumentCache>())
    override fun canAddLight(file: VirtualFile) = canAddLight(file, project.service<SceneDocumentCache>())
    override fun canAddAsset(file: VirtualFile) =
        canAddAsset(file, project.service<SceneDocumentCache>()) && hasRenderAssets(file, metaFiles)
}
