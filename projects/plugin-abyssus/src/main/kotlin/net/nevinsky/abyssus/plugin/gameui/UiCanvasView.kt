/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.ui.UiBuiltResources
import net.nevinsky.abyssus.lib.core.assets.ui.UiBundledFont
import net.nevinsky.abyssus.lib.core.assets.ui.UiPreparedResources
import net.nevinsky.abyssus.lib.core.assets.ui.UiResourcePreparer
import net.nevinsky.abyssus.lib.core.assets.ui.UiResourceResolver
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutResult
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.render.UiEditOverlay
import net.nevinsky.abyssus.lib.core.ui.render.UiGlKit
import net.nevinsky.abyssus.lib.core.ui.render.UiPresenter
import net.nevinsky.abyssus.lib.core.ui.state.UiBindingSession
import net.nevinsky.abyssus.lib.core.ui.state.UiCollectionSource
import net.nevinsky.abyssus.lib.core.ui.state.UiScreenRef
import net.nevinsky.abyssus.lib.core.ui.state.UiWriteResult
import net.nevinsky.abyssus.plugin.sceneview.CanvasOverlayHost
import net.nevinsky.abyssus.plugin.sceneview.GdxContext
import net.nevinsky.abyssus.plugin.sceneview.GlCanvasHooks
import net.nevinsky.abyssus.plugin.sceneview.GlCanvasHost
import net.nevinsky.abyssus.plugin.sceneview.GlFrameTimer
import net.nevinsky.abyssus.plugin.sceneview.GdxFrame
import net.nevinsky.abyssus.plugin.sceneview.GdxRuntime
import net.nevinsky.abyssus.plugin.sceneview.GuardedGLCanvas
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.JPanel

/**
 * The canvas of the UI editor: a GL surface that draws the asset through the shared [UiPresenter], so layout, text
 * metrics and styling are exactly what a game shows. In edit mode it adds selection and hover frames and turns pointer
 * input into the controller's drag gestures; in preview mode it hands input to the presenter. GL work runs only inside
 * [GdxRuntime.withContext] on the AWT thread and only while the surface is safely on screen ([GuardedGLCanvas.glSafe]).
 * The presenter is rebuilt when the document changes, never mutated; the GL kit, default font and context live as long as
 * the canvas.
 */
