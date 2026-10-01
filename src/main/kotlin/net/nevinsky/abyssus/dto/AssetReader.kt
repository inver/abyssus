package net.nevinsky.abyssus.dto

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

private fun parseObject(text: String): JsonObject {
    val element = JsonParser.parseString(text)
    require(element.isJsonObject) { "expected a JSON object" }
    return element.asJsonObject
}

private fun JsonObject.opt(name: String) = get(name)?.takeIf { !it.isJsonNull }

private inline fun <T> guarded(block: () -> T): Result<T> = runCatching(block)

private fun VirtualFile.text() = String(contentsToByteArray(), charset)

private fun JsonObject.color(name: String): ColorDto? = opt(name)?.asJsonObject?.let {
    ColorDto(it.opt("r")?.asFloat ?: 0f, it.opt("g")?.asFloat ?: 0f, it.opt("b")?.asFloat ?: 0f, it.opt("a")?.asFloat ?: 0f)
}

object SceneReader : AssetReader {
    fun parse(text: String): SceneDto {
        val o = parseObject(text)
        return SceneDto(
            id = o.opt("id")?.asLong,
            name = o.opt("name")?.asString,
            ambientLightEnabled = o.opt("ambientLightEnabled")?.asBoolean,
            ambientLight = o.opt("ambientLight")?.asJsonObject?.let {
                BaseLightDto(it.color("color"), it.opt("intensity")?.asFloat)
            },
            fogEnabled = o.opt("fogEnabled")?.asBoolean,
            fog = o.opt("fog")?.asJsonObject?.let {
                FogDto(it.color("color"), it.opt("density")?.asFloat, it.opt("gradient")?.asFloat)
            },
            skyboxEnabled = o.opt("skyboxEnabled")?.asBoolean,
            skyboxName = o.opt("skyboxName")?.asString,
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
        sceneFiles(file).fold(file.modificationStamp) { acc, f -> acc * 31 + f.modificationStamp }

    override fun read(file: VirtualFile): AssetReadResult = guarded {
        val name = parseObject(file.text()).opt("name")?.asString ?: file.nameWithoutExtension
        val entries = sceneFiles(file).mapIndexed { i, f ->
            SceneReader.readScene(f).fold(
                { it.toValue(sceneLabel(it, i)).copy(source = f) },
                { DtoValue.Obj(listOf(DtoProperty("error", DtoValue.Scalar(it.message))), f.name) },
            )
        }
        DtoValue.Obj(listOf(DtoProperty("name", DtoValue.Scalar(name)), DtoProperty("scenes", DtoValue.Items(entries))))
    }.fold(
        { AssetReadResult.Success(it) },
        { AssetReadResult.Failure(it.message ?: it.javaClass.simpleName) },
    )
}
