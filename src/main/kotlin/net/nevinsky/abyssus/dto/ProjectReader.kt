package net.nevinsky.abyssus.dto

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.runtime.scene.SceneDto
import net.nevinsky.abyssus.assets.files.Asset
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.assets.META_FILE

@Service(Service.Level.PROJECT)
class ProjectReader(
    val project: Project,
    private val json: JsonProcessor,
    private val scenes: SceneReader,
    private val loading: net.nevinsky.abyssus.runtime.SceneLoading,
) : ConfigFileReader<ProjectDto> {
    private val assetListing = ProjectAssetListing(json)

    /** What the platform creates: the one place this service looks up what it needs. */
    constructor(project: Project) : this(project, service<AbyssusCore>().json, service<SceneReader>(), service<AbyssusCore>().scenes)

    override fun stamp(file: VirtualFile): Long {
        return (ProjectLayout.sceneFiles(file)
            .fold(file.modificationStamp) { acc, f -> acc * 31 + f.modificationStamp }
                * 31
                + ProjectLayout.assetFolders(file)
            .fold(0L) { acc, dir ->
                (acc * 31 + dir.name.hashCode()) * 31 + (dir.findChild(META_FILE)?.modificationStamp
                    ?: 0L)
            })
    }

    override fun read(file: VirtualFile): AssetReadResult<ProjectDto> = runCatchingKeepingCancellation {
        val name = loading.projectName(file.path) { file.text() }
        val sceneResults = ProjectLayout.sceneFiles(file).map { it to scenes.read(it) }
        val roots = sceneResults.flatMap { (_, result) -> result.obj?.let(::sceneReferences) ?: emptySet() }
            .toSet()
        val assets = readAssets(file)
        val used = usedAssets(assets, roots)
        ProjectDto(
            name ?: file.nameWithoutExtension,
            sceneResults.map { (f, result) -> result.obj?.let { SceneEntry(f, it) } ?: SceneError(f, result.message) },
            assets.map { it.copy(unused = it.name !in used) },
        )
    }.let { AssetReadResult.of(it) }

    private fun readAssets(abss: VirtualFile): List<Asset<Any>> = runCatchingKeepingCancellation {
        return assetListing.list(abss)
    }.getOrElse { emptyList() }

    /** Folder names of the assets reachable from [roots] (folder names) through `uuid` references, transitively. */
    fun usedAssets(assets: List<Asset<Any>>, roots: Set<String>): Set<String> {
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
