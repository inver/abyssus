/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.plugin

import net.nevinsky.abyssus.core.assets.displayMessage

import net.nevinsky.abyssus.physics.play.PlayFrame
import net.nevinsky.abyssus.physics.play.PlayProtocol
import net.nevinsky.abyssus.sceneview.Pose
import net.nevinsky.abyssus.sceneview.Quat
import net.nevinsky.abyssus.sceneview.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * The IDE's end of a connected play process. One reader thread decodes the host's frames and publishes the latest
 * poses as one immutable map, so the render thread reads a consistent snapshot. [onReady] runs once the scene is
 * loaded; [onFailed] runs once if the process reports an error or the connection ends without [stop].
 */
class PlayClient internal constructor(
    val process: Process,
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    val tail: OutputTail,
    private val protocol: PlayProtocol,
) {
    enum class State { CONNECTED, READY, STOPPED, FAILED }

    @Volatile
    var state = State.CONNECTED
        private set

    @Volatile
    var failure: String? = null
        private set

    @Volatile
    private var latest: Map<String, Pose>? = null

    @Volatile
    var telemetry: Map<String, String> = emptyMap()
        private set

    var onReady: () -> Unit = {}
    var onFailed: (String) -> Unit = {}

    /** The latest simulated poses by entity id, null before the first. */
    fun poses(): Map<String, Pose>? = latest

    /** Starts reading the host's frames. */
    fun start() {
        Thread(::read, "abyssus-play-reader").apply { isDaemon = true; start() }
    }

    fun send(frame: PlayFrame) {
        synchronized(output) { protocol.write(output, frame) }
    }

    /** Asks the host to stop and exit, then makes sure the process and its children are gone. */
    fun stop() {
        if (state == State.STOPPED || state == State.FAILED) return
        state = State.STOPPED
        try {
            send(PlayFrame.Stop)
            send(PlayFrame.Bye)
        } catch (_: Exception) {
            // the host may already be gone
        }
        socket.close()
        Thread({
            if (!process.waitFor(2, TimeUnit.SECONDS)) kill(process)
        }, "abyssus-play-stop").apply { isDaemon = true; start() }
    }

    private fun read() {
        try {
            while (true) {
                when (val frame = protocol.read(input)) {
                    is PlayFrame.Poses -> latest = frame.poses.associate {
                        it.id.toString() to Pose(Vec3(it.x, it.y, it.z), Quat(it.qx, it.qy, it.qz, it.qw))
                    }
                    PlayFrame.Ready -> if (state == State.CONNECTED) {
                        state = State.READY
                        onReady()
                    }
                    is PlayFrame.Telemetry -> telemetry = frame.values
                    is PlayFrame.Error -> {
                        fail(AbyssusPhysicsBundle.message("playError", frame.message))
                        return
                    }
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            if (state == State.STOPPED) return
            val code = if (process.waitFor(2, TimeUnit.SECONDS)) process.exitValue() else null
            fail(code?.let { AbyssusPhysicsBundle.message("playExited", it) } ?: (e.displayMessage()))
        }
    }

    private fun fail(message: String) {
        if (state == State.STOPPED || state == State.FAILED) return
        state = State.FAILED
        failure = message
        socket.close()
        kill(process)
        onFailed(message)
    }
}
