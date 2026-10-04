/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.plugin

import net.nevinsky.abyssus.physics.play.PlayModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A game module that never lets its play host connect. */
class BlockingPlayModule : PlayModule {
    init {
        Thread.sleep(Long.MAX_VALUE)
    }
}

/** A game module that prints a few lines, so a failure has output to show. */
class ChattyPlayModule : PlayModule {
    init {
        for (i in 1..3) System.err.println("chatty line $i")
    }
}

class PlayLaunchTest {
    private val classpath = System.getProperty("java.class.path").split(File.pathSeparator)

    private fun project(): File = Files.createTempDirectory("play-launch").toFile()

    @Test
    fun aStalePlayFileNamesTheMissingJar() {
        val dir = project()
        try {
            File(dir, "abyssus").mkdirs()
            File(dir, "abyssus/play.json").writeText("""{"protocol": 1, "module": "com.example.Game", "classpath": ["${dir.path}/gone/game.jar"]}""")
            val launch = PlayLaunch.choose(dir, null) as PlayLaunch.Refused
            assertTrue(launch.message, launch.message.contains("${dir.path}/gone/game.jar") && launch.message.contains("Export the game again"))
            File(dir, "abyssus/play.json").writeText("""{"protocol": 2, "module": "com.example.Game", "classpath": []}""")
            assertTrue((PlayLaunch.choose(dir, null) as PlayLaunch.Refused).message.contains("protocol 2"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun withoutAPlayFileTheBundledHostRunsPhysicsAlone() {
        val dir = project()
        val host = File(dir, "play-host").apply { mkdirs() }
        try {
            File(host, "b.jar").writeText("")
            File(host, "a.jar").writeText("")
            val plan = PlayLaunch.choose(dir, host) as PlayLaunch.Plan
            assertEquals(PHYSICS_ONLY_MODULE, plan.module)
            assertEquals(listOf("a.jar", "b.jar"), plan.classpath.map { File(it).name })
            assertTrue(PlayLaunch.choose(dir, File(dir, "missing")) is PlayLaunch.Refused)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aModuleThatNeverConnectsFailsAfterTheTimeoutWithNoProcessLeft() {
        var process: Process? = null
        val launcher = PlayProcessLauncher(timeoutSeconds = 2, started = { process = it })
        val error = assertThrows(PlayStartException::class.java) {
            launcher.launch(PlayLaunch.Plan(classpath, BlockingPlayModule::class.java.name, emptyList()), File("."))
        }
        assertTrue(error.message!!, error.message!!.contains("did not connect within 2 seconds"))
        assertTrue("the play process is still running", process!!.waitFor(5, TimeUnit.SECONDS))
        assertEquals(0L, process!!.descendants().count())
    }

    @Test
    fun aKilledHostFailsWithItsLastOutputLines() {
        val client = PlayProcessLauncher().launch(PlayLaunch.Plan(classpath, ChattyPlayModule::class.java.name, emptyList()), File("."))
        val failed = CountDownLatch(1)
        var message = ""
        client.onFailed = { message = it; failed.countDown() }
        client.start()
        // the module printed before the host connected, so its lines are already being drained
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while ("chatty line 3" !in client.tail.last() && System.nanoTime() < deadline) Thread.sleep(20)
        client.process.destroyForcibly()
        assertTrue("no failure reported", failed.await(10, TimeUnit.SECONDS))
        assertEquals(PlayClient.State.FAILED, client.state)
        assertTrue(message, message.contains("exited with code"))
        assertTrue(client.tail.last(), client.tail.last().contains("chatty line 3"))
        assertFalse(client.process.isAlive)
    }

    @Test
    fun stopEndsTheProcessWithoutAFailure() {
        val client = PlayProcessLauncher().launch(PlayLaunch.Plan(classpath, ChattyPlayModule::class.java.name, emptyList()), File("."))
        var failures = 0
        client.onFailed = { failures++ }
        client.start()
        client.stop()
        assertTrue(client.process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, failures)
        assertEquals(PlayClient.State.STOPPED, client.state)
    }
}
