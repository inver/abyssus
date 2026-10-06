/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.play

import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.lib.physics.play.PlayFrame
import net.nevinsky.abyssus.lib.physics.play.PlayInput
import net.nevinsky.abyssus.lib.physics.play.PlayProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Play in Abyssus as the IDE drives it: `PlayHostMain` in its own process with [ControlLinePlay], over a socket. */
class ControlLinePlayTest {
    private val protocol = PlayProtocol()

    /** The Trainer's id in the bundled field scene. */
    private val trainer = 5

    private class Session(val input: DataInputStream, val output: DataOutputStream) {
        var telemetry: Map<String, String> = emptyMap()
        var trainerY = Float.NaN
    }

    /** Reads frames until [done] holds or [seconds] pass; returns whether it held. */
    private fun Session.until(seconds: Long, done: Session.() -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (System.nanoTime() < deadline) {
            when (val frame = protocol.read(input)) {
                is PlayFrame.Telemetry -> telemetry = frame.values
                is PlayFrame.Poses -> frame.poses.firstOrNull { it.id == trainer }?.let { trainerY = it.y }
                is PlayFrame.Error -> throw AssertionError(frame.message)
                else -> Unit
            }
            if (done()) return true
        }
        return false
    }

    private fun Session.key(name: String, down: Boolean) =
        protocol.write(output, PlayFrame.Input(PlayInput(if (down) PlayInput.Kind.KEY_DOWN else PlayInput.Kind.KEY_UP, name)))

    private fun Session.number(key: String) = telemetry[key]?.substringBefore(' ')?.removeSuffix("°")?.toFloatOrNull() ?: Float.NaN

    @Test
    fun playFliesTheSelectedTrainer() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 30_000
            val java = File(System.getProperty("java.home"), "bin/java").path
            val process = ProcessBuilder(
                java, "-cp", System.getProperty("java.class.path"), "net.nevinsky.abyssus.lib.physics.play.PlayHostMain",
                "--port", server.localPort.toString(), "--token", "secret", ControlLinePlay::class.java.name,
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 30_000
                    val s = Session(DataInputStream(BufferedInputStream(socket.getInputStream())), DataOutputStream(socket.getOutputStream()))
                    assertEquals(ControlLinePlay::class.java.name, protocol.accept(s.input, s.output, "secret")?.module)
                    val project = bundledProject()
                    protocol.write(s.output, PlayFrame.Load(Files.readString(project.resolve("scenes/Field.scene")), project.toString(), trainer))
                    assertEquals(PlayFrame.Ready, protocol.read(s.input))
                    protocol.write(s.output, PlayFrame.Play)

                    // it takes off on its lines
                    assertTrue("Trainer did not take off: ${s.telemetry}", s.until(20) { telemetry["Plane"] == "Trainer" && trainerY > 1.5f })
                    assertTrue(s.number("Tension") >= 1f)
                    assertEquals("1", s.telemetry["Flight"])

                    // W raises the elevator
                    s.key("W", true)
                    assertTrue("W did not raise the elevator: ${s.telemetry}", s.until(5) { number("Elevator") > 15f })
                    s.key("W", false)
                    assertTrue(s.until(5) { number("Elevator") == 0f })

                    // full down crashes it; the next flight starts from takeoff about a second later
                    s.key("S", true)
                    assertTrue("no crash: ${s.telemetry}", s.until(10) { telemetry["Last flight"]?.startsWith("Crashed") == true })
                    s.key("S", false)
                    val crashed = System.nanoTime()
                    assertTrue("no restart: ${s.telemetry}", s.until(10) { telemetry["Flight"] == "2" })
                    val waited = (System.nanoTime() - crashed) / 1e9
                    assertTrue("restarted after $waited s", waited in 0.7..2.0)
                    assertTrue("not back on the ground: ${s.trainerY}", s.until(2) { trainerY < 0.5f })

                    protocol.write(s.output, PlayFrame.Stop)
                    protocol.write(s.output, PlayFrame.Bye)
                    assertTrue("the play process did not exit", process.waitFor(10, TimeUnit.SECONDS))
                }
            } finally {
                process.destroyForcibly()
            }
        }
    }
}
