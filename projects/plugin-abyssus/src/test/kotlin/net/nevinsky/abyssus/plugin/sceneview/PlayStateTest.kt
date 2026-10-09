/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.content.Pose
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Quat

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import net.nevinsky.abyssus.plugin.sceneview.PlayState.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class PlayStateTest {
    private val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, _, _ -> null } as Project
    private val file: VirtualFile = LightVirtualFile("Main Scene.scene", "{}")
    private fun request() = SimulationRequest(project, file, "{}", File("."), "0")

    @Test fun unavailableProviderStopsStartupPauseAndPlayingAndDiscardsLateCallbacks() {
        for (phase in listOf(Phase.STARTING,Phase.PLAYING,Phase.PAUSED)) {
            val provider = FakeProvider()
            val play = PlayState(provider)
            play.play(::request)
            if (phase != Phase.STARTING) provider.listener!!.started()
            if (phase == Phase.PAUSED) play.pause()
            val callback = provider.listener!!
            play.updateProvider(null)
            play.updateProvider(null)
            callback.started()
            assertEquals(Phase.IDLE,play.phase)
            assertFalse(play.available)
            assertNull(play.poses())
            assertEquals(1,provider.started.single().commands.count { it == "stop" })
            play.updateProvider(provider)
            assertTrue(play.available)
        }
    }

    @Test fun unchangedThirdPartyProviderKeepsPlaying() {
        val provider = FakeProvider()
        val play = PlayState(provider)
        play.play(::request)
        provider.listener!!.started()
        play.updateProvider(provider)
        assertEquals(Phase.PLAYING,play.phase)
        assertTrue(provider.started.single().commands.isEmpty())
    }

    private class FakeSimulation : SceneSimulation {
        val commands = mutableListOf<String>()
        var poses: Map<String, Pose>? = mapOf("0" to Pose(Vec3(0f, 1f, 0f), Quat.IDENTITY))
        override fun pause() { commands += "pause" }
        override fun resume() { commands += "resume" }
        override fun step() { commands += "step" }
        override fun stop() { commands += "stop" }
        override fun input(event: SimulationInput) { commands += "input ${event.key}" }
        override fun poses() = poses
    }

    private class FakeProvider(val throwOnStart: Boolean = false) : SceneSimulationProvider {
        val started = mutableListOf<FakeSimulation>()
        var listener: SimulationListener? = null
        var request: SimulationRequest? = null
        override fun start(request: SimulationRequest, listener: SimulationListener): SceneSimulation {
            if (throwOnStart) error("cannot start")
            this.request = request
            this.listener = listener
            return FakeSimulation().also { started += it }
        }
    }

    @Test
    fun withoutAProviderThereIsNoPlay() {
        val play = PlayState(null)
        assertFalse(play.available)
        play.play(::request)
        assertEquals(Phase.IDLE, play.phase)
        assertNull(play.poses())
    }

    @Test
    fun everyTransition() {
        val provider = FakeProvider()
        val play = PlayState(provider)
        var changes = 0
        play.onChanged = { changes++ }
        play.play(::request)
        assertEquals(Phase.STARTING, play.phase)
        assertEquals("0", provider.request?.selection)
        assertTrue(play.active)
        provider.listener!!.started()
        assertEquals(Phase.PLAYING, play.phase)
        val sim = provider.started.single()
        assertEquals(sim.poses, play.poses())
        play.pause()
        assertEquals(Phase.PAUSED, play.phase)
        play.step()
        play.step()
        assertEquals(Phase.PAUSED, play.phase)
        play.play(::request)
        assertEquals(Phase.PLAYING, play.phase)
        play.step()
        play.input(SimulationInput(SimulationInput.Kind.KEY_DOWN, key = "W"))
        play.stop()
        assertEquals(Phase.IDLE, play.phase)
        assertFalse(play.active)
        assertNull(play.poses())
        assertEquals(listOf("pause", "step", "step", "resume", "input W", "stop"), sim.commands)
        assertEquals(1, provider.started.size)
        assertEquals(5, changes)
    }

    @Test
    fun anEditStopsPlayFirst() {
        val provider = FakeProvider()
        val play = PlayState(provider)
        play.play(::request)
        provider.listener!!.started()
        play.documentChanging()
        assertEquals(Phase.IDLE, play.phase)
        assertEquals(listOf("stop"), provider.started.single().commands)
        play.documentChanging()
        assertEquals(listOf("stop"), provider.started.single().commands)
    }

    @Test
    fun escapeStopsOnlyWhilePlaying() {
        val provider = FakeProvider()
        val play = PlayState(provider)
        assertFalse(play.escape())
        play.play(::request)
        assertTrue(play.escape())
        assertEquals(Phase.IDLE, play.phase)
        assertEquals(listOf("stop"), provider.started.single().commands)
    }

    @Test
    fun aFailureReturnsToTheAuthoredPoses() {
        val provider = FakeProvider()
        val play = PlayState(provider)
        play.play(::request)
        val listener = provider.listener!!
        listener.started()
        listener.failed("the play process exited with code 137")
        assertEquals(Phase.FAILED, play.phase)
        assertEquals("the play process exited with code 137", play.failure)
        assertNull(play.poses())
        assertEquals(listOf("stop"), provider.started.single().commands)
        // a late callback from the dead simulation changes nothing
        listener.started()
        assertEquals(Phase.FAILED, play.phase)
        // Play again starts a new simulation
        play.play(::request)
        assertEquals(Phase.STARTING, play.phase)
        assertEquals(2, provider.started.size)
    }

    @Test
    fun aThrowingProviderIsSwitchedOffWithOneError() {
        val errors = mutableListOf<String>()
        val play = PlayState(FakeProvider(throwOnStart = true), "Broken Plugin", logError = { m, _ -> errors += m })
        play.play(::request)
        assertEquals(Phase.IDLE, play.phase)
        assertFalse(play.available)
        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("Broken Plugin"))
        play.play(::request)
        assertEquals(1, errors.size)
    }
}
