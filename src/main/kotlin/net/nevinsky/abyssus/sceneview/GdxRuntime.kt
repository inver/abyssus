/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.Application
import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.glutils.GLVersion
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.GdxNativesLoader
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Per-frame values reported by the `Gdx.graphics` shim. Touched only on the AWT thread. */
class GdxFrame {
    var width = 0
    var height = 0
    var deltaSeconds = 0f
    private var lastNanos = 0L

    fun tick(width: Int, height: Int, nowNanos: Long = System.nanoTime()) {
        deltaSeconds = if (lastNanos == 0L) 0f else (nowNanos - lastNanos) / 1e9f
        lastNanos = nowNanos
        this.width = width
        this.height = height
    }
}

class GdxContext(val app: Application, val graphics: Graphics, val gl20: GL20, val gl30: GL30?, val files: Files)

private fun defaultFor(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0f
    java.lang.Double.TYPE -> 0.0
    java.lang.Short.TYPE -> 0.toShort()
    java.lang.Byte.TYPE -> 0.toByte()
    else -> null
}

/**
 * A dynamic implementation of [type]: [handle] answers the methods that matter (returning `null` for
 * "not handled"); everything else returns the default for its return type (0, false, null).
 */
fun <T : Any> stubOf(type: Class<T>, handle: (Method, Array<out Any?>) -> Any? = { _, _ -> null }): T {
    val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args ->
        val a = args ?: emptyArray()
        if (method.declaringClass == Any::class.java) {
            when (method.name) {
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === a[0]
                else -> "${type.simpleName} stub"
            }
        } else {
            handle(method, a) ?: defaultFor(method.returnType)
        }
    }
    return type.cast(proxy)
}

/**
 * Lets libGDX core run without a libGDX backend. `Gdx.*` is process-global inside the IDE, so it is
 * installed only for the duration of [withContext] and the previous values are restored afterwards.
 */
object GdxRuntime {
    private val lock = ReentrantLock()

    // Same prefixes Lwjgl3Application installs for GL3: libGDX shaders are GLSL 1.20 and the canvas is a core profile.
    private const val VERTEX_PREFIX = "#version 150\n#define attribute in\n#define varying out\n"
    private const val FRAGMENT_PREFIX = "#version 150\n#define varying in\n#define texture2D texture\n" +
        "#define textureCube texture\n#define gl_FragColor fragColor\nout vec4 fragColor;\n"

    init {
        GdxNativesLoader.load()
    }

    fun newContext(frame: GdxFrame, gl20: GL20, gl30: GL30?, files: Files): GdxContext {
        lateinit var graphics: Graphics
        val glVersion by lazy {
            GLVersion(
                Application.ApplicationType.Desktop,
                gl20.glGetString(GL20.GL_VERSION),
                gl20.glGetString(GL20.GL_VENDOR),
                gl20.glGetString(GL20.GL_RENDERER),
            )
        }
        val app = stubOf(Application::class.java) { m, _ ->
            when (m.name) {
                "getType" -> Application.ApplicationType.Desktop
                "getGraphics" -> graphics
                "getFiles" -> files
                else -> null
            }
        }
        graphics = stubOf(Graphics::class.java) { m, _ ->
            when (m.name) {
                "getWidth", "getBackBufferWidth" -> frame.width
                "getHeight", "getBackBufferHeight" -> frame.height
                "getDeltaTime" -> frame.deltaSeconds
                "getBackBufferScale", "getDensity" -> 1f
                "isGL30Available" -> gl30 != null
                "getGL20" -> gl20
                "getGL30" -> gl30
                "getGLVersion" -> glVersion
                else -> null
            }
        }
        return GdxContext(app, graphics, gl20, gl30, files)
    }

    fun <T> withContext(ctx: GdxContext, block: () -> T): T = lock.withLock {
        val app = Gdx.app
        val graphics = Gdx.graphics
        val gl = Gdx.gl
        val gl20 = Gdx.gl20
        val gl30 = Gdx.gl30
        val files = Gdx.files
        val vertexPrefix = ShaderProgram.prependVertexCode
        val fragmentPrefix = ShaderProgram.prependFragmentCode
        Gdx.app = ctx.app
        Gdx.graphics = ctx.graphics
        Gdx.gl20 = ctx.gl20
        Gdx.gl30 = ctx.gl30
        Gdx.gl = ctx.gl30 ?: ctx.gl20
        Gdx.files = ctx.files
        ShaderProgram.prependVertexCode = VERTEX_PREFIX
        ShaderProgram.prependFragmentCode = FRAGMENT_PREFIX
        try {
            block()
        } finally {
            Gdx.app = app
            Gdx.graphics = graphics
            Gdx.gl = gl
            Gdx.gl20 = gl20
            Gdx.gl30 = gl30
            Gdx.files = files
            ShaderProgram.prependVertexCode = vertexPrefix
            ShaderProgram.prependFragmentCode = fragmentPrefix
        }
    }
}
