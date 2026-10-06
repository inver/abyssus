/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

class PlayProtocolTest {
    private val protocol = PlayProtocol()

    private fun bytes(vararg frames: PlayFrame): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).let { data -> frames.forEach { protocol.write(data, it) } }
        return out.toByteArray()
    }

    private fun input(bytes: ByteArray) = DataInputStream(ByteArrayInputStream(bytes))

    @Test
    fun everyFrameTypeRoundTrips() {
        val frames = listOf(
            PlayFrame.Hello(PLAY_PROTOCOL, "t0k3n", "net.nevinsky.abyssus.lib.physics.play.PhysicsOnlyPlayModule"),
            PlayFrame.Ready,
            PlayFrame.Poses(42, 0.35, listOf(EntityPose(0, -3f, 2.5f, -3.25f, 0f, 0f, 0f, 1f), EntityPose(7, 1f, 2f, 3f, 0.5f, 0.5f, 0.5f, 0.5f))),
            PlayFrame.Poses(0, 0.0, emptyList()),
            PlayFrame.Lines(listOf(DebugLine(0f, 0f, 0f, 1f, 2f, 3f, 0x00ff00ff.toInt()))),
            PlayFrame.Telemetry(mapOf("tension" to "9.81", "speed" to "20")),
            PlayFrame.Error("the scene could not be loaded"),
            PlayFrame.Load("{\"format\":\"abyssus\"}\n", "/tmp/project dir", 0),
            PlayFrame.Play, PlayFrame.Pause, PlayFrame.Step, PlayFrame.Stop,
            PlayFrame.Input(PlayInput(PlayInput.Kind.KEY_DOWN, key = "W")),
            PlayFrame.Input(PlayInput(PlayInput.Kind.BUTTON_UP, button = 1, x = 10, y = 20)),
            PlayFrame.Bye,
        )
        val stream = input(bytes(*frames.toTypedArray()))
        assertEquals(frames, frames.map { protocol.read(stream) })
        assertThrows(EOFException::class.java) { protocol.read(stream) }
    }

    @Test
    fun wireIdsRemainStableForEveryFrame() {
        val cases = listOf(
            1 to PlayFrame.Hello(1, "token", "module"), 2 to PlayFrame.Ready,
            3 to PlayFrame.Poses(7, 0.5, listOf(EntityPose(1, 2f, 3f, 4f, 0f, 0f, 0f, 1f))),
            4 to PlayFrame.Lines(listOf(DebugLine(1f, 2f, 3f, 4f, 5f, 6f, -1))),
            5 to PlayFrame.Telemetry(mapOf("speed" to "10")), 6 to PlayFrame.Error("error"),
            20 to PlayFrame.Load("scene", "project", 7), 21 to PlayFrame.Play, 22 to PlayFrame.Pause,
            23 to PlayFrame.Step, 24 to PlayFrame.Stop,
            25 to PlayFrame.Input(PlayInput(PlayInput.Kind.MOUSE_MOVE, x = 8, y = 9)), 26 to PlayFrame.Bye,
        )
        for ((wireId, frame) in cases) {
            val encoded = bytes(frame)
            assertEquals(wireId, encoded[4].toInt() and 255)
            assertEquals(frame, protocol.read(input(encoded)))
        }
    }

    @Test
    fun badFramesAreRefused() {
        assertThrows(PlayProtocolException::class.java) { protocol.read(input(byteArrayOf(0, 0, 0, 1, 99))) }
        assertThrows(PlayProtocolException::class.java) { protocol.read(input(byteArrayOf(0x7f, 0, 0, 0, 1))) }
        assertThrows(PlayProtocolException::class.java) { protocol.read(input(byteArrayOf(0, 0, 0, 3, 1, '{'.code.toByte(), '}'.code.toByte()))) }
    }

    @Test
    fun aBadTokenCloses() {
        val reply = ByteArrayOutputStream()
        val hello = protocol.accept(input(bytes(PlayFrame.Hello(PLAY_PROTOCOL, "wrong", "M"))), DataOutputStream(reply), "right")
        assertNull(hello)
        assertEquals(0, reply.size())
    }

    @Test
    fun protocolTwoGetsAnError() {
        val reply = ByteArrayOutputStream()
        val hello = protocol.accept(input(bytes(PlayFrame.Hello(2, "right", "M"))), DataOutputStream(reply), "right")
        assertNull(hello)
        val error = protocol.read(input(reply.toByteArray())) as PlayFrame.Error
        assertTrue(error.message, error.message.contains("protocol 2"))
    }

    @Test
    fun theRightTokenAndProtocolAreAccepted() {
        val reply = ByteArrayOutputStream()
        val hello = protocol.accept(input(bytes(PlayFrame.Hello(PLAY_PROTOCOL, "right", "M"))), DataOutputStream(reply), "right")
        assertEquals("M", hello?.module)
        assertEquals(0, reply.size())
    }
}
