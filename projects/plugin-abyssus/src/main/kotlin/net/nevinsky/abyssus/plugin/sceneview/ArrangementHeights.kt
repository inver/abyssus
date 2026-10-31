/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.editor.document.DocumentParsing
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.pick.GroundHeights
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Where the ground is under the copies of a path arrangement when a scene is edited. With a Scene view of the scene
 * open, its drawn terrains answer. Without one (an edit from the Properties panel, say), the scene's terrains are read
 * with [TerrainLoader] off the AWT thread under a modal progress, and kept per terrain until its files change, so the
 * next edit does not read them again. A terrain that cannot be read is left out: copies over it take the line's height.
 */
@Service(Service.Level.PROJECT)
class ArrangementHeights(private val project: Project, private val json: JsonProcessor) {
    /** The platform's entry: resolves the application's JSON once. */
    constructor(project: Project) : this(project, service<AbyssusPluginCtx>().json)

    private class Stamped(val stamp: List<Long>, val data: TerrainData?)

    private val cache = ConcurrentHashMap<String, Stamped>()

    /**
     * The ground of the scene [file]; null when the user cancelled the read. Call on the AWT thread: a read that is
     * not already cached shows a progress dialog.
     */
    fun forScene(file: VirtualFile): GroundHeight? {
        openView(file)?.let { return it }
        val projectDir = ProjectLayout.projectDirFor(file) ?: return GroundHeights(emptyList())
        val placements = terrainPlacements(file)
        if (placements.isEmpty()) return GroundHeights(emptyList())
        val targets = try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                ThrowableComputable<List<TerrainTarget>, RuntimeException> { load(projectDir, placements) },
                AbyssusBundle.message("arrangementReadingTerrain"), true, project,
            )
        } catch (e: ProcessCanceledException) {
            return null
        }
        return GroundHeights(targets)
    }

    private fun openView(file: VirtualFile): GroundHeight? =
        FileEditorManager.getInstance(project).getEditors(file).filterIsInstance<SceneFileEditor>()
            .firstNotNullOfOrNull { it.sceneView?.groundHeights() }

    private class Placement(val entityId: String, val asset: String, val world: com.badlogic.gdx.math.Matrix4)

    private fun terrainPlacements(file: VirtualFile): List<Placement> {
        val scene = runCatchingKeepingCancellation { DocumentParsing(json, org.slf4j.helpers.NOPLogger.NOP_LOGGER).parse(textOf(file)) }.getOrNull() ?: return emptyList()
        return sceneContentOf(scene).terrains.map { Placement(it.entityId, it.assetName, it.transform.toMatrix()) }
    }

    private fun load(projectDir: File, placements: List<Placement>): List<TerrainTarget> {
        val files = FileLoader(projectDir)
        val metas = AssetMetaLoader(json, files)
        val loader = TerrainLoader(files, metas)
        return placements.mapNotNull { placement ->
            val data = runCatchingKeepingCancellation { dataOf(projectDir, placement.asset, metas, loader) }.getOrNull()
            data?.let { TerrainTarget(placement.entityId, it, placement.world) }
        }
    }

    private fun dataOf(projectDir: File, name: String, metas: AssetMetaLoader, loader: TerrainLoader): TerrainData? {
        val meta = metas.loadBaseMeta(name) ?: return null
        val folder = File(File(projectDir, ASSETS_DIR), name)
        val dataFile = File(folder, meta.typedAdditional<TerrainMeta>().terrainFile ?: return null)
        val stamp = listOf(File(folder, META_FILE).lastModified(), dataFile.lastModified(), dataFile.length())
        val key = dataFile.path
        cache[key]?.takeIf { it.stamp == stamp }?.let { return it.data }
        val data = loader.loadPrepared(meta).staged.data
        cache[key] = Stamped(stamp, data)
        return data
    }

    companion object {
        fun of(project: Project): ArrangementHeights = project.service()
    }
}
