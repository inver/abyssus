/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.app.game.controlline.render.Cable
import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import net.nevinsky.abyssus.app.game.controlline.render.FieldView
import net.nevinsky.abyssus.app.game.controlline.render.LineSegment
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import net.nevinsky.abyssus.app.game.controlline.display.DisplaySettings
import net.nevinsky.abyssus.app.game.controlline.display.defaultDisplayFile
import net.nevinsky.abyssus.app.game.controlline.pattern.defaultPatternScoresFile
import net.nevinsky.abyssus.app.game.controlline.score.defaultScoresFile
import com.badlogic.gdx.Graphics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("control-line")

/**
 * Starts the game on the project in the `controlLine.project` system property (`./gradlew :games:control-line:run`
 * sets it to the bundled project), or the bundled `project/ControlLine` of the distribution (see `defaultProjectFolder`).
 *
 * The opt-in ray-traced field view is chosen with `controlLine.fieldView=ray`. It lives in the `ray` source set and is
 * loaded reflectively here so the shipped main classpath never sees it. On `Unavailable` (classes absent, probe refused,
 * runtime missing) the game starts the raster field and states the reason in the window title and the log: it never
 * crashes and never switches silently. Without the option the field renders exactly as before.
 *
 * A failure on any game thread leaves a crash record in `~/.abyssus-control-line/logs` ([CrashReporter]) and the process
 * exits with status 1. `controlLine.crashTest=true` makes the field view fail on its first frame, to check that path in a
 * run or a package (`JAVA_TOOL_OPTIONS=-DcontrolLine.crashTest=true`).
 */
fun main() {
    val crashes = CrashReporter(defaultLogsFolder())
    crashes.onCrash = { Gdx.app?.exit() }
    Thread.setDefaultUncaughtExceptionHandler(crashes)
    try {
        start(crashes)
    } catch (error: Throwable) {
        // the window could not be made, or the game failed before its first guarded call
        crashes.report(Thread.currentThread(), error)
    }
    if (crashes.crashed) exitProcess(1)
}

private fun start(crashes: CrashReporter) {
    val project = (System.getProperty("controlLine.project")?.let { Path.of(it) } ?: defaultProjectFolder()).toAbsolutePath()
    val config = gameConfiguration(DisplaySettings(defaultDisplayFile()).fullscreen, primaryMonitorPresent(), log)
    val fieldView = if (System.getProperty("controlLine.crashTest") == "true") {
        log.warn("controlLine.crashTest is set: the field view fails on its first frame")
        RayFieldResult.Available(CrashTestFieldView())
    } else {
        resolveFieldView(project)
    }
    val reason = if (fieldView is RayFieldResult.Unavailable) fieldView.reason else null
    val view = when (fieldView) {
        is RayFieldResult.Available -> fieldView.view.also { if (it.kind == "ray") config.setTitle("Control Line (ray)") }
        else -> {
            reason?.let { r ->
                config.setTitle("Control Line (raster: $r)")
                log.warn("Ray-traced field view unavailable: $r; running the raster field")
            }
            null
        }
    }
    Lwjgl3Application(ControlLineGame(project, defaultScoresFile(), defaultPatternScoresFile(), view, crashes = crashes), config)
}

/** The field view of `controlLine.crashTest`: it fails on its first frame, so a run shows what a crash leaves behind. */
private class CrashTestFieldView : FieldView {
    override val kind: String get() = "crash test"

    override fun draw(
        field: FieldScene, camera: Camera, lines: List<LineSegment>, hidden: Entity?,
        clips: Map<Entity, String>, seconds: Float, cables: List<Cable>, poses: Map<Entity, Matrix4>,
    ): Unit = throw IllegalStateException("controlLine.crashTest: a forced failure while drawing a frame")

    override fun resize(width: Float, height: Float) = Unit
    override fun dispose() = Unit
}

/** Where the window opens when no monitor is reported: libGDX would centre it on a primary monitor there is none of. */
internal const val NO_MONITOR_WINDOW_X = 64
internal const val NO_MONITOR_WINDOW_Y = 64

/**
 * The window of the game. With a [primaryMonitor] it opens centred, or fullscreen at [displayMode] when the player last
 * chose fullscreen ([savedFullscreen]). Without one (a locked screen, a virtual display: evidence M5 of OpenSpec change
 * `improve-control-line-performance-stability`) it opens windowed at a fixed position for this start, ignoring a saved
 * fullscreen choice (which stays saved), and [log] says so.
 */
