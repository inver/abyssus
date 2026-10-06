/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("PlayHostMain")

package net.nevinsky.abyssus.lib.physics.play

import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import kotlin.system.exitProcess

/**
 * The play process Abyssus Physics starts: `PlayHostMain --port <p> --token <t> <PlayModule class>`. It connects to
 * the IDE on the loopback port, sends `hello`, then runs [PlayHost] until the IDE says `bye` or the socket closes.
 * Stdout and stderr are free for logging; the protocol uses only the socket.
 */
fun main(args: Array<String>) {
    val options = HashMap<String, String>()
    val rest = ArrayList<String>()
    var i = 0
    while (i < args.size) {
        if (args[i].startsWith("--") && i + 1 < args.size) options[args[i].removePrefix("--")] = args[++i] else rest += args[i]
        i++
    }
    val port = options["port"]?.toIntOrNull()
    val token = options["token"]
    if (port == null || token == null || rest.size != 1) {
        System.err.println("usage: PlayHostMain --port <port> --token <token> <PlayModule class>")
        exitProcess(2)
    }
    val module = Class.forName(rest.single()).getDeclaredConstructor().newInstance() as PlayModule
    // slf4j-simple (this module's runtime binding) prints to stderr, which Abyssus Physics keeps the end of
    val log = LoggerFactory.getLogger("play-host")
    val code = Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
        socket.tcpNoDelay = true
        PlayHost(
            DataInputStream(BufferedInputStream(socket.getInputStream())),
            DataOutputStream(BufferedOutputStream(socket.getOutputStream())),
            module, log,
        ).run(token)
    }
    exitProcess(code)
}
