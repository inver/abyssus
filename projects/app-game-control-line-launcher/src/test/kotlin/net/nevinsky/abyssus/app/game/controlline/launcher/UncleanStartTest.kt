/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import net.nevinsky.abyssus.lib.updater.store.UpdateState
import net.nevinsky.abyssus.lib.updater.store.VersionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UncleanStartTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var seed: Path
    private lateinit var updates: Path
    private val ran = mutableListOf<GameLaunch>()

    @Before
    fun setUp() {
        seed = tmp.newFolder("seed").toPath().also { Files.writeString(it.resolve("seed.jar"), "s") }
        updates = tmp.newFolder("updates").toPath()
        val store = VersionStore(updates, seed)
        for (v in listOf("1.0.1", "1.0.2")) {
            Files.writeString(store.beginStaging(v, 1).resolve("$v.jar"), v)
            store.commit(v)
        }
    }

    private fun launcher(cleanAfter: Duration = Duration.ofSeconds(30), body: (GameLaunch) -> Unit = { }) =
        Launcher(
            LauncherConfig(seed, updates, "1.0.0", "Main", emptyMap(), { null }, false, cleanAfter),
            { ran += it; body(it) }, { }, { },
        )

    private fun state() = UpdateState(updates.resolve("state.json")).load()
    private fun version() = ran.last().jars.single().fileName.toString().removeSuffix(".jar")

    @Test
    fun `two failures in a row mark the version bad and the previous one runs`() {
        for (i in 1..2) {
            try {
                launcher { throw IllegalStateException("crash") }.start()
            } catch (e: IllegalStateException) {
            }
            assertEquals("1.0.2", version())
        }
        assertEquals(setOf("1.0.2"), state().bad)
        launcher().start()
        assertEquals("1.0.1", version())
    }

    @Test
    fun `a crash that leaves the mark counts at the next start`() {
        // simulate a process that died before the clean mark: launching stays set
        UpdateState(updates.resolve("state.json")).update { it.copy(launching = "1.0.2", unclean = mapOf("1.0.2" to 1)) }
        launcher().start()
        assertEquals("1.0.1", version())
        assertTrue("1.0.2" in state().bad)
    }

    @Test
    fun `a crash after the clean period does not count`() {
        try {
            launcher(cleanAfter = Duration.ofMillis(50)) { Thread.sleep(300); throw IllegalStateException("late crash") }.start()
        } catch (e: IllegalStateException) {
        }
        assertEquals(emptyMap<String, Int>(), state().unclean)
        assertFalse("1.0.2" in state().bad)
        launcher().start()
        assertEquals("1.0.2", version())
    }

    @Test
    fun `a clean exit clears the mark and the count`() {
        UpdateState(updates.resolve("state.json")).update { it.copy(unclean = mapOf("1.0.2" to 1)) }
        launcher().start()
        assertNull(state().launching)
        assertEquals(emptyMap<String, Int>(), state().unclean)
        assertEquals("1.0.2", version())
    }
}
