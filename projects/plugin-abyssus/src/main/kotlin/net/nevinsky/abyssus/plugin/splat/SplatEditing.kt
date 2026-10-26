/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.editor.document.SceneDocument
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatStateReader
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import java.io.File

/** Project-owned entry point that opens the Paint Texture session of a terrain entity for a view. */
@Service(Service.Level.PROJECT)
class SplatEditing(private val project: Project) {
    /**
     * Opens the splat session of [entity] in [scene] and calls [ready] on the UI thread with it, or with null when the
     * entity is not a terrain. [mode] is read by the stroke on every press and drag.
     */
    fun open(scene: VirtualFile, entity: String, mode: () -> BrushMode?, report: (String) -> Unit, ready: (SplatSession?) -> Unit) {
        val root = SceneDocumentCache.of(project).read(scene)?.root
        val terrain = root?.let(::SceneDocument)?.renderAsset(entity)?.takeIf { it.type == MetaType.TERRAIN.name }
        val abss = ProjectLayout.abssFor(scene)
        val assets = abss?.parent?.findChild("assets")
        val folder = assets?.findChild(terrain?.name ?: "")
        if (terrain == null || abss == null || folder == null) {
            ready(null)
            return
        }
        val core = service<AbyssusPluginCtx>()
        val projectDir = File(abss.parent.path)
        val store = LocalAssetFileStore(projectDir)
        val cache = SplatStateCache(SplatStateReader(core.json, ImageIoSplatCodec()), File(assets.path), folder)
        val command = SplatStrokeCommand(
            project, folder, project.service<SplatDrafts>(), SplatTransactions(store), store, cache::current, mode,
            background = { ApplicationManager.getApplication().executeOnPooledThread(it) },
            ui = { ApplicationManager.getApplication().invokeLater(it) },
            report = report,
        )
        ready(SplatSession(project, folder, projectDir, core.json, store, core.assetEditor, command, command::dispose, cache))
    }
}
