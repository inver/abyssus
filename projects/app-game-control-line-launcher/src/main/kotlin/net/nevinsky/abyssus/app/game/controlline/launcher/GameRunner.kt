/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.launcher

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

/** What to run: the jars of one game folder, its main class and the bundled project folder. */
data class GameLaunch(val jars: List<Path>, val mainClass: String, val project: Path)

/** Runs the game until it ends. A failure to start, or an exception out of the game's main, is thrown. */
fun interface GameRunner {
    fun run(launch: GameLaunch)
}

/**
 * Runs the game's main on the calling thread (the process's first thread, which macOS needs for the window) from a
 * `URLClassLoader` whose parent is the platform loader, so the game sees exactly its own jars.
 */
class ClassLoaderRunner : GameRunner {
    override fun run(launch: GameLaunch) {
        System.setProperty("controlLine.project", launch.project.toString())
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        URLClassLoader(launch.jars.map { it.toUri().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { loader ->
            thread.contextClassLoader = loader
            try {
                val type = loader.loadClass(launch.mainClass)
                val withArgs = type.methods.firstOrNull {
                    it.name == "main" && it.parameterTypes.contentEquals(arrayOf(Array<String>::class.java))
                }
                try {
                    if (withArgs != null) withArgs.invoke(null, arrayOf<String>())
                    else type.getMethod("main").invoke(null)
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw e.targetException
                }
            } finally {
                thread.contextClassLoader = previous
            }
        }
    }
}

/**
 * The fallback if the class-loader route fails on an OS: starts the game in a child JVM and waits. [java] is the
 * `java` executable to use (the bundled runtime's), [jvmArgs] go before the class path (for example `-XstartOnFirstThread`).
 */
class ChildProcessRunner(private val java: Path, private val jvmArgs: List<String> = emptyList()) : GameRunner {
    override fun run(launch: GameLaunch) {
        val classpath = launch.jars.joinToString(File.pathSeparator)
        val command = listOf(java.toString()) + jvmArgs +
            listOf("-DcontrolLine.project=${launch.project}", "-cp", classpath, launch.mainClass)
        val process = ProcessBuilder(command).inheritIO().start()
        val code = process.waitFor()
        check(code == 0) { "The game exited with code $code" }
    }
}

/** The game jars of [folder], in a fixed order. */
fun gameJars(folder: Path): List<Path> =
    Files.list(folder).use { list -> list.filter { it.fileName.toString().endsWith(".jar") }.sorted().toList() }
