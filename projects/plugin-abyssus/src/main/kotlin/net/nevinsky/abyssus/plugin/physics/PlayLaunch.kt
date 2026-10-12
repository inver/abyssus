/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.physics

import net.nevinsky.abyssus.lib.physics.play.PLAY_FILE
import net.nevinsky.abyssus.lib.physics.play.PLAY_PROTOCOL
import net.nevinsky.abyssus.lib.physics.play.PlayFile
import java.io.File

/** The play host's main class, in every play classpath. */
const val PLAY_HOST_MAIN = "net.nevinsky.abyssus.lib.physics.play.PlayHostMain"

/** The module the bundled play host runs: physics alone. */
const val PHYSICS_ONLY_MODULE = "net.nevinsky.abyssus.lib.physics.play.PhysicsOnlyPlayModule"

/** What to start for Play, or why Play cannot start. */
sealed interface PlayLaunch {
    /** `java [jvmArgs] -cp <classpath> PlayHostMain --port <p> --token <t> <module>`. */
    data class Plan(val classpath: List<String>, val module: String, val jvmArgs: List<String>) : PlayLaunch {
        fun command(java: String, port: Int, token: String): List<String> =
            buildList {
                addAll(listOf(java))
                addAll(this@Plan.jvmArgs)
                addAll(
                    listOf(
                        "-cp",
                        this@Plan.classpath.joinToString(File.pathSeparator),
                        PLAY_HOST_MAIN,
                        "--port",
                        port.toString(),
                        "--token",
                        token,
                        this@Plan.module
                    )
                )
            }
    }

    data class Refused(val message: String) : PlayLaunch

    companion object {
        /**
         * The game's own play host when [projectDir] holds `abyssus/play.json` (every classpath entry must exist and the
         * protocol must be [PLAY_PROTOCOL]), else the jars of the bundled [playHost] folder with physics alone.
         */
        fun choose(projectDir: File, playHost: File?): PlayLaunch {
            val file = File(projectDir, PLAY_FILE)
            if (file.isFile) {
                val config = try {
                    PlayFile().parse(file.readText())
                } catch (e: Exception) {
                    return Refused(AbyssusPhysicsBundle.message("playBadFile", e.message.orEmpty()))
                }
                if (config.protocol != PLAY_PROTOCOL) return Refused(
                    AbyssusPhysicsBundle.message(
                        "playStaleProtocol",
                        config.protocol,
                        PLAY_PROTOCOL
                    )
                )
                config.classpath.firstOrNull { !File(it).exists() }
                    ?.let { return Refused(AbyssusPhysicsBundle.message("playStaleJar", it)) }
                return Plan(config.classpath, config.module, config.jvmArgs)
            }
            val jars = playHost?.listFiles { f -> f.isFile && f.name.endsWith(".jar") }?.sortedBy { it.name }.orEmpty()
            if (jars.isEmpty()) return Refused(AbyssusPhysicsBundle.message("playNoHost"))
            return Plan(jars.map { it.absolutePath }, PHYSICS_ONLY_MODULE, emptyList())
        }
    }
}
