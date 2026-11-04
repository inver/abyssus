/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CrashReporterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val environment = GameEnvironment(
        version = "1.2.3", os = "TestOS 9 x64", jvm = "Vendor 21.0.9", maxHeapMb = 512,
        glRenderer = "Test GPU", glVersion = "3.3 Test", fieldView = "raster",
    )

    private fun records(folder: Path): List<Path> =
        Files.list(folder).use { s -> s.filter { it.fileName.toString().startsWith("crash-") }.sorted().toList() }

    private class Ticking(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = now.also { now = now.plusSeconds(1) }
    }

    @Test fun aRecordNamesTheEnvironmentAndTheStackTrace() {
        val folder = temp.root.toPath().resolve("logs")
        val err = ByteArrayOutputStream()
        val reporter = CrashReporter(folder, environment, Ticking(Instant.parse("2026-11-04T10:15:30Z")), PrintStream(err))
        val thread = Thread.currentThread()

        val file = reporter.report(thread, IllegalStateException("the field broke"))

        assertNotNull("a record is written", file)
        assertEquals("crash-20261104-101530-000.txt", file!!.fileName.toString())
        val text = Files.readString(file)
        listOf(
            "time: 2026-11-04T10:15:30Z", "thread: ${thread.name}", "version: 1.2.3", "os: TestOS 9 x64",
            "jvm: Vendor 21.0.9", "max heap: 512 MB", "gl renderer: Test GPU", "gl version: 3.3 Test", "field view: raster",
            "java.lang.IllegalStateException: the field broke", "at net.nevinsky.abyssus.app.game.controlline.CrashReporterTest",
        ).forEach { assertTrue("the record holds '$it':\n$text", text.contains(it)) }
        assertTrue(reporter.crashed)
        assertTrue("stderr names the record", err.toString().contains(file.toString()))
        assertEquals("no temporary file is left", listOf(file), Files.list(folder).use { it.toList() })
    }

    @Test fun noContextIsNamed() {
        val reporter = CrashReporter(temp.root.toPath(), environment.copy(glRenderer = null, glVersion = null, fieldView = null))
        val text = reporter.format("main", "now", RuntimeException("early"))
        assertTrue(text.contains("gl renderer: no context"))
        assertTrue(text.contains("field view: not started"))
    }

    @Test fun theEleventhRecordDeletesTheOldest() {
        val folder = temp.root.toPath()
        val reporter = CrashReporter(folder, environment, Ticking(Instant.parse("2026-11-04T10:00:00Z")), PrintStream(ByteArrayOutputStream()))
        val written = (1..11).map { reporter.report(Thread.currentThread(), RuntimeException("crash $it"))!! }

        val kept = records(folder)
        assertEquals(KEPT_CRASH_RECORDS, kept.size)
        assertFalse("the oldest is deleted", Files.exists(written.first()))
        assertEquals(written.drop(1), kept)
    }

    @Test fun otherFilesInTheFolderAreKept() {
        val folder = temp.root.toPath()
        val other = Files.writeString(folder.resolve("notes.txt"), "mine")
        val reporter = CrashReporter(folder, environment, Ticking(Instant.parse("2026-11-04T10:00:00Z")), PrintStream(ByteArrayOutputStream()))
        repeat(12) { reporter.report(Thread.currentThread(), RuntimeException("crash")) }
        assertTrue(Files.exists(other))
    }

    @Test fun anUnwritableFolderPrintsTheRecordToStandardError() {
        // a plain file where the folder should be: the folder cannot be created on any OS, whoever runs the test
        val blocked = Files.writeString(temp.root.toPath().resolve("logs"), "not a folder")
        val err = ByteArrayOutputStream()
        val reporter = CrashReporter(blocked.resolve("inner"), environment, Clock.systemUTC(), PrintStream(err))

        val file = reporter.report(Thread.currentThread(), IllegalArgumentException("no disk"))

        assertNull(file)
        assertTrue("the game still exits with a failure", reporter.crashed)
        val printed = err.toString()
        assertTrue(printed, printed.contains("could not be written"))
        assertTrue(printed, printed.contains("version: 1.2.3"))
        assertTrue(printed, printed.contains("java.lang.IllegalArgumentException: no disk"))
    }

    @Test fun anUncaughtExceptionIsReportedAndAsksToExit() {
        val folder = temp.root.toPath()
        var exits = 0
        val reporter = CrashReporter(folder, environment, Clock.systemUTC(), PrintStream(ByteArrayOutputStream()))
        reporter.onCrash = { exits++ }
        val thread = Thread({ throw IllegalStateException("asset prepare failed") }, "asset-prepare")
        thread.uncaughtExceptionHandler = reporter
        thread.start()
        thread.join()

        assertEquals(1, exits)
        val text = Files.readString(records(folder).single())
        assertTrue(text.contains("thread: asset-prepare"))
        assertTrue(text.contains("asset prepare failed"))
    }
}
