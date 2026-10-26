/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.foliage

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneDocument
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.dto.textOf

/** Project-owned workers for loading and committing a view's foliage paint session. */
@Service(Service.Level.PROJECT)
class FoliageEditing(private val project: Project, private val scope: CoroutineScope) {
    fun open(
        scene: VirtualFile, entity: String, mode: () -> BrushMode?, report: (String) -> Unit,
        ready: (FoliagePaintSession?) -> Unit
    ) {
        val root = SceneDocumentCache.of(project).read(scene)?.root
        val document = root?.let(::SceneDocument)
        val terrain = document?.renderAsset(entity)
            ?.takeIf { it.isTerrain() }
        val name = document?.components(entity)?.get("FoliageComponent")?.get("assetName")?.textValue()
        val abss = ProjectLayout.abssFor(scene)
        val folder = abss?.parent?.findChild("assets")?.findChild(name ?: "")
        val metadata = folder?.findChild("meta.json")
        if (terrain == null || name == null || folder == null || metadata == null) {
            ready(null); return
        }
        val text = textOf(metadata)
        val projectText = textOf(abss)
        val projectDir = java.io.File(abss.parent.path)
        val documents = service<AbyssusPluginCtx>().documents
        val json = documents.json
        val parsing = documents.parsing
        scope.launch {
            val source = withContext(Dispatchers.IO) {
                runCatchingKeepingCancellation {
                    parsing.requireSupported(projectText, DocumentKind.PROJECT)
                    if (parsing.assetFields(text).type != net.nevinsky.abyssus.lib.core.assets.MetaType.FOLIAGE) return@runCatchingKeepingCancellation null
                    readFoliageSource(name, projectDir, json, text, parsing.foliageMeta(text)) as? FoliageSource.Ready
                }.getOrNull()
            }
            withContext(Dispatchers.EDT) {
                if (project.isDisposed || !scene.isValid || !folder.isValid) {
                    ready(null); return@withContext
                }
                if (source == null || source.settings.terrain != terrain.name || source.settings.layers.isEmpty() ||
                    source.read.problems.isNotEmpty() || textOf(metadata) != text || textOf(abss) != projectText
                ) {
                    ready(null); return@withContext
                }
                val stroke = FoliageStrokeCommand(
                    project,
                    scene,
                    folder,
                    source,
                    project.service<FoliageDrafts>(),
                    documents,
                    project.service<FoliageUndoCache>(),
                    mode,
                    background = { task -> scope.launch(Dispatchers.Default) { task.run() } },
                    ui = { task -> scope.launch(Dispatchers.EDT) { task.run() } },
                    report = report
                )
                ready(FoliagePaintSession(source.settings.layers.map { it.id }, stroke, stroke::dispose))
            }
        }
    }
}
