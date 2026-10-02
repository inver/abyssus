/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.dto

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
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
            ProjectLayout.SCENE_EXTENSION -> SceneReader
            ProjectLayout.PROJECT_EXTENSION -> ProjectReader
            else -> null
        }
    }
}

private inline fun <T> guarded(block: () -> T): Result<T> = runCatchingKeepingCancellation(block)

/** The file's saved content (not unsaved editor text). */
fun VirtualFile.text() = String(contentsToByteArray(), charset)

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
    override fun stamp(file: VirtualFile): Long =
        ProjectLayout.sceneFiles(file).fold(file.modificationStamp) { acc, f -> acc * 31 + f.modificationStamp } * 31 + ProjectAssets.stamp(file)

    override fun read(file: VirtualFile): AssetReadResult = guarded {
        val name = Json.parseObject(file.text()).opt("name")?.asText() ?: file.nameWithoutExtension
        val scenes = ProjectLayout.sceneFiles(file).map { it to SceneReader.readScene(it) }
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

/** `Main Scene (6275127)`: the scene name followed by its id; the index stands in for a missing name. */
private fun sceneLabel(scene: SceneDto, index: Int): String {
    val name = scene.name?.takeIf { it.isNotBlank() } ?: AbyssusBundle.message("dtoListElementLabel", "scenes", index)
    return scene.id?.let { "$name ($it)" } ?: name
}
