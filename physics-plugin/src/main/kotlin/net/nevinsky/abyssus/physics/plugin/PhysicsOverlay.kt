/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.plugin

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.core.io.FileLoader
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.sceneview.LineSink
import net.nevinsky.abyssus.sceneview.OverlayView
import net.nevinsky.abyssus.sceneview.SceneContent
import net.nevinsky.abyssus.sceneview.SceneOverlay
import net.nevinsky.abyssus.sceneview.SceneOverlayProvider
import java.io.File

/** Gives every Scene view a [PhysicsOverlay]. */
class PhysicsOverlayProvider : SceneOverlayProvider {
    override fun create(project: Project, file: VirtualFile): SceneOverlay = PhysicsOverlay()
}

/**
 * Draws the scene's colliders and constraints in one Scene view while "Show Physics" is on (off when the view opens).
 * Others are depth-tested; the selected entity's are drawn brighter over everything, so a collider inside its model
 * stays visible. Segments are recomputed only when the shown content, the scene JSON or the selection changes.
 */
class PhysicsOverlay : SceneOverlay {
    @Volatile
    var shown = false

    private var projectDir: File? = null
    private var metas: AssetMetaLoader? = null
    private val geometry = PhysicsOverlayGeometry { name -> metas?.terrainSize(name) }

    private var lastContent: SceneContent? = null
    private var lastEcs: JsonNode? = null
    private var lastSelection: String? = null
    private var segments: List<OverlaySegment> = emptyList()

    override fun draw(view: OverlayView, lines: LineSink) {
        if (!shown) return
        val dir = view.projectDir
        if (dir != null && projectDir != dir.absoluteFile) useProject(dir)
        if (view.content !== lastContent || view.ecs !== lastEcs || view.selectedId != lastSelection) {
            segments = geometry.segments(view.content, view.ecs, view.selectedId)
            lastContent = view.content
            lastEcs = view.ecs
            lastSelection = view.selectedId
        }
        for (s in segments) if (s.selected == view.onTop) lines.line(s.from, s.to, s.color)
    }

    override fun actions(): List<AnAction> = listOf(object : ToggleAction(
        AbyssusPhysicsBundle.message("showPhysics"), AbyssusPhysicsBundle.message("showPhysicsTooltip"), AllIcons.Actions.Show,
    ) {
        override fun isSelected(e: AnActionEvent) = shown
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            shown = state
        }
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    })

    /** For tests: the project folder terrain sizes are read from. */
    internal fun useProject(dir: File) {
        projectDir = dir.absoluteFile
        metas = AssetMetaLoader(JsonProcessor(), FileLoader(dir.absoluteFile))
    }
}

/** The edge length of the terrain asset [name] from its meta; null without a terrain meta. */
internal fun AssetMetaLoader.terrainSize(name: String): Float? =
    loadBaseMeta(name)?.additional.let { it as? TerrainMeta }?.size?.toFloat()
