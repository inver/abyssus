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

package net.nevinsky.abyssus.sceneview.skybox

import com.intellij.openapi.diagnostic.logger
import net.nevinsky.abyssus.sceneview.AssetLoader
import net.nevinsky.abyssus.sceneview.MetaBase
import net.nevinsky.abyssus.sceneview.MetaType
import net.nevinsky.abyssus.sceneview.ProjectAssetFiles
import java.io.File
import java.util.*

/** The `meta.json` of an `SKYBOX_HDR` asset; `additional` is read only for text values that may name the image. */
class HdrSkyMeta(
    version: Int,
    lastModified: Long,
    type: MetaType,
    additional: Map<String, Any?>?,
    uuid: UUID? = null,
) : MetaBase<Map<String, Any?>?>(version, lastModified, type, additional, uuid)

/** A decoded HDR sky waiting for its GPU build; [build] is created and advanced on the GL thread only. */
class PreparedHdrSky(val name: String, val file: File, val image: HdrImage) {
    internal var build: HdrEnvironmentBuild? = null
}

/**
 * `SKYBOX_HDR` assets: the `.hdr` is chosen and decoded off the GL thread, then the environment is built on the GPU
 * one step per frame ([HdrEnvironmentBuild]).
 */
class HdrSkyLoader : AssetLoader<PreparedHdrSky, HdrSky> {
    /**
     * Null for a missing folder or a folder that is not an `SKYBOX_HDR`; throws, with the reason, for a folder without
     * a `.hdr` and for an image that cannot be decoded.
     */
    override fun prepare(files: ProjectAssetFiles, name: String): PreparedHdrSky? {
        val asset = files.loadAsset(HdrSkyMeta::class.java, name) ?: return null
        if (asset.meta.type != MetaType.SKYBOX_HDR) return null
        val dir = asset.baseDir
        val named = asset.meta.additional.orEmpty().values.filterIsInstance<String>()
        val choice = HdrSkyFiles.choose(dir.list()?.toList().orEmpty(), named)
            ?: throw IllegalStateException("HDR sky '$name' has no .hdr file")
        choice.warning?.let { logger<HdrSkyLoader>().warn("HDR sky '$name': $it") }
        val file = files.file(dir, choice.file) ?: throw IllegalStateException("HDR sky '$name': cannot read '${choice.file}'")
        val image = try {
            RadianceDecoder.read(file)
        } catch (e: RadianceFormatException) {
            throw IllegalStateException("HDR sky '$name': '${choice.file}' ${e.message}", e)
        }
        return PreparedHdrSky(name, file, image)
    }

    override fun upload(prepared: PreparedHdrSky): Boolean =
        (prepared.build ?: HdrEnvironmentBuild(prepared.image).also { prepared.build = it }).step()

    override fun build(prepared: PreparedHdrSky): HdrSky {
        val environment = checkNotNull(prepared.build) { "HDR sky '${prepared.name}' was never uploaded" }.finish()
        prepared.build = null
        return HdrSky(environment)
    }

    /** Releases a half-done build; nothing to do before the first upload step (the decoded image is plain memory). */
    override fun discard(prepared: PreparedHdrSky) {
        prepared.build?.dispose()
        prepared.build = null
    }
}
