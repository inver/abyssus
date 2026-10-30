/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.app.game.controlline.render.FieldView
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import net.nevinsky.abyssus.app.game.controlline.pattern.defaultPatternScoresFile
import net.nevinsky.abyssus.app.game.controlline.score.defaultScoresFile
import org.slf4j.LoggerFactory
import java.nio.file.Path

private val log = LoggerFactory.getLogger("control-line")

/**
 * Starts the game on the project in the `controlLine.project` system property (`./gradlew :games:control-line:run`
 * sets it to the bundled project), or the bundled `project/ControlLine` of the distribution (see `defaultProjectFolder`).
 *
 * The opt-in ray-traced field view is chosen with `controlLine.fieldView=ray`. It lives in the `ray` source set and is
 * loaded reflectively here so the shipped main classpath never sees it. On `Unavailable` (classes absent, probe refused,
 * runtime missing) the game starts the raster field and states the reason in the window title and the log: it never
 * crashes and never switches silently. Without the option the field renders exactly as before.
 */
fun main() {
    val project = (System.getProperty("controlLine.project")?.let { Path.of(it) } ?: defaultProjectFolder()).toAbsolutePath()
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Control Line")
        setWindowedMode(1280, 720)
        useVsync(true)
        // a steady 60 frames a second: on a 120 Hz screen an uncapped, vsynced loop alternates 8, 17 and 25 ms frames
        setForegroundFPS(60)
        setBackBufferConfig(8, 8, 8, 8, 24, 0, 4)
        // foliage is drawn instanced, which needs glVertexAttribDivisor, core in OpenGL 3.3: a 3.3 core context (forward compatible on macOS)
        setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3)
    }
    val fieldView = resolveFieldView(project)
    val reason = if (fieldView is RayFieldResult.Unavailable) fieldView.reason else null
    val game = when (fieldView) {
        is RayFieldResult.Available -> {
            config.setTitle("Control Line (ray)")
            ControlLineGame(project, defaultScoresFile(), defaultPatternScoresFile(), fieldView.view)
        }
        else -> {
            reason?.let { r ->
                config.setTitle("Control Line (raster: $r)")
                log.warn("Ray-traced field view unavailable: $r; running the raster field")
            }
            ControlLineGame(project, defaultScoresFile(), defaultPatternScoresFile(), null)
        }
    }
    Lwjgl3Application(game, config)
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
