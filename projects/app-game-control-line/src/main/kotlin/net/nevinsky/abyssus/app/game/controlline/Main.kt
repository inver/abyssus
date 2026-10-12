/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import net.nevinsky.abyssus.app.game.controlline.pattern.defaultPatternScoresFile
import net.nevinsky.abyssus.app.game.controlline.score.defaultScoresFile
import java.nio.file.Path

/**
 * Starts the game on the project in the `controlLine.project` system property (`./gradlew :games:control-line:run`
 * sets it to the bundled project), or `project/ControlLine` under the working folder.
 */
fun main() {
    val project = Path.of(System.getProperty("controlLine.project") ?: "project/ControlLine").toAbsolutePath()
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Control Line")
        setWindowedMode(1280, 720)
        useVsync(true)
        // a steady 60 frames a second: on a 120 Hz screen an uncapped, vsynced loop alternates 8, 17 and 25 ms frames
        setForegroundFPS(60)
        setBackBufferConfig(8, 8, 8, 8, 24, 0, 4)
        // foliage is drawn instanced, which needs OpenGL 3: a 3.2 core context (forward compatible on macOS)
        setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2)
    }
    Lwjgl3Application(ControlLineGame(project, defaultScoresFile(), defaultPatternScoresFile()), config)
}