class UiCanvasView(
    private val controller: UiCanvasModel,
    private val assetName: String,
    private val assetDir: File,
    private val json: JsonProcessor,
    private val source: UiCollectionSource,
    /** Makes the read-only scene drawn behind a preview, on the GL thread; null (or a throw) draws none. */
    private val backdropFactory: (String) -> UiBackdrop? = { null },
) : JPanel(BorderLayout()), Disposable {
    private val glHost = GlCanvasHost(object : GlCanvasHooks {
        override fun create() {
            kit = UiGlKit()
            defaultFont = bundled.acquire()
        }

        override fun paint(canvas: GuardedGLCanvas, ctx: GdxContext, frame: GdxFrame) = GdxRuntime.withContext(ctx) { drawFrame() }

        override fun release() = releaseGl()

        override fun abandoned() {
            presenter = null; resources = null; kit = null; defaultFont = null; buffer = null; backdrop = null; backdropName = null
            shownState = null
            abandoned = true
        }
    }, "Failed to release UI editor GL resources")
    private val frame get() = glHost.frame
    private var kit: UiGlKit? = null
    private val bundled = UiBundledFont()
    private var defaultFont: BitmapFont? = null
    private var presenter: UiPresenter? = null
    private var resources: UiBuiltResources? = null
    private var shownState: UiEditorState.Editable? = null
    private var shownMode: UiEditorMode? = null
    private var shownScreen: String? = null
    private var abandoned = false
    private var closed = false
    private var shownRevision = -1L
    private var shownPreview: net.nevinsky.abyssus.lib.core.editor.ui.UiPreviewState? = null
    private var shownGeneration = -1
    private var buffer: FrameBuffer? = null
    private var backdrop: UiBackdrop? = null
    private var backdropName: String? = null
    private val fit = PreviewFit()

    /** Told (on the AWT thread) why the chosen backdrop could not be shown, or null when it can. */
    var onBackdropProblem: ((String?) -> Unit)? = null

    /** True once the GL objects have been released; for tests. */
    @Volatile
    internal var released = false
        private set
    private val overlay = UiEditOverlay()
    private val input = UiCanvasInput(this, controller, { presenter }, ::layoutOf, overlay, fit) { scale() }

    /** What the last preparation reported about the asset's image and font files; for diagnostics. */
    @Volatile
    var resourceReport: UiPreparedResources? = null
        private set

    private fun newCanvas(): GuardedGLCanvas = glHost.newCanvas(glData())

    private var canvas: GuardedGLCanvas = newCanvas()
    private fun newHost(): CanvasOverlayHost = CanvasOverlayHost(canvas) {
        try {
            glHost.capabilities?.let { GL.setCapabilities(it) }
            canvas.captureFrame { glHost.capabilities?.let { GL.setCapabilities(it) } }
        } catch (e: Exception) {
            thisLogger().warn("Failed to capture the UI canvas beneath an IDE overlay", e)
            null
        }
    }
    private var host: CanvasOverlayHost = newHost()

    private val timer: GlFrameTimer = GlFrameTimer({ canvas }) { e ->
        thisLogger().warn("UI render failed, stopping the canvas", e)
        timer.stop()
    }

    init {
        isFocusable = true
        add(host, BorderLayout.CENTER)
        input.attach(canvas)
        addKeyListener(input.keyListener())
        controller.layoutProvider = ::layoutOf
        controller.addListener { repaint() }
    }

    /** The layout the canvas currently shows for [screenId], or null before the first frame. */
    fun layoutOf(screenId: String): UiLayoutResult? = presenter?.layout(UiScreenRef(assetName, screenId))

    // ---- frame ----

    private fun drawFrame() {
        syncPresenter()
        val gl = Gdx.gl
        gl.glViewport(0, 0, frame.width, frame.height)
        gl.glClearColor(0.11f, 0.12f, 0.14f, 1f)
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val p = presenter ?: return
        val dpi = scale()
        if (controller.mode == UiEditorMode.PREVIEW) { drawPreview(p); controller.reportZoom(fit.scale / dpi); return }
        releasePreviewBuffers()
        overlay.selectedId = controller.selectedWidgetId
        p.overlay = overlay
        p.resize(frame.width, frame.height)
        p.render(frame.deltaSeconds)
        // the screen's own fit into the panel: reference units to framebuffer pixels
        controller.screenId?.let(::layoutOf)?.fit?.let { controller.reportZoom(it.scale / dpi) }
    }

    /**
     * The preview is laid out for its own size, whatever the panel is: backdrop and UI are drawn into an offscreen buffer of
     * exactly that size, which is then shown fitted into the panel.
     */
    private fun drawPreview(p: UiPresenter) {
        val size = controller.previewViewport
        val gl = Gdx.gl
        val target = buffer?.takeIf { it.width == size.width && it.height == size.height }
            ?: run { buffer?.dispose(); FrameBuffer(Pixmap.Format.RGBA8888, size.width, size.height, true).also { buffer = it } }
        p.overlay = null
        p.publish(controller.previewState?.snapshot() ?: net.nevinsky.abyssus.lib.core.ui.state.UiSnapshot())
        target.begin()
        gl.glClearColor(0.05f, 0.06f, 0.08f, 1f)
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        syncBackdrop()?.let { b ->
            try { b.render(size.width, size.height, frame.deltaSeconds) } catch (e: Throwable) { dropBackdrop(e) }
            gl.glDisable(GL20.GL_DEPTH_TEST); gl.glDisable(GL20.GL_CULL_FACE); gl.glDisable(GL20.GL_SCISSOR_TEST)
        }
        p.resize(size.width, size.height)
        p.render(frame.deltaSeconds)
        target.end(0, 0, frame.width, frame.height)
        // fit the offscreen picture into the panel, centred, without stretching
        val scale = minOf(frame.width.toFloat() / size.width, frame.height.toFloat() / size.height)
        fit.scale = scale
        fit.offsetX = (frame.width - size.width * scale) / 2f
        fit.offsetY = (frame.height - size.height * scale) / 2f
        val kit = kit ?: return
        gl.glViewport(0, 0, frame.width, frame.height)
        val region = TextureRegion(target.colorBufferTexture).also { it.flip(false, true) }
        kit.batch.projectionMatrix.setToOrtho2D(0f, 0f, frame.width.toFloat(), frame.height.toFloat())
        kit.batch.begin()
        kit.batch.draw(region, fit.offsetX, frame.height - fit.offsetY - size.height * scale, size.width * scale, size.height * scale)
        kit.batch.end()
    }

    /** The backdrop for the chosen scene, made on first use; a failure is reported once and draws none. GL thread. */
    private fun syncBackdrop(): UiBackdrop? {
        val wanted = controller.backdropScene
        if (wanted != backdropName) {
            backdrop?.let { runCatchingKeepingCancellation { it.dispose() } }
            backdrop = null; backdropName = wanted
            onBackdropProblem?.invoke(null)
            if (wanted != null) backdrop = try { backdropFactory(wanted) } catch (e: Throwable) { dropBackdrop(e); null }
        }
        return backdrop
    }

    private fun dropBackdrop(e: Throwable) {
        thisLogger().warn("The UI preview backdrop failed", e)
        runCatchingKeepingCancellation { backdrop?.dispose() }
        backdrop = null
        onBackdropProblem?.invoke(e.message ?: e.javaClass.simpleName)
    }

    private fun releasePreviewBuffers() {
        buffer?.dispose(); buffer = null
        backdrop?.let { runCatchingKeepingCancellation { it.dispose() } }; backdrop = null; backdropName = null
    }

    /** Builds a presenter when the document or mode changed and repoints it when only the screen did. GL thread. */
    private fun syncPresenter() {
        val state = controller.state as? UiEditorState.Editable
        if (state == null) { releasePresenter(); shownState = null; return }
        val screen = controller.screenId ?: return
        val preview = controller.previewState
        if (state !== shownState || controller.mode != shownMode || controller.resourceRevision != shownRevision || preview !== shownPreview) {
            rebuild(state.collection, controller.mode, preview)
            shownState = state; shownMode = controller.mode; shownScreen = null
            shownRevision = controller.resourceRevision; shownPreview = preview; shownGeneration = preview?.generation ?: -1
        }
        // a reset sends the preview back to the screen it started on
        if (preview != null && preview.generation != shownGeneration) {
            shownGeneration = preview.generation
            preview.startScreen?.let { presenter?.display(it, assetName); shownScreen = it }
        }
        if (screen != shownScreen) {
            presenter?.display(preview?.startScreen?.takeIf { shownScreen == null } ?: screen, assetName)
            shownScreen = presenter?.navigator?.layers()?.lastOrNull()?.ref?.screenId ?: screen
        }
    }

    private fun rebuild(collection: UiCollection, mode: UiEditorMode, preview: net.nevinsky.abyssus.lib.core.editor.ui.UiPreviewState?) {
        releasePresenter()
        val kit = kit ?: return
        val font = defaultFont ?: return
        val prepared = UiResourcePreparer(UiResourceResolver(assetDir)).prepare(collection)
        resourceReport = prepared
        val built = UiBuiltResources.Builder().build(prepared)
        resources = built
        // edit mode never lets a control change anything; preview changes only its own samples and logs its actions
        val writer = if (mode == UiEditorMode.PREVIEW && preview != null) preview.writer() else net.nevinsky.abyssus.lib.core.ui.state.UiValueWriter { UiWriteResult.Rejected("editing") }
        val sink = if (mode == UiEditorMode.PREVIEW && preview != null) preview.actionSink() else net.nevinsky.abyssus.lib.core.ui.state.UiActionSink { true }
        presenter = UiPresenter(assetName, collection, source, UiBindingSession(collection, writer), sink, built.forRenderer(font), json, showEntry = false, kit = kit)
    }

    private fun releasePresenter() {
        presenter?.dispose(); presenter = null
        resources?.dispose(); resources = null
    }

    private fun releaseGl() {
        releasePreviewBuffers()
        releasePresenter()
        defaultFont?.let { bundled.release() }; defaultFont = null
        kit?.dispose(); kit = null
        shownState = null
        released = true
    }

    // ---- pointer input (AWT pixels scaled to framebuffer pixels) ----

    private fun scale(): Float = if (canvas.width > 0) canvas.framebufferWidth.toFloat() / canvas.width else 1f

    /** How the preview picture sits in the panel: its scale and the offsets (framebuffer pixels); for tests. */
    internal fun previewSnapshotScale(): Triple<Float, Float, Float> = Triple(fit.scale, fit.offsetX, fit.offsetY)

    /** The frame as displayed, for tests: read back after the canvas has drawn at least once. */
    internal fun snapshot(): java.awt.image.BufferedImage? = canvas.captureFrame { glHost.capabilities?.let { GL.setCapabilities(it) } }

    // ---- lifecycle ----

    /** A canvas that gave up its context while hidden keeps a native surface that is no longer sized with the component. */
    private fun replaceAbandonedCanvas() {
        if (!abandoned) return
        abandoned = false
        remove(host)
        canvas = newCanvas()
        input.attach(canvas)
        host = newHost()
        add(host, BorderLayout.CENTER)
    }

    override fun addNotify() {
        replaceAbandonedCanvas()
        super.addNotify()
        timer.start()
    }

    override fun removeNotify() {
        timer.stop()
        super.removeNotify()
    }

    override fun dispose() {
        if (closed) return
        closed = true
        timer.stop()
        host.stop()
        controller.layoutProvider = { null }
        canvas.disposeCanvas() // releases GL resources through disposeGL while the context is still current
    }

    private companion object {

        fun glData() = GLData().apply {
            majorVersion = 3
            minorVersion = 2
            profile = GLData.Profile.CORE
            forwardCompatible = true
            depthSize = 24
            swapInterval = 0 // the Swing timer paces frames; a vsync-blocked swap would stall the IDE thread
        }
    }
}

/** libGDX key codes for the keys the UI uses; others map to 0 (unused). */
internal fun toGdxKey(awt: Int): Int = when (awt) {
    KeyEvent.VK_TAB -> com.badlogic.gdx.Input.Keys.TAB
    KeyEvent.VK_ENTER -> com.badlogic.gdx.Input.Keys.ENTER
    KeyEvent.VK_SPACE -> com.badlogic.gdx.Input.Keys.SPACE
    KeyEvent.VK_ESCAPE -> com.badlogic.gdx.Input.Keys.ESCAPE
    KeyEvent.VK_BACK_SPACE -> com.badlogic.gdx.Input.Keys.BACKSPACE
    KeyEvent.VK_LEFT -> com.badlogic.gdx.Input.Keys.LEFT
    KeyEvent.VK_RIGHT -> com.badlogic.gdx.Input.Keys.RIGHT
    else -> 0
}
