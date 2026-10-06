/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.FlushablePool
import com.badlogic.gdx.utils.GdxRuntimeException
import net.nevinsky.abyssus.lib.core.shader.Shader
import net.nevinsky.abyssus.lib.core.shader.ShaderProvider

/**
 * Batches [Renderable] instances, fetches [Shader]s for them, sorts them and then renders them. Fetching
 * the shaders is done using a [ShaderProvider], which defaults to [DefaultShaderProvider]. Sorting the
 * renderables is done using a [RenderableSorter], which default to [DefaultRenderableSorter].
 *
 *
 * The OpenGL context between the [.begin] and [.end] call is maintained by the
 * [RenderContext].
 *
 *
 * To provide multiple [Renderable]s at once a [RenderableProvider] can be used, e.g. a
 * [ModelInstance].
 *
 * @author xoppa, badlogic
 */
class ModelBatch(context: RenderContext?, sorter: RenderableSorter?, protected val shaderProvider: ShaderProvider) {
    protected class RenderablePool : FlushablePool<Renderable>() {
        override fun newObject(): Renderable {
            return Renderable()
        }

        override fun obtain(): Renderable {
            val renderable = super.obtain()
            renderable.cleanup()
            return renderable
        }
    }

    private var camera: Camera? = null
    protected val renderablesPool: RenderablePool = RenderablePool()

    /**
     * list of Renderables to be rendered in the current batch
     */
    protected val renderables: Array<Renderable> = Array<Renderable>()
    /**
     * @return the [RenderContext] used by this ModelBatch.
     */
    /**
     * the [RenderContext]
     */
    val renderContext: RenderContext
    private val ownContext: Boolean
    /**
     * @return the [RenderableSorter] used by this ModelBatch.
     */
    /**
     * the [RenderableSorter]
     */
    val renderableSorter: RenderableSorter

    constructor(shaderProvider: ShaderProvider) : this(null, null, shaderProvider)

