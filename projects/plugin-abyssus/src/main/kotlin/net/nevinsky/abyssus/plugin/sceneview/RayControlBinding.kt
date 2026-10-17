/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import net.nevinsky.abyssus.lib.core.editor.ray.RayModeSnapshot
import net.nevinsky.abyssus.lib.core.editor.ray.RayViewFeed
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

/**
 * One view's Ray Tracing binding: the [RayViewFeed] of its [RayIntegration], the mode the Abyssus Properties switch
 * shows, and the listeners told when that changed. Without an integration the view stays raster only. AWT thread.
 */
internal class RayControlBinding(private val renderer: SceneRenderer) {
    /** The feed of the installed integration, or null while the view is raster only. */
    var feed: RayViewFeed? = null
        private set
    private var shownRay: RayModeSnapshot? = null
    private var shownFoliage = false
    private val listeners = mutableListOf<() -> Unit>()

    /** The current Ray Tracing mode of this view (Off until it is switched on from Abyssus Properties). */
    val mode: RayModeSnapshot? get() = feed?.runtime?.mode?.snapshot

    /** The only switch for Ray Tracing: the Abyssus Properties panel flips it. The Scene View toolbar has no ray control. */
    val control: RayControl? get() = if (feed == null) null else panelControl

    private val panelControl = object : RayControl {
        override val mode: RayModeSnapshot? get() = this@RayControlBinding.mode

        /** The ray-traced image traces this scene's models and terrains only, so a scene that shows foliage says so. */
        override val foliageNote: String? get() =
            if (renderer.content.foliages.isEmpty()) null else AbyssusBundle.message("propertiesSceneRayFoliage")

        override fun setRequested(enabled: Boolean) {
            feed?.runtime?.setRequested(enabled)
            refresh()
        }

        override fun retry() {
            feed?.runtime?.retry()
            refresh()
        }

        override fun addListener(parent: Disposable, listener: () -> Unit) {
            listeners += listener
            Disposer.register(parent) { listeners -= listener }
        }
    }

    /**
     * Binds this view's Ray Tracing mode to [integration], replacing any earlier binding. Nothing native is created
     * until ray tracing is switched on from Abyssus Properties.
     */
    fun install(integration: RayIntegration) {
        feed?.close()
        val fresh = integration.newFeed("scene-view-${NEXT_VIEW.incrementAndGet()}", renderer.assets)
        fresh.runtime.mode.addListener { SwingUtilities.invokeLater { if (feed === fresh) refresh() } }
        feed = fresh
        renderer.rayFrameProvider = { context -> fresh.frame(context) }
        shownRay = null
        refresh()
    }

    /** Tells listeners (the Properties panel's switch) when the mode changed, or the foliage note with it. Cheap when nothing did. */
    fun refresh() {
        val snapshot = feed?.runtime?.mode?.snapshot
        val foliage = renderer.content.foliages.isNotEmpty()
        if (snapshot == shownRay && foliage == shownFoliage) return
        shownRay = snapshot
        shownFoliage = foliage
        listeners.toList().forEach { it() }
    }

    /** Releases the feed when the view is disposed. */
    fun close() {
        feed?.close()
        feed = null
        renderer.rayFrameProvider = null
    }

    private companion object {
        val NEXT_VIEW = AtomicLong()
    }
}
