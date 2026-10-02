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

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.scene.SceneDto

sealed interface AssetReadResult {
    /** [root] is the bound DTO: a [SceneDto] or a [ProjectDto]. */
    data class Success(val root: Any) : AssetReadResult
    data class Failure(val message: String) : AssetReadResult
}

/** Reads one asset file into its DTO. Never writes and never throws. */
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

object SceneReader : AssetReader {
    fun parse(text: String): SceneDto = SceneJson.bind(SceneJson.parseObject(text), SceneDto::class.java)

    fun readScene(file: VirtualFile): Result<SceneDto> = guarded { parse(file.text()).copy(file = file) }

    override fun read(file: VirtualFile): AssetReadResult = readScene(file).fold(
        { AssetReadResult.Success(it) },
        { AssetReadResult.Failure(it.message ?: it.javaClass.simpleName) },
    )
}

object ProjectReader : AssetReader {
    override fun stamp(file: VirtualFile): Long =
        ProjectLayout.sceneFiles(file).fold(file.modificationStamp) { acc, f -> acc * 31 + f.modificationStamp } * 31 + ProjectAssets.stamp(file)

    override fun read(file: VirtualFile): AssetReadResult = guarded {
        val name = SceneJson.parseObject(file.text()).opt("name")?.asText() ?: file.nameWithoutExtension
        val scenes = ProjectLayout.sceneFiles(file).map { it to SceneReader.readScene(it) }
        val assets = ProjectAssets.read(file)
        val roots = scenes.flatMap { (_, result) -> result.getOrNull()?.let(ProjectAssets::sceneReferences) ?: emptySet() }.toSet()
        val used = ProjectAssets.usedAssets(assets, roots)
        ProjectDto(
            name,
            scenes.map { (f, result) -> result.getOrElse { SceneError(f, it.message) } },
            assets.map { it.copy(unused = it.name !in used) },
        )
    }.fold(
        { AssetReadResult.Success(it) },
        { AssetReadResult.Failure(it.message ?: it.javaClass.simpleName) },
    )
}
