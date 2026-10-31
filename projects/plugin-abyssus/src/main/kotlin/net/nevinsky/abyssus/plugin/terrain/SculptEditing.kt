/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptState
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptTool
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.plugin.brush.resolveTerrainEntity
import java.io.File

/**
 * What a Scene view knows about sculpting the selected entity: why it cannot be done ([reason], null when it can), the
 * terrain's folder name, the project's `assets` folder for the Stamp images, and the [stroke] that does the sculpting.
 * Closing it drops a stroke in progress.
 */
class SculptSession(
    val reason: String?,
    val stroke: BrushStroke?,
    val assetsDir: File?,
    val images: StampImages,
    private val close: () -> Unit,
) {
    fun dispose() = close()
}

/** Project-owned workers that read a terrain's files for a view's Sculpt Terrain mode. */
@Service(Service.Level.PROJECT)
class SculptEditing(private val project: Project) {
    /**
     * Opens the sculpt session of [entity] in [scene] and calls [ready] on the UI thread with it, or with null when the
     * entity is not a terrain. [mode] and [tool] are read by the stroke on every press and drag.
     */
    fun open(
        scene: VirtualFile,
        entity: String,
        mode: () -> BrushMode?,
        tool: () -> SculptTool?,
        report: (String) -> Unit,
        ready: (SculptSession?) -> Unit,
    ) {
        val found = resolveTerrainEntity(project, scene, entity)
        val folder = found?.assetFolder(found.terrain.name)
        if (found == null || folder == null) {
            ready(null)
            return
        }
        val abss = found.abss
        val assets = found.assets
        val core = service<AbyssusPluginCtx>()
        val projectDir = File(abss.parent.path)
        // a pooled thread, not a coroutine: the resume of a coroutine fails under the IDE's own debug probes in the sandbox
        ApplicationManager.getApplication().executeOnPooledThread {
            val source = readTerrainFolder(folder, core.json, core.terrainRecipes, core.terrainSculpt)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || !scene.isValid || !folder.isValid) {
                    ready(null)
                    return@invokeLater
                }
                val reason = when {
                    source is TerrainSource.Unusable -> AbyssusBundle.message("sculptUnavailableUnreadable", source.reason)
                    (source as TerrainSource.Ready).sculpt is SculptState.Unsafe -> AbyssusBundle.message("sculptUnavailableUnsafe")
                    else -> null
                }
                if (reason != null) {
                    ready(SculptSession(reason, null, File(assets.path), StampImages(core.json), {}))
                    return@invokeLater
                }
                val command = SculptStrokeCommand(
                    project, folder, project.service<SculptDrafts>(), core.terrainSculpt, LocalAssetFileStore(projectDir),
                    { readTerrainFolder(folder, core.json, core.terrainRecipes, core.terrainSculpt) }, mode, tool, report,
                )
                ready(SculptSession(null, command, File(assets.path), StampImages(core.json), command::dispose))
            }
        }
    }
}