internal fun gameConfiguration(
    savedFullscreen: Boolean, primaryMonitor: Boolean, log: Logger,
    displayMode: () -> Graphics.DisplayMode = { Lwjgl3ApplicationConfiguration.getDisplayMode() },
): Lwjgl3ApplicationConfiguration = Lwjgl3ApplicationConfiguration().apply {
    setTitle("Control Line")
    // The window and taskbar icon on Linux and Windows: without it the window manager shows Java's icon. The sizes are
    // packaged into the jar from packaging/icons/ (processResources in app-game-control-line.gradle.kts); one 1024 px
    // icon is not enough because X11's _NET_WM_ICON request would exceed the server's maximum request size.
    setWindowIcon(
        Files.FileType.Classpath,
        "icons/control-line-16.png",
        "icons/control-line-32.png",
        "icons/control-line-48.png",
        "icons/control-line-64.png",
        "icons/control-line-128.png",
        "icons/control-line-256.png",
    )
    setWindowedMode(1280, 720)
    useVsync(true)
    // a steady 60 frames a second: on a 120 Hz screen an uncapped, vsynced loop alternates 8, 17 and 25 ms frames
    setForegroundFPS(60)
    setBackBufferConfig(8, 8, 8, 8, 24, 0, 4)
    // foliage is drawn instanced, which needs glVertexAttribDivisor, core in OpenGL 3.3: a 3.3 core context (forward compatible on macOS)
    setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3)
    if (!primaryMonitor) {
        setWindowPosition(NO_MONITOR_WINDOW_X, NO_MONITOR_WINDOW_Y)
        log.warn(
            "No primary monitor is reported; opening a window at ($NO_MONITOR_WINDOW_X, $NO_MONITOR_WINDOW_Y)" +
                if (savedFullscreen) " instead of fullscreen for this start" else ""
        )
    } else if (savedFullscreen) {
        // the mode the player last chose on the settings screen: the window opens in it, with no windowed flash
        setFullscreenMode(displayMode())
    }
}

/**
 * Whether GLFW reports a primary monitor; asking initializes GLFW the way the application will. libGDX's own call fails
 * reading the position of a monitor that is not there (evidence M5); only `GlfwJoysticks` talks to GLFW directly.
 */
private fun primaryMonitorPresent(): Boolean = try {
    Lwjgl3ApplicationConfiguration.getPrimaryMonitor() != null
} catch (none: RuntimeException) {
    false
}

/**
 * The project folder without the property: `project/ControlLine` beside the distribution's `lib` folder when the game
 * runs from its jars, otherwise under the working folder.
 */
internal fun defaultProjectFolder(): Path {
    val bundled = try {
        val jar = Path.of(ControlLineGame::class.java.protectionDomain.codeSource.location.toURI())
        jar.parent?.parent?.resolve("project/ControlLine")
    } catch (unresolvable: Exception) {
        null
    }
    return bundled?.takeIf { java.nio.file.Files.isDirectory(it) } ?: Path.of("project/ControlLine")
}

/** The result of resolving the optional ray-traced field view at launch. */
sealed interface RayFieldResult {
    data class Available(val view: FieldView) : RayFieldResult
    data class Unavailable(val reason: String?) : RayFieldResult
}

/** Resolves the launch option reflectively without pulling the `ray` source set onto the main classpath. */
internal fun resolveFieldView(project: Path): RayFieldResult {
    if ("ray" != System.getProperty("controlLine.fieldView")) return RayFieldResult.Unavailable(null)
    return try {
        val entry = Class.forName("net.nevinsky.abyssus.app.game.controlline.ray.RayFieldEntry")
        val method = entry.getMethod("fieldView", Path::class.java)
        val result = method.invoke(null, project)
        when (result) {
            is RayFieldResult.Available -> result
            is RayFieldResult.Unavailable -> result
            else -> RayFieldResult.Unavailable("RayFieldEntry.fieldView returned an unknown result")
        }
    } catch (notFound: ClassNotFoundException) {
        RayFieldResult.Unavailable("ray source set not on the classpath: ${notFound.message}")
    } catch (noMethod: NoSuchMethodException) {
        RayFieldResult.Unavailable("RayFieldEntry has no fieldView() method: ${noMethod.message}")
    } catch (target: java.lang.reflect.InvocationTargetException) {
        RayFieldResult.Unavailable("RayFieldEntry failed to start: ${target.cause?.message ?: target.message}")
    } catch (reflective: Exception) {
        RayFieldResult.Unavailable("Could not load the ray-traced field view: ${reflective.message}")
    }
}
