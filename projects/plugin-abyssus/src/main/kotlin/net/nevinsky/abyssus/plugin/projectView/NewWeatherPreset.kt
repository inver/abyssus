/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.editor.document.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.terrain.checkFolderName
import net.nevinsky.abyssus.lib.core.editor.terrain.uniqueAssetUuid
import net.nevinsky.abyssus.lib.core.editor.weather.WeatherPresetDraft
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.assetfiles.*
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf
import java.io.File
import java.util.UUID

class StagedWeatherPreset(val name: String, val uuid: String, val transaction: AssetTransaction)

/** Reads current editor metadata and stages immutable bytes; writes belong to AssetFileCommand. EDT. */
class NewWeatherPresetFactory(
    private val processor: JsonProcessor,
    private val draft: WeatherPresetDraft = WeatherPresetDraft(processor, EditorBundle),
    private val clock: () -> Long = System::currentTimeMillis,
    private val randomUuid: () -> UUID = UUID::randomUUID,
) {
    private val json = SceneJson()
    private val reader = AssetMetaReader(processor)

    private fun texts(abss: VirtualFile, sky: VirtualFile): Pair<String, String> {
        AbyssusDocumentFormat().requireSupported(json.parseObject(textOf(abss)), DocumentKind.PROJECT)
        val assets = abss.parent.findChild("assets")
        require(assets != null && sky.name == "meta.json" && sky.parent.parent == assets) {
            AbyssusBundle.message("weatherSourceUnavailable")
        }
        val skyText = textOf(sky)
        val skyMeta = reader.read(json.parseObject(skyText))
        require(skyMeta.type == net.nevinsky.abyssus.lib.core.assets.MetaType.SKYBOX_PROCEDURAL) {
            EditorBundle.message("weatherSourceSky")
        }
        val reference = skyMeta.json["additional"]?.get("clouds")
        require(reference != null && reference.isTextual && reference.textValue().isNotBlank()) {
            EditorBundle.message("weatherSourceReference")
        }
        val matches = ProjectLayout.assetFolders(abss).mapNotNull { folder ->
            val file = folder.findChild("meta.json") ?: return@mapNotNull null
            runCatchingKeepingCancellation {
                val text = textOf(file)
                val meta = reader.read(json.parseObject(text))
                text.takeIf { meta.json["uuid"]?.asText() == reference.textValue() }
            }.getOrNull()
        }
        return skyText to requireNotNull(matches.singleOrNull()) { AbyssusBundle.message("weatherSourceUnavailable") }
    }

    fun validateSource(abss: VirtualFile, sky: VirtualFile) = ReadAction.compute<Unit, RuntimeException> {
        val (skyText, sourceText) = texts(abss, sky)
        draft.create(skyText, sourceText, UUID(0, 0), 0)
        Unit
    }

    fun stage(abss: VirtualFile, sky: VirtualFile, rawName: String): StagedWeatherPreset = ReadAction.compute<StagedWeatherPreset, RuntimeException> {
        val (skyText, sourceText) = texts(abss, sky)
        val projectDir = File(abss.parent.path)
        val assetsDir = File(projectDir, "assets")
        val name = rawName.trim()
        checkFolderName(assetsDir, name)?.let { throw IllegalArgumentException(AbyssusBundle.message("newTerrainNameError.$it")) }
        val used = ProjectLayout.assetFolders(abss).mapNotNull { folder ->
            runCatchingKeepingCancellation {
                folder.findChild("meta.json")?.let { reader.read(json.parseObject(textOf(it))).json["uuid"]?.asText() }
            }.getOrNull()
        }.toSet()
        var uuid = uniqueAssetUuid(processor, assetsDir, randomUuid)
        while (uuid.toString() in used) uuid = uniqueAssetUuid(processor, assetsDir, randomUuid)
        val text = draft.create(skyText, sourceText, uuid, clock())
        val base = "assets/$name"
        StagedWeatherPreset(name, uuid.toString(), AssetTransaction(
            AbyssusBundle.message("commandNewWeatherPreset"),
            listOf(FileChange("$base/meta.json", FileSnapshot.Absent, FileSnapshot.Bytes(text.toByteArray(Charsets.UTF_8)))),
            createdDirs = (if (assetsDir.isDirectory) emptyList() else listOf("assets")) + base,
            guard = { AssetReferenceGuard(projectDir).blocker(name, uuid.toString()) },
        ))
    }
}
