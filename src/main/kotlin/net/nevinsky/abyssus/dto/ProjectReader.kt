package net.nevinsky.abyssus.dto

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.JsonProcessor
import net.nevinsky.abyssus.dto.ProjectLayout.SCENES_DIR
import net.nevinsky.abyssus.dto.ProjectLayout.isScene
import net.nevinsky.abyssus.scene.SceneDto
import net.nevinsky.abyssus.sceneview.Asset
import net.nevinsky.abyssus.sceneview.ProjectAssetFiles

@Service(Service.Level.PROJECT)
class ProjectReader(val project: Project) : ConfigFileReader<ProjectDto> {
    override fun stamp(file: VirtualFile): Long {
        return (ProjectLayout.sceneFiles(file)
            .fold(file.modificationStamp) { acc, f -> acc * 31 + f.modificationStamp }
                * 31
                + ProjectLayout.assetFolders(file)
            .fold(0L) { acc, dir ->
                (acc * 31 + dir.name.hashCode()) * 31 + (dir.findChild(ProjectLayout.META_FILE)?.modificationStamp
                    ?: 0L)
            })
    }

    override fun read(file: VirtualFile): AssetReadResult<ProjectDto> = runCatchingKeepingCancellation {
        val name = service<JsonProcessor>().parse(file.text(), ProjectDto::class.java).name
        val scenes = sceneFiles(file).map { it to service<SceneReader>().read(it) }
        val roots = scenes.flatMap { (_, result) -> result.obj?.let(::sceneReferences) ?: emptySet() }
            .toSet()
        val assets = readAssets(file)
        val used = usedAssets(assets, roots)
        ProjectDto(
            name ?: file.nameWithoutExtension,
            scenes.map { (f, result) -> result.obj ?: SceneError(f, result.message) },
            assets.map { it.copy(unused = it.name !in used) },
        )
    }.fold(
        { AssetReadResult.success(it) },
        { AssetReadResult.failure(it.message ?: it.javaClass.simpleName) },
    )

    private fun readAssets(abss: VirtualFile): List<Asset<Any>> = runCatchingKeepingCancellation {
        return project.getService(ProjectAssetFiles::class.java).loadShortAssets(abss)
    }.getOrElse { emptyList() }

    private fun sceneFiles(projectFile: VirtualFile): List<VirtualFile> =
        projectFile.parent?.findChild(SCENES_DIR)?.children
            ?.filter(::isScene)
            ?.sortedBy { it.name }
            ?: emptyList()


    /** Folder names of the assets reachable from [roots] (folder names) through `uuid` references, transitively. */
    private fun usedAssets(assets: List<Asset<Any>>, roots: Set<String>): Set<String> {
        val byName = assets.associateBy { it.name }
        val byUuid = assets.filter { it.meta.uuid != null }.associateBy { it.meta.uuid.toString() }
        val used = mutableSetOf<String>()
        val pending = ArrayDeque<Asset<Any>>()
        fun visit(asset: Asset<Any>?) {
            if (asset != null && used.add(asset.name)) {
                pending += asset
            }
        }
        roots.forEach { visit(byName[it]) }
        while (pending.isNotEmpty()) {
            pending.removeFirst().references.forEach { visit(byUuid[it]) }
        }
        return used
    }
}

/** What a scene names directly: `assetName` and `shaderKey` values in its ECS data, and `skyboxName`. */
fun sceneReferences(scene: SceneDto): Set<String> {
    val names = mutableSetOf<String>()
    scene.ecs?.let { ecs ->
        for (field in listOf("assetName", "shaderKey")) ecs.findValues(field)
            .forEach { v -> if (v.isTextual) names += v.asText() }
    }
    scene.skyboxName?.let { names += it }
    return names
}
