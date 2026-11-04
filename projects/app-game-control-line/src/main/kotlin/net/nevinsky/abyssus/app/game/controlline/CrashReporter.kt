/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import java.io.IOException
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** How many crash records the logs folder keeps: the newest ones. */
const val KEPT_CRASH_RECORDS = 10

/** Where the game writes its crash records: `~/.abyssus-control-line/logs`. */
fun defaultLogsFolder(home: String = System.getProperty("user.home")): Path = Path.of(home, ".abyssus-control-line", "logs")

/**
 * What the game runs on, as plain values: what a crash record names and what the game logs once at start. The GL strings
 * are null until the window's context exists; [GameEnvironment.withGl] adds them, so the reporter never touches GL.
 */
data class GameEnvironment(
    val version: String,
    val os: String,
    val jvm: String,
    val maxHeapMb: Long,
    val glRenderer: String? = null,
    val glVersion: String? = null,
    val fieldView: String? = null,
) {
    fun withGl(renderer: String?, version: String?, fieldView: String): GameEnvironment =
        copy(glRenderer = renderer, glVersion = version, fieldView = fieldView)

    /** The one environment line of the start log. */
    fun line(): String = "Control Line $version; $os; $jvm; max heap $maxHeapMb MB; " +
        "GL ${glRenderer ?: "none"}, ${glVersion ?: "none"}; field view ${fieldView ?: "unknown"}"

    companion object {
        /** The running JVM's environment; the version is the jar's `Implementation-Version`, `dev` outside a build. */
        fun current(): GameEnvironment = GameEnvironment(
            version = ControlLineGame::class.java.`package`?.implementationVersion ?: "dev",
            os = "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
            jvm = "${System.getProperty("java.vendor")} ${System.getProperty("java.version")}",
            maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024),
        )
    }
}

/**
 * Writes a plain-text crash record (design decision D1, spec `control-line-runtime-stability`) to
 * `<folder>/crash-<timestamp>.txt`: the time, the thread, [environment] and the stack trace. The record is written to
 * a temporary file first and moved into place, so a record is whole or absent. Only the newest [KEPT_CRASH_RECORDS] are
 * kept. When the folder cannot be made or written, the record goes to [stderr] instead. Nothing is sent anywhere.
 *
 * As the default uncaught-exception handler it reports the failure of any thread and then calls [onCrash], which asks
 * the game to exit. [crashed] tells `main` to exit with status 1.
 */
class CrashReporter(
    private val folder: Path,
    @Volatile var environment: GameEnvironment = GameEnvironment.current(),
    private val clock: Clock = Clock.systemUTC(),
    private val stderr: PrintStream = System.err,
) : Thread.UncaughtExceptionHandler {
    /** Called after an uncaught exception was reported: the game asks its application to exit. */
    @Volatile
    var onCrash: () -> Unit = {}

    /** Whether a failure was reported: the process then exits with a non-zero status. */
    @Volatile
    var crashed: Boolean = false
        private set

    override fun uncaughtException(thread: Thread, error: Throwable) {
        report(thread, error)
        onCrash()
    }

    /** Records [error] of [thread]; returns the record's file, or null when it went to [stderr]. */
    @Synchronized
    fun report(thread: Thread, error: Throwable): Path? {
        crashed = true
        val now = clock.instant()
        val text = format(thread.name, now.toString(), error)
        return try {
            Files.createDirectories(folder)
            val name = "crash-${FILE_TIME.format(now)}"
            var target = folder.resolve("$name.txt")
            var n = 2
            while (Files.exists(target)) target = folder.resolve("$name-${n++}.txt")
            val temp = Files.createTempFile(folder, ".crash-", ".tmp")
            try {
                Files.writeString(temp, text)
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temp)
            }
            prune()
            stderr.println("Control Line crashed: ${error}; crash record written to $target")
            target
        } catch (unwritable: IOException) {
            stderr.println("Control Line crashed; the crash record could not be written to $folder ($unwritable):")
            stderr.print(text)
            stderr.flush()
            null
        }
    }

    /** The record's text: a header of plain values, then the stack trace. */
    internal fun format(thread: String, time: String, error: Throwable): String {
        val env = environment
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        return buildString {
            appendLine("Control Line crash record")
            appendLine("time: $time")
            appendLine("thread: $thread")
            appendLine("version: ${env.version}")
            appendLine("os: ${env.os}")
            appendLine("jvm: ${env.jvm}")
            appendLine("max heap: ${env.maxHeapMb} MB")
            appendLine("gl renderer: ${env.glRenderer ?: "no context"}")
            appendLine("gl version: ${env.glVersion ?: "no context"}")
            appendLine("field view: ${env.fieldView ?: "not started"}")
            appendLine()
            append(trace)
        }
    }

    /** Deletes the oldest records beyond [KEPT_CRASH_RECORDS]; the names sort by time. */
    private fun prune() {
        val records = Files.list(folder).use { files ->
            files.filter { it.fileName.toString().let { n -> n.startsWith("crash-") && n.endsWith(".txt") } }.sorted().toList()
        }
        records.dropLast(KEPT_CRASH_RECORDS).forEach { Files.deleteIfExists(it) }
    }

    private companion object {
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
    }
}
