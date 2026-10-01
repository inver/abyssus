package net.nevinsky.abyssus.dto

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.project.sceneLabel
import net.nevinsky.abyssus.scene.BaseLightDto
import net.nevinsky.abyssus.scene.ColorDto
import net.nevinsky.abyssus.scene.FogDto
import net.nevinsky.abyssus.scene.SceneDto

sealed interface AssetReadResult {
    data class Success(val root: DtoValue.Obj) : AssetReadResult
    data class Failure(val message: String) : AssetReadResult
}

/** Reads one asset file into its DTO property tree. Never writes and never throws. */
interface AssetReader {
    fun read(file: VirtualFile): AssetReadResult

    /** Part of the cache key: changes whenever anything this reader depends on changes. */
    fun stamp(file: VirtualFile): Long = file.modificationStamp

    companion object {
        fun forExtension(extension: String?): AssetReader? = when (extension) {
            "scene" -> SceneReader
            "abss" -> ProjectReader
            else -> null
        }
    }
}

private inline fun <T> guarded(block: () -> T): Result<T> = runCatching(block)

private fun VirtualFile.text() = String(contentsToByteArray(), charset)

private fun JsonNode.color(name: String): ColorDto? = opt(name)?.takeIf { it.isObject }?.let {
    ColorDto(it.opt("r")?.floatValue() ?: 0f, it.opt("g")?.floatValue() ?: 0f, it.opt("b")?.floatValue() ?: 0f, it.opt("a")?.floatValue() ?: 0f)
}

object SceneReader : AssetReader {
    fun parse(text: String): SceneDto {
        val o = Json.parseObject(text)
        return SceneDto(
            id = o.opt("id")?.asLong(),
            name = o.opt("name")?.asText(),
            ambientLightEnabled = o.opt("ambientLightEnabled")?.asBoolean(),
            ambientLight = o.opt("ambientLight")?.takeIf { it.isObject }?.let {
                BaseLightDto(it.color("color"), it.opt("intensity")?.floatValue())
            },
            fogEnabled = o.opt("fogEnabled")?.asBoolean(),
            fog = o.opt("fog")?.takeIf { it.isObject }?.let {
                FogDto(it.color("color"), it.opt("density")?.floatValue(), it.opt("gradient")?.floatValue())
            },
            skyboxEnabled = o.opt("skyboxEnabled")?.asBoolean(),
            skyboxName = o.opt("skyboxName")?.asText(),
            ecs = o.opt("ecs"),
        )
    }

    fun readScene(file: VirtualFile): Result<SceneDto> = guarded { parse(file.text()) }

    override fun read(file: VirtualFile): AssetReadResult = readScene(file).fold(
        { AssetReadResult.Success(it.toValue().copy(source = file)) },
        { AssetReadResult.Failure(it.message ?: it.javaClass.simpleName) },
    )
}

object ProjectReader : AssetReader {
    const val SCENES_DIR = "scenes"

    fun sceneFiles(file: VirtualFile): List<VirtualFile> =
        file.parent?.findChild(SCENES_DIR)?.children
            ?.filter { !it.isDirectory && it.extension == "scene" }
            ?.sortedBy { it.name }
            ?: emptyList()

    override fun stamp(file: VirtualFile): Long =
        sceneFiles(file).fold(file.modificationStamp) { acc, f -> acc * 31 + f.modificationStamp } * 31 + ProjectAssets.stamp(file)

    override fun read(file: VirtualFile): AssetReadResult = guarded {
        val name = Json.parseObject(file.text()).opt("name")?.asText() ?: file.nameWithoutExtension
        val scenes = sceneFiles(file).map { it to SceneReader.readScene(it) }
        val entries = scenes.mapIndexed { i, (f, result) ->
            result.fold(
                { it.toValue(sceneLabel(it, i)).copy(source = f) },
                { DtoValue.Obj(listOf(DtoProperty("error", DtoValue.Scalar(it.message))), f.name) },
            )
        }
        val assets = ProjectAssets.read(file)
        val roots = scenes.flatMap { (_, result) -> result.getOrNull()?.let(ProjectAssets::sceneReferences) ?: emptySet() }.toSet()
        val used = ProjectAssets.usedAssets(assets, roots)
        val assetEntries = assets.map { a ->
            DtoValue.Obj(
                listOf(DtoProperty("type", DtoValue.Scalar(a.type)), DtoProperty("uuid", DtoValue.Scalar(a.uuid))),
                a.name,
                unused = a.name !in used,
                asset = true,
            )
        }
        DtoValue.Obj(
            listOf(
                DtoProperty("name", DtoValue.Scalar(name)),
                DtoProperty("scenes", DtoValue.Items(entries)),
                DtoProperty("assets", DtoValue.Items(assetEntries)),
            ),
        )
    }.fold(
        { AssetReadResult.Success(it) },
        { AssetReadResult.Failure(it.message ?: it.javaClass.simpleName) },
    )
}
