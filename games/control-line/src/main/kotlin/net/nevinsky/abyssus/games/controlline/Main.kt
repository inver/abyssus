/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import net.nevinsky.abyssus.games.controlline.score.defaultScoresFile
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
        setForegroundFPS(120)
        setBackBufferConfig(8, 8, 8, 8, 24, 0, 4)
    }
    Lwjgl3Application(ControlLineGame(project, defaultScoresFile()), config)
}
