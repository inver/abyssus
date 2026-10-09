/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView.preview

import com.badlogic.gdx.backends.lwjgl3.GdxGlBridge
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.lib.gdx.AnimationController
import net.nevinsky.abyssus.lib.gdx.ModelInstance
import net.nevinsky.abyssus.lib.core.assets.model.PreparedModel
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3
import net.nevinsky.abyssus.lib.gdx.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.Model
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.shader.DefaultShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.ShaderProvider
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.sceneview.GdxContext
import net.nevinsky.abyssus.plugin.sceneview.GdxFrame
import net.nevinsky.abyssus.plugin.sceneview.GdxRuntime
import net.nevinsky.abyssus.plugin.sceneview.GridModel
import net.nevinsky.abyssus.plugin.sceneview.GuardedGLCanvas
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.Timer
import com.badlogic.gdx.graphics.g3d.Model as GdxModel
import com.badlogic.gdx.graphics.g3d.ModelInstance as GdxModelInstance
import net.nevinsky.abyssus.lib.gdx.ModelBatch as ContentBatch

/**
 * A model as the preview draws it: the transformed [data], its [pixmaps] decoded off the AWT thread (the canvas takes
 * them over), the [file] its texture names resolve against, and its [size] in metres for framing.
 */
class PreviewModel(val data: ModelData, val pixmaps: MutableMap<String, Pixmap>, val file: FileHandle, val size: Vector3)

/**
 * The live 3D preview of Import Model: a [GuardedGLCanvas] with its own `GdxRuntime` context, drawing the model as Create
 * would write it over a ground grid with a 1 m post, from an orbit camera (drag to orbit, wheel to zoom). The chosen
 * animation plays in a loop; textures are uploaded one per frame. A Swing timer drives frames, and GL is used only
 * inside `GdxRuntime.withContext` while the canvas is safe ([GuardedGLCanvas.glSafe]). The dialog calls [release]
 * while it is still showing, so the context is current when its resources are freed. Without GL the reason is shown.
 */
class ModelPreviewCanvas : JPanel(CardLayout()) {
    private val frame = GdxFrame()
    private val framing = PreviewFraming()
    private val orbit = OrbitCamera(Vec3(0f, 0.5f, 0f), 3f, 30f, 20f)
    private val message = JBLabel("", SwingConstants.CENTER)
    private var gdx: GdxContext? = null
    private var capabilities: GLCapabilities? = null
    private var released = false

    /** The model to show next; taken by the next frame. AWT thread. */
    private var pending: PreviewModel? = null
    private var animationName: String? = null
    private var animationChanged = false
    private var reframe = true
    private var size = Vector3(1f, 1f, 1f)
    private var shownMessage: String? = null
    private var framesDrawn = 0
    private var centrePixel = 0

    /** Whether each frame reads back its centre pixel, for [state]; tests only. */
    internal var sampleCentre = false

