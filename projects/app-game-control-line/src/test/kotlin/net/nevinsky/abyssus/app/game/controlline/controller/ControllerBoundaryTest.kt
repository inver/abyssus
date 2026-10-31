/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** One class each talks to gdx-controllers and to GLFW, so everything else runs against a fake device. */
class ControllerBoundaryTest {
    @Test
    fun onlyGdxControllersImportsGdxControllers() {
        val importers = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readLines().any { it.trimStart().startsWith("import com.badlogic.gdx.controllers") } }
            .map { it.name }.toList()
        assertEquals(listOf("GdxControllers.kt"), importers)
    }

    @Test
    fun onlyGlfwJoysticksImportsGlfw() {
        val importers = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readLines().any { it.trimStart().startsWith("import org.lwjgl.glfw") } }
            .map { it.name }.toList()
        assertEquals(listOf("GlfwJoysticks.kt"), importers)
    }
}
