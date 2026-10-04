/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.play

import net.nevinsky.abyssus.physics.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

class PlayHostMainTest {
    private val protocol = PlayProtocol()

    /** A play process for [module] connected to a test socket; [use] gets the socket's streams after the handshake. */
    private fun playing(module: String = PhysicsOnlyPlayModule::class.java.name, use: (Process, Socket, DataInputStream, DataOutputStream) -> Unit) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 20_000
            val java = File(System.getProperty("java.home"), "bin/java").path
            val process = ProcessBuilder(
                java, "-cp", System.getProperty("java.class.path"), "net.nevinsky.abyssus.physics.play.PlayHostMain",
                "--port", server.localPort.toString(), "--token", "secret", module,
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 20_000
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                    val output = DataOutputStream(socket.getOutputStream())
                    val hello = protocol.accept(input, output, "secret")
                    assertEquals(module, hello?.module)
                    use(process, socket, input, output)
                }
            } finally {
                process.destroyForcibly()
            }
        }
    }

    @Test
    fun playsThePhysicsSceneOverASocket() = playing { process, _, input, output ->
        val project = testProject("Physics")
        protocol.write(output, PlayFrame.Load(File(project, "scenes/Main Scene.scene").readText(), project.path, -1))
        assertEquals(PlayFrame.Ready, protocol.read(input))
        val start = protocol.read(input) as PlayFrame.Poses
        assertEquals(3.086434f, start.poses.single { it.id == 0 }.y)
        protocol.write(output, PlayFrame.Play)
        var fallen: PlayFrame.Poses? = null
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (fallen == null && System.nanoTime() < deadline) {
            val frame = protocol.read(input)
            if (frame is PlayFrame.Poses && frame.poses.single { it.id == 0 }.y < 3f) fallen = frame
        }
        assertNotNull("Model 0 did not fall", fallen)
        assertTrue(fallen!!.frame > 0 && fallen.simTime > 0.0)
        assertEquals(-38.25267f, fallen.poses.single { it.id == 1 }.x)
        protocol.write(output, PlayFrame.Stop)
        protocol.write(output, PlayFrame.Bye)
        assertTrue("the play process did not exit", process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
    }

    @Test
    fun exitsWhenTheSocketCloses() = playing { process, socket, _, _ ->
        socket.close()
        assertTrue("the play process did not exit", process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
    }

    @Test
    fun aSceneThatCannotLoadIsReported() = playing { process, _, input, output ->
        protocol.write(output, PlayFrame.Load("{}", testProject("Physics").path, -1))
        val error = protocol.read(input) as PlayFrame.Error
        assertTrue(error.message, error.message.contains("could not be loaded"))
        protocol.write(output, PlayFrame.Bye)
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
    }
}
