/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.play

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.physics.FoliageProject
import net.nevinsky.abyssus.lib.physics.testProject
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
                java, "-cp", System.getProperty("java.class.path"), "net.nevinsky.abyssus.lib.physics.play.PlayHostMain",
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
    fun aBoxSettlesOnAFoliageColliderInTheExternalHost() = FoliageProject().use { project ->
        project.foliage("[${FoliageProject.layerJson(FoliageProject.CAPSULE)}]")
        project.bake(listOf(FoliageProject.layer(listOf(FoliageCopy(0, 50f, 50f, 0f, 1f)))))
        playing { process, _, input, output ->
            protocol.write(output, PlayFrame.Load(FoliageProject.scene(extra = FoliageProject.box(50f, 5f, 50f)), project.dir.path, -1))
            assertEquals(PlayFrame.Ready, protocol.read(input))
            protocol.write(output, PlayFrame.Play)
            var settled: PlayFrame.Poses? = null
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (settled == null && System.nanoTime() < deadline) {
                val frame = protocol.read(input)
                if (frame is PlayFrame.Poses && frame.simTime >= 5.0) settled = frame
            }
            assertNotNull("no frame reached 5 s of simulated time", settled)
            // the capsule trunk's top is 1.5 m up, so the 1 m box rests at 2.0 m (the floor would give -18.5 m)
            assertEquals(2.0f, settled!!.poses.single { it.id == 2 }.y, 0.05f)
            protocol.write(output, PlayFrame.Stop)
            protocol.write(output, PlayFrame.Bye)
            assertTrue("the play process did not exit", process.waitFor(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun exitsWhenTheSocketCloses() = playing { process, socket, _, _ ->
        socket.close()
        assertTrue("the play process did not exit", process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
    }

    @Test
    fun aSceneThatCannotLoadIsReported() = playing { process, _, input, output ->
        protocol.write(output, PlayFrame.Load("not a scene", testProject("Physics").path, -1))
        val error = protocol.read(input) as PlayFrame.Error
        assertTrue(error.message, error.message.contains("could not be loaded"))
        protocol.write(output, PlayFrame.Bye)
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
    }
}
