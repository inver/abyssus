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

package net.nevinsky.abyssus.properties

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.projectView.describeNonAsset
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** What the panel shows. */
sealed interface PanelState {
    /** No asset to describe: [message], and under it [hint] when there is one. */
    data class Empty(val message: String, val hint: String?) : PanelState

    data class Details(val name: String, val meta: AssetMeta.Loaded, val faces: List<FaceCell>?) : PanelState
}

/** One face of a skybox: [file] is what `meta.json` names (or `null`), [image] its thumbnail, null when it cannot be shown. */
data class FaceCell(val face: String, val file: String, val image: BufferedImage?)

val SKYBOX_FACES = listOf("top", "bottom", "left", "right", "front", "back")

private const val SKYBOX = "SKYBOX"
private const val THUMBNAIL_WIDTH = 320
private const val THUMBNAIL_HEIGHT = 144

fun emptyState(node: Any?): PanelState.Empty {
    val hint = AbyssusBundle.message("propertiesHint")
    val (name, kind) = describeNonAsset(node) ?: return PanelState.Empty(AbyssusBundle.message("propertiesNothingSelected"), hint)
    return PanelState.Empty(AbyssusBundle.message("propertiesNothingToShow", name, kind), hint)
}

/** Reads the asset in [folder] for display: its Meta and, for a skybox, the face thumbnails. Safe off the EDT. */
fun readAssetState(folder: VirtualFile): PanelState {
    val meta = runReadAction { loadAssetMeta(folder) }
    return when (meta) {
        is AssetMeta.Failed -> PanelState.Empty(meta.message, null)
        is AssetMeta.Loaded -> PanelState.Details(folder.name, meta, if (meta.type == SKYBOX) faces(folder, meta) else null)
    }
}

private fun faces(folder: VirtualFile, meta: AssetMeta.Loaded): List<FaceCell> {
    val additional = meta.json.get("additional")
    return SKYBOX_FACES.map { face ->
        val file = additional?.get(face)?.takeIf { it.isTextual }?.asText()
        FaceCell(face, file ?: AbyssusBundle.message("dtoNullValue"), file?.let { thumbnail(folder, it) })
    }
}

private fun thumbnail(folder: VirtualFile, fileName: String): BufferedImage? = runCatchingKeepingCancellation {
    val file = folder.takeIf { it.isValid }?.findChild(fileName)?.takeIf { it.isValid && !it.isDirectory } ?: return@runCatchingKeepingCancellation null
    val source = ImageIO.read(ByteArrayInputStream(file.contentsToByteArray())) ?: return@runCatchingKeepingCancellation null
    scaled(source)
}.getOrNull()

private fun scaled(source: BufferedImage): BufferedImage {
    val ratio = minOf(THUMBNAIL_WIDTH.toDouble() / source.width, THUMBNAIL_HEIGHT.toDouble() / source.height, 1.0)
    val w = maxOf(1, (source.width * ratio).toInt())
    val h = maxOf(1, (source.height * ratio).toInt())
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    out.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        drawImage(source, 0, 0, w, h, null)
        dispose()
    }
    return out
}
