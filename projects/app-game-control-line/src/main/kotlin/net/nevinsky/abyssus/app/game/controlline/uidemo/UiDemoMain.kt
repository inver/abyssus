/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.uidemo

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import java.nio.file.Path

/**
 * Starts the UI demo on the project in the `uiDemo.project` system property (`./gradlew :app-game-control-line:runUiDemo`
 * sets it to `uiDemo/UiDemo`). Separate from [net.nevinsky.abyssus.app.game.controlline.main], which is unchanged.
 */
fun main() {
    val project = Path.of(System.getProperty("uiDemo.project") ?: "uiDemo/UiDemo").toAbsolutePath()
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Abyssus UI demo")
        setWindowedMode(1280, 720)
        useVsync(true)
        setForegroundFPS(60)
        setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2)
    }
    Lwjgl3Application(UiDemoGame(project), config)
}
