package net.nevinsky.abyssus.plugin.dto

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.editor.document.DocumentParsing
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx

@Service(Service.Level.PROJECT)
class ProjectReader(
    val project: Project,
    private val json: JsonProcessor,
    private val scenes: SceneReader,
    private val loading: DocumentParsing,
) : ConfigFileReader<net.nevinsky.abyssus.plugin.dto.ProjectDto> {
    /** The platform's entry: resolves the application collaborators once. */
    constructor(project: Project) : this(
        project, service<AbyssusPluginCtx>().documents.json, service<SceneReader>(), service<AbyssusPluginCtx>().documents.parsing,
    )

    private val assetListing by lazy { ProjectAssetListing(json) }

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

    override fun read(file: VirtualFile): AssetReadResult<net.nevinsky.abyssus.plugin.dto.ProjectDto> =
        runCatchingKeepingCancellation {
            val name = loading.projectName(file.path) { textOf(file) }
            val sceneResults = ProjectLayout.sceneFiles(file).map { it to scenes.read(it) }
            val roots = sceneResults.flatMap { (_, result) -> result.obj?.let(::sceneReferences) ?: emptySet() }
                .toSet()
            val assets = readAssets(file)
            val usage = usage(assets, roots, loading.uiAssetNames(file.path) { textOf(file) })
            ProjectDto(
                name ?: file.nameWithoutExtension,
                sceneResults.map { (f, result) ->
                    result.obj?.let { SceneEntry(f, it) } ?: SceneError(
                        f,
                        result.message
                    )
                },
                assets.map { it.copy(unused = it.name !in usage.used) },
                usage.uiIssues,
            )
        }.let { AssetReadResult.of(it) }

    private fun readAssets(abss: VirtualFile): List<Asset<Any>> = runCatchingKeepingCancellation {
        return assetListing.list(abss)
    }.getOrElse { emptyList() }

    /** Folder names reachable from scene roots through UUID links and foliage's folder-name links, transitively. */
    fun usedAssets(assets: List<Asset<Any>>, roots: Set<String>): Set<String> = usage(assets, roots, emptyList()).used

    /** [usedAssets] with the project's registered UI assets as further roots, and the problems found along the UI links. */
    fun usage(assets: List<Asset<Any>>, roots: Set<String>, uiAssetNames: List<String>): AssetUsage {
        val ui = UiAssetUsage(assets, uiAssetNames)
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
        ui.roots().forEach { visit(it) }
        while (pending.isNotEmpty()) {
            val asset = pending.removeFirst()
            asset.references.forEach { visit(byUuid[it]) }
            ui.links(asset).forEach { visit(it) }
            if (asset.meta.type == MetaType.FOLIAGE) {
                val additional = asset.meta.additional as? Map<*, *> ?: continue
                (additional["terrain"] as? String)?.let { visit(byName[it]) }
                val layers = additional["layers"] as? List<*> ?: continue
                for (layer in layers) {
                    val models = (layer as? Map<*, *>)?.get("models") as? List<*> ?: continue
                    for (model in models) {
                        val name = (model as? Map<*, *>)?.get("asset") as? String ?: continue
                        visit(byName[name])
                    }
                }
            }
        }
        return AssetUsage(used, ui.issues(used))
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
