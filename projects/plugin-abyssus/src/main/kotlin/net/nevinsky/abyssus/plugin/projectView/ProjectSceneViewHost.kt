package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.plugin.sceneview.SceneViewHost

/** Shared pane implementation also usable before the platform has created its tree component. */
class ProjectSceneViewHost(
    private val project: Project,
    private val metaFiles: MetaFiles,
) : SceneViewHost {
    /** The entry the pane and the editor provider use: resolves the application's meta files once. */
    constructor(project: Project) : this(project, service<AbyssusPluginCtx>().assets.metaFiles)

    override fun listen(file: VirtualFile, parent: Disposable, selected: (String) -> Unit) {
        project.messageBus.connect(parent).subscribe(AbyssusSelectionListener.TOPIC, AbyssusSelectionListener { node ->
            componentTargetOf(node)?.takeIf { it.file == file }?.let { selected(it.entityId) }
        })
    }
    override fun listenWater(file: VirtualFile, parent: Disposable, selected: (String) -> Unit) {
        project.messageBus.connect(parent).subscribe(AbyssusSelectionListener.TOPIC, AbyssusSelectionListener { node ->
            waterTargetOf(node)?.takeIf { it.file == file }?.let { selected(it.id) }
        })
    }
    override fun selectWater(file: VirtualFile, waterId: String) = selectWaterInAbyssusView(project, file, waterId)
    override fun waterActions(file: VirtualFile, position: () -> Vec3) = AddWaterGroup(project, file, position)
    override fun canAddWater(file: VirtualFile) = canAddWater(file, project.service<SceneDocumentCache>())
    override fun select(file: VirtualFile, entityId: String) = selectEntityInAbyssusView(project, file, entityId)
    override fun lightActions(file: VirtualFile, position: () -> Vec3) = AddLightGroup(project, file, position)
    override fun assetActions(file: VirtualFile, position: () -> Vec3) = AddAssetGroup(project, file, metaFiles, position)
    override fun pathActions(file: VirtualFile, start: (RenderAsset) -> Unit) = AddPathArrangementGroup(project, file, start)
    override fun canAddPath(file: VirtualFile) = canAddPathArrangement(file, project.service<SceneDocumentCache>())
    override fun canAddLight(file: VirtualFile) = canAddLight(file, project.service<SceneDocumentCache>())
    override fun canAddAsset(file: VirtualFile) = canAddAsset(file, project.service<SceneDocumentCache>()) && hasRenderAssets(file, metaFiles)
}
