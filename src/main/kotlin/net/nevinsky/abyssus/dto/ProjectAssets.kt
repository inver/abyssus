package net.nevinsky.abyssus.dto

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.scene.SceneDto

/**
 * One folder under a project's `assets`. [references] are the `uuid`s its `meta.json` holds in the fields Mundus
 * resolves to other assets; files named in `meta.json` live in the asset's own folder and are not references.
 */
data class AssetInfo(val name: String, val uuid: String?, val type: String?, val references: List<String>)

object ProjectAssets {
    const val ASSETS_DIR = "assets"
    const val META_FILE = "meta.json"

    /** `meta.json` fields of an asset's `additional` block that hold another asset's `uuid` (`TerrainMeta`). */
    private val REFERENCE_FIELDS = listOf("splatMap", "splatBase", "splatR", "splatG", "splatB", "splatA")

    /** Fields holding a list of asset `uuid`s (`ModelMeta.materials`). */
    private val REFERENCE_LISTS = listOf("materials")

    fun assetFolders(abss: VirtualFile): List<VirtualFile> =
        abss.parent?.findChild(ASSETS_DIR)?.children?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()

    /** Changes when an asset folder is added, removed or renamed, or any `meta.json` changes. */
    fun stamp(abss: VirtualFile): Long =
        assetFolders(abss).fold(0L) { acc, dir ->
            (acc * 31 + dir.name.hashCode()) * 31 + (dir.findChild(META_FILE)?.modificationStamp ?: 0L)
        }

    fun read(abss: VirtualFile): List<AssetInfo> = assetFolders(abss).map { dir ->
        val meta = runCatching { dir.findChild(META_FILE)?.let { Json.parseObject(String(it.contentsToByteArray(), it.charset)) } }.getOrNull()
        parse(dir.name, meta)
    }

    fun parse(name: String, meta: JsonNode?): AssetInfo {
        val additional = meta?.opt("additional")?.takeIf { it.isObject }
        val references = additional?.let { a ->
            REFERENCE_FIELDS.mapNotNull { a.opt(it)?.takeIf(JsonNode::isTextual)?.asText() } +
                REFERENCE_LISTS.flatMap { f -> a.opt(f)?.takeIf { it.isArray }?.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() } ?: emptyList() }
        } ?: emptyList()
        return AssetInfo(
            name,
            meta?.opt("uuid")?.takeIf(JsonNode::isTextual)?.asText(),
            meta?.opt("type")?.takeIf(JsonNode::isTextual)?.asText(),
            references,
        )
    }

    /**
     * What a scene names directly: `assetName` and `shaderKey` values anywhere in its ECS data, and `skyboxName`.
     * A name matching no asset folder (a bundled shader) is simply never found later.
     */
    fun sceneReferences(scene: SceneDto): Set<String> {
        val names = mutableSetOf<String>()
        scene.ecs?.let { ecs ->
            for (field in listOf("assetName", "shaderKey")) ecs.findValues(field).forEach { v -> if (v.isTextual) names += v.asText() }
        }
        scene.skyboxName?.let { names += it }
        return names
    }

    /** Folder names of the assets reachable from [roots] (folder names) through `uuid` references, transitively. */
    fun usedAssets(assets: List<AssetInfo>, roots: Set<String>): Set<String> {
        val byName = assets.associateBy { it.name }
        val byUuid = assets.filter { it.uuid != null }.associateBy { it.uuid!! }
        val used = mutableSetOf<String>()
        val pending = ArrayDeque<AssetInfo>()
        fun visit(asset: AssetInfo?) {
            if (asset != null && used.add(asset.name)) pending += asset
        }
        roots.forEach { visit(byName[it]) }
        while (pending.isNotEmpty()) pending.removeFirst().references.forEach { visit(byUuid[it]) }
        return used
    }
}