    /**
     * Construct a ModelBatch, using this constructor makes you responsible for calling context.begin() and
     * context.end() yourself.
     *
     * @param context        The [RenderContext] to use.
     * @param sorter         The [RenderableSorter] to use.
     * @param shaderProvider
     */
    init {
        this.renderableSorter = if (sorter == null) DefaultRenderableSorter() else sorter
        this.ownContext = (context == null)
        this.renderContext =
            if (context == null) RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1)) else context
    }

    /**
     * Construct a ModelBatch, using this constructor makes you responsible for calling context.begin() and
     * context.end() yourself.
     *
     * @param context        The [RenderContext] to use.
     * @param shaderProvider
     */
    constructor(context: RenderContext?, shaderProvider: ShaderProvider) : this(context, null, shaderProvider)

    /**
     * Construct a ModelBatch
     *
     * @param sorter         The [RenderableSorter] to use.
     * @param shaderProvider
     */
    constructor(sorter: RenderableSorter?, shaderProvider: ShaderProvider) : this(null, sorter, shaderProvider)

    /**
     * Start rendering one or more [Renderable]s. Use one of the render() methods to provide the renderables. Must
     * be followed by a call to [.end]. The OpenGL context must not be altered between [.begin]
     * and [.end].
     *
     * @param cam The [Camera] to be used when rendering and sorting.
     */
    fun begin(cam: Camera?) {
        if (camera != null) {
            throw GdxRuntimeException("Call end() first.")
        }
        camera = cam
        if (ownContext) {
            renderContext.begin()
        }
    }

    /**
     * Change the camera in between [.begin] and [.end]. This causes the batch to be flushed. Can
     * only be called after the call to [.begin] and before the call to [.end].
     *
     * @param cam The new camera to use.
     */
    fun setCamera(cam: Camera?) {
        if (camera == null) {
            throw GdxRuntimeException("Call begin() first.")
        }
        if (renderables.size > 0) {
            flush()
        }
        camera = cam
    }

    /**
     * Provides access to the current camera in between [.begin] and [.end]. Do not change the
     * camera's values. Use [.setCamera], if you need to change the camera.
     *
     * @return The current camera being used or null if called outside [.begin] and [.end].
     */
    fun getCamera(): Camera? {
        return camera
    }

    /**
     * Checks whether the [RenderContext] returned by [.getRenderContext] is owned and managed by this
     * ModelBatch. When the RenderContext isn't owned by the ModelBatch, you are responsible for calling the
     * [RenderContext.begin] and [RenderContext.end] methods yourself, as well as disposing the
     * RenderContext.
     *
     * @return True if this ModelBatch owns the RenderContext, false otherwise.
     */
    fun ownsRenderContext(): Boolean {
        return ownContext
    }

    /**
     * Flushes the batch, causing all [Renderable]s in the batch to be rendered. Can only be called after the call
     * to [.begin] and before the call to [.end].
     */
    fun flush() {
        renderableSorter.sort(camera!!, renderables)
        var currentShader: Shader? = null
        for (i in 0..<renderables.size) {
            val renderable = renderables.get(i)
            if (currentShader !== renderable.shader) {
                if (currentShader != null) {
                    currentShader.end()
                }
                currentShader = renderable.shader
                currentShader!!.begin(camera, this.renderContext)
            }
            currentShader!!.render(renderable)
        }
        if (currentShader != null) {
            currentShader.end()
        }
        renderablesPool.flush()
        renderables.clear()
    }

    /**
     * End rendering one or more [Renderable]s. Must be called after a call to [.begin]. This will
     * flush the batch, causing any renderables provided using one of the render() methods to be rendered. After a call
     * to this method the OpenGL context can be altered again.
     */
    fun end() {
        flush()
        if (ownContext) {
            renderContext.end()
        }
        camera = null
    }

    /**
     * Calls [RenderableProvider.getRenderables] and adds all returned [Renderable] instances
     * to the current batch to be rendered. Any shaders set on the returned renderables will be replaced with the given
     * [Shader]. Can only be called after a call to [.begin] and before a call to [.end].
     *
     * @param renderableProvider the renderable provider
     * @param shaderKey          the shader key to get shader to use for the renderables
     */
    fun render(renderableProvider: RenderableProvider, shaderKey: String?) {
        val offset = renderables.size
        renderableProvider.getRenderables(renderables, renderablesPool)
        for (i in offset..<renderables.size) {
            val renderable = renderables.get(i)
            renderable.shader = shaderProvider.get(shaderKey, renderable)
        }
    }

    /**
     * Calls [RenderableProvider.getRenderables] and adds all returned [Renderable] instances
     * to the current batch to be rendered. Any shaders set on the returned renderables will be replaced with the given
     * [Shader]. Can only be called after a call to [.begin] and before a call to [.end].
     *
     * @param renderableProviders one or more renderable providers
     * @param shaderKey           the shader key to get shader to use for the renderables
     */
    fun <T : RenderableProvider?> render(renderableProviders: Iterable<T?>, shaderKey: String?) {
        for (renderableProvider in renderableProviders) {
            render(renderableProvider!!, shaderKey)
        }
    }

    /**
     * Calls [RenderableProvider.getRenderables] and adds all returned [Renderable] instances
     * to the current batch to be rendered. Any environment set on the returned renderables will be replaced with the
     * given environment. Any shaders set on the returned renderables will be replaced with the given [Shader].
     * Can only be called after a call to [.begin] and before a call to [.end].
     *
     * @param renderableProvider the renderable provider
     * @param environment        the [Environment] to use for the renderables
     * @param shaderKey          the shader key to get shader to use for the renderables
     */
    fun render(
        renderableProvider: RenderableProvider, environment: Environment?,
        shaderKey: String?
    ) {
        val offset = renderables.size
        renderableProvider.getRenderables(renderables, renderablesPool)
        for (i in offset..<renderables.size) {
            val renderable = renderables.get(i)
            renderable.environment = environment
            renderable.shader = shaderProvider.get(shaderKey, renderable)
        }
    }

    /**
     * Calls [RenderableProvider.getRenderables] and adds all returned [Renderable] instances
     * to the current batch to be rendered. Any environment set on the returned renderables will be replaced with the
     * given environment. Any shaders set on the returned renderables will be replaced with the given [Shader].
     * Can only be called after a call to [.begin] and before a call to [.end].
     *
     * @param renderableProviders one or more renderable providers
     * @param environment         the [Environment] to use for the renderables
     * @param shaderKey           the shader key to get shader to use for the renderables
     */
    fun <T : RenderableProvider?> render(
        renderableProviders: Iterable<T?>,
        environment: Environment?,
        shaderKey: String?
    ) {
        for (renderableProvider in renderableProviders) {
            render(renderableProvider!!, environment, shaderKey)
        }
    }
}
