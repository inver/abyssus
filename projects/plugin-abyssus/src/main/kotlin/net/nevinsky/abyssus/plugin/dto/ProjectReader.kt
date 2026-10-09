package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.gdx.editor.document.DocumentParsing
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.dto.SceneDto
import net.nevinsky.abyssus.lib.gdx.assets.Asset
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE

@Service(Service.Level.PROJECT)
class ProjectReader(
    val project: Project,
    json: JsonProcessor?,
    scenes: SceneReader?,
    loading: DocumentParsing?,
) : ConfigFileReader<net.nevinsky.abyssus.plugin.dto.ProjectDto> {
    constructor(project: Project) : this(project, null, null, null)

    private val json by lazy { json ?: service<AbyssusCore>().documents.json }
    private val scenes by lazy { scenes ?: service<SceneReader>() }
    private val loading by lazy { loading ?: service<AbyssusCore>().documents.parsing }
    private val assetListing by lazy { ProjectAssetListing(this.json) }

    override fun stamp(file: VirtualFile): Long {
        return (ProjectLayout.sceneFiles(file)
            .fold(documentStamp(file)) { acc, f -> acc * 31 + documentStamp(f) }
                * 31
                + ProjectLayout.assetFolders(file)
            .fold(0L) { acc, dir ->
                (acc * 31 + dir.name.hashCode()) * 31 + (dir.findChild(META_FILE)?.let(::documentStamp)
                    ?: 0L)
            })
    }

    override fun read(file: VirtualFile): AssetReadResult<net.nevinsky.abyssus.plugin.dto.ProjectDto> = runCatchingKeepingCancellation {
        val name = loading.projectName(file.path) { textOf(file) }
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
