package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.plugin.sceneview.SceneViewHost

/** Shared pane implementation also usable before the platform has created its tree component. */
class ProjectSceneViewHost(private val project: Project) : SceneViewHost {
    override fun listen(file: VirtualFile, parent: Disposable, selected: (String) -> Unit) {
        project.messageBus.connect(parent).subscribe(AbyssusSelectionListener.TOPIC, AbyssusSelectionListener { node ->
            componentTargetOf(node)?.takeIf { it.file == file }?.let { selected(it.entityId) }
        })
    }
    override fun select(file: VirtualFile, entityId: String) = selectEntityInAbyssusView(project, file, entityId)
    override fun lightActions(file: VirtualFile, position: () -> Vec3) = AddLightGroup(project, file, position)
    override fun assetActions(file: VirtualFile, position: () -> Vec3) = AddAssetGroup(project, file, position)
    override fun canAddLight(file: VirtualFile) = canAddLight(file, project.service<SceneDocumentCache>())
    override fun canAddAsset(file: VirtualFile) = canAddAsset(file, project.service<SceneDocumentCache>()) && hasRenderAssets(file)
}
