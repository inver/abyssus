/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.physics

import net.nevinsky.abyssus.physics.play.PlayProtocol
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.HexFormat

/** How long a play process has to connect. */
const val CONNECT_TIMEOUT_SECONDS = 20

/** How many of the play process's last output lines are kept for a failure message. */
const val OUTPUT_TAIL_LINES = 200

/** The last [capacity] lines a process printed (stdout and stderr together). Thread-safe. */
class OutputTail(private val capacity: Int = OUTPUT_TAIL_LINES) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun lines(): List<String> = lines.toList()

    /** The last [count] lines, joined. */
    fun last(count: Int = 20): String = lines().takeLast(count).joinToString("\n")
}

/** Play could not start: the process did not connect in time, refused the handshake or exited. */
class PlayStartException(message: String, val output: String) : Exception(message)

/**
 * Starts a play process for a [PlayLaunch.Plan]: opens a loopback port, runs the JVM at [java] with a fresh token, and
 * waits [timeoutSeconds] for it to connect and say hello. Its output is drained into an [OutputTail]; the protocol
 * uses only the socket. A process that does not connect in time, or fails the handshake, is killed with its children.
 */
class PlayProcessLauncher(
    private val java: String = File(System.getProperty("java.home"), "bin/java").path,
    private val timeoutSeconds: Int = CONNECT_TIMEOUT_SECONDS,
    private val started: (Process) -> Unit = {},
) {
    fun launch(plan: PlayLaunch.Plan, workingDir: File): PlayClient {
        val token = HexFormat.of().formatHex(ByteArray(16).also { SecureRandom().nextBytes(it) })
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val process = ProcessBuilder(plan.command(java, server.localPort, token))
                .directory(workingDir).redirectErrorStream(true).start()
            started(process)
            val tail = OutputTail()
            Thread({
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach(tail::add) }
            }, "abyssus-play-output").apply { isDaemon = true; start() }
            server.soTimeout = timeoutSeconds * 1000
            try {
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    throw PlayStartException(AbyssusPhysicsBundle.message("playNoConnection", timeoutSeconds), tail.last())
                }
                socket.tcpNoDelay = true
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val protocol = PlayProtocol()
                socket.soTimeout = timeoutSeconds * 1000
                val hello = try {
                    protocol.accept(input, output, token)
                } catch (e: Exception) {
                    null
                }
                if (hello == null) {
                    socket.close()
                    throw PlayStartException(AbyssusPhysicsBundle.message("playCouldNotStart"), tail.last())
                }
                socket.soTimeout = 0
                return PlayClient(process, socket, input, output, tail, protocol)
            } catch (e: Exception) {
                kill(process)
                throw e
            }
        }
    }
}

/** Ends [process] and every process it started. */
fun kill(process: Process) {
    process.descendants().forEach { it.destroyForcibly() }
    process.destroyForcibly()
}
