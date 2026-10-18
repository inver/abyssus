/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.plugin.AssetServices
import net.nevinsky.abyssus.plugin.dto.SceneReader
import net.nevinsky.abyssus.plugin.foliage.FoliageDrafts
import net.nevinsky.abyssus.plugin.sceneview.SceneParamsSource
import net.nevinsky.abyssus.plugin.sceneview.SceneRenderer
import net.nevinsky.abyssus.plugin.sceneview.ViewAssets
import org.slf4j.Logger

/** A read-only picture drawn behind a UI preview. GL thread only, inside the canvas's Gdx context. */
interface UiBackdrop {
    fun render(width: Int, height: Int, delta: Float)
    fun dispose()
}

/**
 * A native scene as a preview backdrop: the scene view's own renderer, fed the scene's current editor text (unsaved text
 * included, native admission applied), looking through its main camera. It draws models, terrain, sky and lights as the
 * Scene view does and runs no simulation, no Play and no game module. Created and disposed on the GL thread.
 */
class SceneBackdrop(
    sceneFile: VirtualFile,
    assets: AssetServices,
    drafts: FoliageDrafts,
    scenes: SceneReader,
    log: Logger,
) : UiBackdrop {
    private val renderer = SceneRenderer(ViewAssets(assets.loading), assets.sceneShaders, drafts, log = log)
    private val orbit: OrbitCamera

    init {
        val params = SceneParamsSource.editorText(scenes).read(sceneFile)
        renderer.params = params
        orbit = OrbitCamera(params.camera)
        try { renderer.create() } catch (e: Throwable) { renderer.dispose(); throw e }
    }

    override fun render(width: Int, height: Int, delta: Float) = renderer.render(width, height, orbit, delta)

    override fun dispose() = renderer.dispose()
}
