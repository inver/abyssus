/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import javax.tools.ToolProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LauncherTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val seed: Path by lazy { tmp.newFolder("seed").toPath().also { Files.writeString(it.resolve("game.jar"), "x") } }
    private val updates: Path by lazy { tmp.newFolder("updates").toPath() }

    private fun config(env: Map<String, String> = emptyMap(), cleanAfter: Duration = Duration.ofSeconds(30)) =
        LauncherConfig(seed, updates, "1.0.0", "Main", env, { null }, true, cleanAfter)

    @Test
    fun `the game runs on the calling thread and the project is the seed's`() {
        var runThread: Thread? = null
        var launch: GameLaunch? = null
        Launcher(config(), { l -> runThread = Thread.currentThread(); launch = l }, { }).start()
        assertEquals(Thread.currentThread(), runThread)
        assertEquals("Main", launch!!.mainClass)
        assertEquals(seed.resolve("project/ControlLine"), launch!!.project)
        assertEquals(listOf(seed.resolve("game.jar")), launch!!.jars)
    }

    @Test
    fun `a slow update check does not delay the game`() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        var gameRan = false
        Launcher(config(), { gameRan = true }, { started.countDown(); release.await() }).start()
        assertTrue(gameRan) // start() returned while the check still blocks
        assertTrue(started.await(5, TimeUnit.SECONDS))
        release.countDown()
    }

    @Test
    fun `an offline or failing check does not fail the start`() {
        var gameRan = false
        val logged = mutableListOf<String>()
        val done = CountDownLatch(1)
        Launcher(config(), { gameRan = true }, { throw java.io.IOException("offline") }, { logged += it; if (it.contains("failed")) done.countDown() }).start()
        assertTrue(gameRan)
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(logged.any { it.contains("offline") })
    }

    @Test
    fun `updates off starts no check`() {
        var checked = false
        Launcher(config(env = mapOf("CONTROL_LINE_UPDATES" to "off")), { }, { checked = true }).start()
        Thread.sleep(100)
        assertFalse(checked)
    }

    @Test
    fun `class loader runner sets the property and runs main on the calling thread`() {
        val dir = tmp.newFolder("fake").toPath()
        val source = dir.resolve("FakeGame.java")
        Files.writeString(
            source,
            """public class FakeGame {
                 public static String thread; public static String project; public static String loader;
                 public static void main(String[] args) {
                   thread = Thread.currentThread().getName();
                   project = System.getProperty("controlLine.project");
                   loader = Thread.currentThread().getContextClassLoader().getClass().getName();
                   if (System.getProperty("fake.fail") != null) throw new IllegalStateException("boom");
                 }
               }"""
        )
        check(ToolProvider.getSystemJavaCompiler().run(null, null, null, source.toString()) == 0)
        val jar = dir.resolve("fake.jar")
        JarOutputStream(Files.newOutputStream(jar)).use {
            it.putNextEntry(ZipEntry("FakeGame.class"))
            it.write(Files.readAllBytes(dir.resolve("FakeGame.class")))
            it.closeEntry()
        }
        val project = tmp.newFolder("project").toPath()
        ClassLoaderRunner().run(GameLaunch(listOf(jar), "FakeGame", project))
        assertEquals(project.toString(), System.getProperty("controlLine.project"))
        assertTrue(Thread.currentThread().contextClassLoader !is java.net.URLClassLoader)
        System.setProperty("fake.fail", "1")
        try {
            ClassLoaderRunner().run(GameLaunch(listOf(jar), "FakeGame", project))
            throw AssertionError("expected the game's exception")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        } finally {
            System.clearProperty("fake.fail")
        }
    }
}