    // GL state, touched only inside the context
    private var gridBatch: ModelBatch? = null
    private var contentShaders: DefaultShaderProvider? = null
    private var contentBatch: ContentBatch? = null
    private var grid: GdxModel? = null
    private var post: GdxModel? = null
    private var gridInstance: GdxModelInstance? = null
    private var postInstance: GdxModelInstance? = null
    private var prepared: PreparedModel? = null
    private var preparedSize: Vector3? = null
    private var model: Model? = null
    private var instance: ModelInstance? = null
    private var animation: AnimationController? = null
    private val camera = PerspectiveCamera()
    private val environment = Environment().apply {
        set(ColorAttribute.createAmbientLight(0.45f, 0.45f, 0.45f, 1f))
        add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -0.4f, -1f, -0.3f))
    }

    private val canvas = object : GuardedGLCanvas(glData()) {
        override fun initGL() {
            capabilities = GL.createCapabilities()
            val ctx = GdxRuntime.newContext(frame, GdxGlBridge.gl20(), GdxGlBridge.gl30(), Lwjgl3Files())
            gdx = ctx
            GdxRuntime.withContext(ctx) { create() }
        }

        override fun disposeGL() {
            val ctx = gdx ?: return
            gdx = null
            capabilities?.let { GL.setCapabilities(it) }
            try {
                GdxRuntime.withContext(ctx) { disposeResources() }
            } catch (e: Throwable) {
                thisLogger().warn("Failed to release the model preview's GL resources", e)
            }
        }

        override fun onContextAbandoned() {
            gdx = null
            forgetResources()
        }

        override fun paintGL() {
            val ctx = gdx ?: return
            capabilities?.let { GL.setCapabilities(it) }
            frame.tick(framebufferWidth, framebufferHeight)
            GdxRuntime.withContext(ctx) { draw() }
            swapBuffers()
        }
    }

    private val timer = Timer(FRAME_MILLIS) {
        if (released || !canvas.glSafe()) return@Timer
        try {
            canvas.render()
        } catch (e: Throwable) {
            thisLogger().warn("Model preview failed", e)
            stopWith(e.message ?: e.javaClass.simpleName)
        }
    }

    init {
        add(canvas, CARD_CANVAS)
        add(JPanel(BorderLayout()).apply { add(message, BorderLayout.CENTER) }, CARD_MESSAGE)
        preferredSize = JBUI.size(420, 320)
        val mouse = object : MouseAdapter() {
            private var last: java.awt.Point? = null
            override fun mousePressed(e: MouseEvent) { last = e.point }
            override fun mouseReleased(e: MouseEvent) { last = null }
            override fun mouseDragged(e: MouseEvent) {
                val from = last ?: e.point
                orbit.orbit((e.x - from.x).toFloat(), (e.y - from.y).toFloat())
                last = e.point
            }
            override fun mouseWheelMoved(e: MouseWheelEvent) = orbit.zoom(e.preciseWheelRotation.toFloat())
        }
        canvas.addMouseListener(mouse)
        canvas.addMouseMotionListener(mouse)
        canvas.addMouseWheelListener(mouse)
        timer.start()
    }

    /** Shows [next] (the previous model is released on the next frame); the camera is framed on it. AWT thread. */
    fun show(next: PreviewModel) {
        if (released) {
            next.pixmaps.values.forEach(Pixmap::dispose)
            return
        }
        pending?.pixmaps?.values?.forEach(Pixmap::dispose)
        pending = next
        shownMessage = null
        (layout as CardLayout).show(this, CARD_CANVAS)
    }

    /** Plays [name] in a loop, or nothing. */
    fun play(name: String?) {
        animationName = name
        animationChanged = true
    }

    /** Shows [text] instead of a model, such as why the source could not be converted. */
    fun showMessage(text: String) {
        shownMessage = text
        message.text = "<html><div style='text-align:center'>$text</div></html>"
        (layout as CardLayout).show(this, CARD_MESSAGE)
    }

    /**
     * Stops drawing and frees every GL resource the preview made, with its context current; call while the dialog is
     * still showing (a hidden canvas cannot make its context current, and then only abandons it). Idempotent.
     */
    fun release() {
        if (released) return
        released = true
        timer.stop()
        pending?.pixmaps?.values?.forEach(Pixmap::dispose)
        pending = null
        canvas.disposeCanvas()
    }

    /** A failed context or frame: drawing stops and the reason is shown; the dialog's Create still works. */
    private fun stopWith(reason: String) {
        timer.stop()
        showMessage(AbyssusBundle.message("importModelPreviewFailed", reason))
    }

    /** What the preview is doing, for tests. AWT thread. */
    internal class State(
        val frames: Int,
        val built: Boolean,
        val uploading: Boolean,
        val playing: String?,
        val animationTime: Float,
        val size: Vector3,
        val message: String?,
        val released: Boolean,
        val centre: Int,
    )

    internal fun state() = State(
        framesDrawn, model != null, prepared != null || pending != null, animation?.current?.animation?.id,
        animation?.current?.time ?: 0f, Vector3(size), shownMessage, released, centrePixel,
    )

    // --- GL, inside the context -------------------------------------------------------------------------------------

    private fun create() {
        gridBatch = ModelBatch()
        contentBatch = ContentBatch(DefaultShaderProvider().also { contentShaders = it })
        grid = GridModel.build().also { gridInstance = GdxModelInstance(it) }
        val material = Material(ColorAttribute.createDiffuse(Color(0.95f, 0.55f, 0.1f, 1f)))
        post = ModelBuilder().createBox(PreviewFraming.POST_WIDTH * 2, PreviewFraming.POST_HEIGHT, PreviewFraming.POST_WIDTH * 2,
            material, (Usage.Position or Usage.Normal).toLong()).also { postInstance = GdxModelInstance(it) }
    }

    private fun draw() {
        val gl = com.badlogic.gdx.Gdx.gl
        take()
        uploadAndBuild()
        if (animationChanged) {
            animationChanged = false
            instance?.let { startAnimation(it) }
        }
        animation?.update(frame.deltaSeconds)

        val aspect = if (frame.height > 0) frame.width.toFloat() / frame.height else 1f
        if (reframe) {
            reframe = false
            val f = framing.frame(size, aspect)
            orbit.target = f.target
            orbit.distance = f.distance
        }
        val f = framing.frame(size, aspect)
        camera.viewportWidth = frame.width.toFloat()
        camera.viewportHeight = frame.height.toFloat()
        camera.fieldOfView = PreviewFraming.FOV_DEGREES
        camera.near = f.near
        camera.far = maxOf(f.far, orbit.distance * 4f)
        val eye = orbit.position()
        camera.position.set(eye.x, eye.y, eye.z)
        camera.up.set(Vector3.Y)
        camera.lookAt(orbit.target.x, orbit.target.y, orbit.target.z)
        camera.update()
        postInstance?.transform?.setToTranslation(framing.postX(size), PreviewFraming.POST_HEIGHT / 2f, 0f)

        gl.glViewport(0, 0, frame.width, frame.height)
        gl.glClearColor(0.16f, 0.17f, 0.19f, 1f)
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        gridBatch?.let { batch ->
            batch.begin(camera)
            gridInstance?.let { batch.render(it) }
            postInstance?.let { batch.render(it, environment) }
            batch.end()
        }
        instance?.let { shown ->
            contentBatch?.let { batch ->
                batch.begin(camera)
                batch.render(shown, environment, ShaderProvider.DEFAULT_SHADER_KEY)
                batch.end()
            }
        }
        framesDrawn++
        if (sampleCentre) {
            val pixel = BufferUtils.newByteBuffer(4)
            gl.glReadPixels(frame.width / 2, frame.height / 2, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel)
            centrePixel = ((pixel.get(0).toInt() and 0xFF) shl 16) or ((pixel.get(1).toInt() and 0xFF) shl 8) or (pixel.get(2).toInt() and 0xFF)
        }
    }

    /** A new model replaces the one being prepared; the shown one stays until the new one is built. */
    private fun take() {
        val next = pending ?: return
        pending = null
        prepared?.dispose()
        prepared = PreparedModel(next.data, next.file, next.pixmaps)
        preparedSize = next.size
    }

    /** One texture per frame, then the model: as the scene view builds its models. */
    private fun uploadAndBuild() {
        val building = prepared ?: return
        if (!building.uploadNext()) return
        prepared = null
        val built = AssimpModelLoader().build(building.data, building.file, building.textures)
        building.dispose()
        animation = null
        model?.dispose()
        model = built
        instance = ModelInstance(built).also { startAnimation(it) }
        val newSize = preparedSize ?: size
        if (!newSize.epsilonEquals(size, 1e-4f)) reframe = true
        size = newSize
    }

    private fun startAnimation(target: ModelInstance) {
        val name = animationName?.takeIf { wanted -> target.animations.any { it.id == wanted } }
        animation = name?.let { AnimationController(target).also { c -> c.setAnimation(it, -1) } }
        if (name == null) target.calculateTransforms()
    }

    private fun disposeResources() {
        prepared?.dispose()
        model?.dispose()
        grid?.dispose()
        post?.dispose()
        gridBatch?.dispose()
        contentShaders?.dispose()
        forgetResources()
    }

    /** Drops every handle without GL calls: the context they belong to is gone. */
    private fun forgetResources() {
        prepared = null
        model = null
        instance = null
        animation = null
        grid = null
        post = null
        gridInstance = null
        postInstance = null
        gridBatch = null
        contentShaders = null
        contentBatch = null
    }

    private companion object {
        const val FRAME_MILLIS = 16
        const val CARD_CANVAS = "canvas"
        const val CARD_MESSAGE = "message"

        fun glData() = GLData().apply {
            majorVersion = 3
            minorVersion = 2
            profile = GLData.Profile.CORE
            forwardCompatible = true
            depthSize = 24
            swapInterval = 0
        }
    }
}
